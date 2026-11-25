package com.planaday.nativeapp.core.documents

import com.planaday.nativeapp.core.model.DraftRecord
import com.planaday.nativeapp.core.model.RecordKind
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DocumentStats(
    val wordCount: Int,
    val charCount: Int,
    val paragraphCount: Int,
    val lineCount: Int,
)

data class LineDiffEntry(
    val kind: String, // EQUAL | ADDED | REMOVED
    val lineNumber: Int,
    val text: String,
)

/**
 * Single-pass O(n) document statistics & WikiLink extractor engineered for 100k+ word notes (PD-010, PF-07).
 * Avoids catastrophic regex backtracking and redundant full-string allocations.
 */
object LargeDocumentAnalyzer {
    fun computeStatsSinglePass(text: String): DocumentStats {
        if (text.isEmpty()) return DocumentStats(0, 0, 0, 0)
        var words = 0
        var inWord = false
        var lines = 1
        var paragraphs = 0
        var currentLineHasNonSpace = false
        var prevLineWasBlank = true

        for (i in 0 until text.length) {
            val ch = text[i]
            if (ch == '\n') {
                lines++
                if (!currentLineHasNonSpace) {
                    prevLineWasBlank = true
                }
                currentLineHasNonSpace = false
                inWord = false
            } else if (ch == '\r') {
                inWord = false
            } else if (ch.isWhitespace()) {
                inWord = false
            } else {
                if (!inWord) {
                    words++
                    inWord = true
                }
                if (!currentLineHasNonSpace) {
                    currentLineHasNonSpace = true
                    if (prevLineWasBlank) {
                        paragraphs++
                        prevLineWasBlank = false
                    }
                }
            }
        }
        return DocumentStats(
            wordCount = words,
            charCount = text.length,
            paragraphCount = paragraphs,
            lineCount = lines,
        )
    }

    /** Extracts unique `[[WikiLink]]` target titles in O(n) without regex backtracking. */
    fun extractWikiLinks(text: String): List<String> {
        val results = LinkedHashSet<String>()
        var i = 0
        val len = text.length
        while (i < len - 3) {
            if (text[i] == '[' && text[i + 1] == '[') {
                val close = text.indexOf("]]", i + 2)
                if (close > i + 2 && close - i < 200) {
                    val raw = text.substring(i + 2, close)
                    val target = raw.substringBefore('|').trim()
                    if (target.isNotEmpty() && '\n' !in target) {
                        results.add(target)
                    }
                    i = close + 2
                    continue
                }
            }
            i++
        }
        return results.toList()
    }

    /** Computes readable line-level diff between two revisions (R-0238). */
    fun diffLines(before: String, after: String): List<LineDiffEntry> {
        val oldLines = if (before.isEmpty()) emptyList() else before.split("\n")
        val newLines = if (after.isEmpty()) emptyList() else after.split("\n")
        val out = mutableListOf<LineDiffEntry>()
        val maxLen = maxOf(oldLines.size, newLines.size)
        for (i in 0 until maxLen) {
            val o = oldLines.getOrNull(i)
            val n = newLines.getOrNull(i)
            when {
                o == n && o != null -> out.add(LineDiffEntry("EQUAL", i + 1, o))
                o != null && n == null -> out.add(LineDiffEntry("REMOVED", i + 1, o))
                o == null && n != null -> out.add(LineDiffEntry("ADDED", i + 1, n))
                o != null && n != null -> {
                    out.add(LineDiffEntry("REMOVED", i + 1, o))
                    out.add(LineDiffEntry("ADDED", i + 1, n))
                }
            }
        }
        return out
    }
}

/**
 * Chunked large-document buffer supporting fast localized edits on 100k+ word documents (PD-010).
 */
class LargeDocumentBuffer(initialText: String = "", private val chunkSize: Int = 4096) {
    private val chunks = mutableListOf<StringBuilder>()

    init {
        setText(initialText)
    }

    fun setText(text: String) {
        chunks.clear()
        if (text.isEmpty()) {
            chunks.add(StringBuilder())
            return
        }
        var offset = 0
        while (offset < text.length) {
            val end = minOf(offset + chunkSize, text.length)
            chunks.add(StringBuilder(text.substring(offset, end)))
            offset = end
        }
    }

    fun length(): Int = chunks.sumOf { it.length }

    fun insertAt(charOffset: Int, text: String) {
        require(charOffset in 0..length()) { "Invalid offset $charOffset" }
        var remaining = charOffset
        for (idx in chunks.indices) {
            val chunk = chunks[idx]
            if (remaining <= chunk.length) {
                chunk.insert(remaining, text)
                if (chunk.length > chunkSize * 2) {
                    val splitPoint = chunk.length / 2
                    val right = StringBuilder(chunk.substring(splitPoint))
                    chunk.setLength(splitPoint)
                    chunks.add(idx + 1, right)
                }
                return
            }
            remaining -= chunk.length
        }
    }

    fun toFullString(): String {
        val sb = StringBuilder(length())
        for (c in chunks) sb.append(c)
        return sb.toString()
    }
}

interface DraftPersistenceSink {
    suspend fun saveDraft(draft: DraftRecord)
    suspend fun deleteDraft(workspaceId: String, recordId: String, sessionId: String)
    suspend fun listDrafts(workspaceId: String): List<DraftRecord>
}

class InMemoryDraftSink : DraftPersistenceSink {
    private val store = LinkedHashMap<String, DraftRecord>()
    private fun key(ws: String, rec: String, sess: String) = "$ws|$rec|$sess"
    override suspend fun saveDraft(draft: DraftRecord) {
        store[key(draft.workspaceId, draft.recordId, draft.sessionId)] = draft
    }
    override suspend fun deleteDraft(workspaceId: String, recordId: String, sessionId: String) {
        store.remove(key(workspaceId, recordId, sessionId))
    }
    override suspend fun listDrafts(workspaceId: String): List<DraftRecord> =
        store.values.filter { it.workspaceId == workspaceId }
}

/**
 * Record-bound Draft Session Manager (eliminates PD-016 by construction & implements PF-55 Draft Recovery Inbox).
 * Every edit and flush is cryptographically/structurally bound to its exact (workspaceId, recordId, sessionId);
 * switching active selection without blur ALWAYS flushes the previous session strictly to its bound recordId
 * and NEVER writes into the newly selected record!
 */
class DraftSessionManager(
    private val sink: DraftPersistenceSink,
    private val nowProvider: () -> String,
) {
    private val mutex = Mutex()
    private val activeSessions = LinkedHashMap<String, DraftRecord>()
    private var focusedKey: String? = null

    private fun sessionKey(workspaceId: String, recordId: String) = "$workspaceId::$recordId"

    suspend fun openSession(
        workspaceId: String,
        recordId: String,
        recordKind: RecordKind,
        sessionId: String,
        initialTitle: String,
        initialBody: String,
        baseRev: Long,
        onFlushPrevious: suspend (DraftRecord) -> Unit = {},
    ): DraftRecord = mutex.withLock {
        // Blurless record switch safeguard (PD-016): flush previous dirty session ONLY to its own bound recordId!
        val prevKey = focusedKey
        val nextKey = sessionKey(workspaceId, recordId)
        if (prevKey != null && prevKey != nextKey) {
            val prev = activeSessions[prevKey]
            if (prev != null && prev.dirty) {
                sink.saveDraft(prev)
                onFlushPrevious(prev)
            }
        }
        val existing = activeSessions[nextKey]
        val session = existing ?: DraftRecord(
            sessionId = sessionId,
            workspaceId = workspaceId,
            recordId = recordId,
            recordKind = recordKind,
            titleDraft = initialTitle,
            bodyDraft = initialBody,
            baseRev = baseRev,
            updatedAtUtc = nowProvider(),
            dirty = false,
        )
        activeSessions[nextKey] = session
        focusedKey = nextKey
        session
    }

    suspend fun updateDraft(
        workspaceId: String,
        recordId: String,
        sessionId: String,
        newTitle: String,
        newBody: String,
    ): DraftRecord = mutex.withLock {
        val key = sessionKey(workspaceId, recordId)
        val current = activeSessions[key]
            ?: error("No open draft session bound to ($workspaceId, $recordId)")
        require(current.sessionId == sessionId && current.recordId == recordId && current.workspaceId == workspaceId) {
            "Cross-record draft binding mismatch rejected (PD-016 guard)"
        }
        val updated = current.copy(
            titleDraft = newTitle,
            bodyDraft = newBody,
            updatedAtUtc = nowProvider(),
            dirty = true,
        )
        activeSessions[key] = updated
        sink.saveDraft(updated)
        updated
    }

    suspend fun commitAndClear(
        workspaceId: String,
        recordId: String,
        sessionId: String,
        commitHandler: suspend (DraftRecord) -> Unit,
    ): DraftRecord = mutex.withLock {
        val key = sessionKey(workspaceId, recordId)
        val current = activeSessions[key]
            ?: error("No open draft session for ($workspaceId, $recordId)")
        require(current.sessionId == sessionId && current.recordId == recordId) {
            "Refusing commit: sessionId/recordId mismatch (PD-016 guard)"
        }
        commitHandler(current)
        sink.deleteDraft(workspaceId, recordId, sessionId)
        val cleaned = current.copy(dirty = false, updatedAtUtc = nowProvider())
        activeSessions[key] = cleaned
        cleaned
    }

    suspend fun recoverableDrafts(workspaceId: String): List<DraftRecord> = mutex.withLock {
        sink.listDrafts(workspaceId).filter { it.dirty }
    }
}
