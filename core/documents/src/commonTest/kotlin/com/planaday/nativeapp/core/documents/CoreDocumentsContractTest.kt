package com.planaday.nativeapp.core.documents

import com.planaday.nativeapp.core.model.RecordKind
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class CoreDocumentsContractTest {
    @Test
    fun pd016BlurlessNoteSwitchNeverCorruptsNextSelectedNote() = runTest {
        val sink = InMemoryDraftSink()
        val mgr = DraftSessionManager(sink) { "2026-11-25T12:00:00Z" }
        val flushed = mutableListOf<Pair<String, String>>()

        // Open Alpha and edit without blur/commit
        mgr.openSession("ws1", "note_alpha", RecordKind.NOTE, "sess_a", "Alpha", "Original Alpha", 1L)
        mgr.updateDraft("ws1", "note_alpha", "sess_a", "Alpha", "Dirty Alpha Uncommitted Text")

        // Immediately switch to Beta without blurring Alpha!
        val betaSession = mgr.openSession(
            workspaceId = "ws1",
            recordId = "note_beta",
            recordKind = RecordKind.NOTE,
            sessionId = "sess_b",
            initialTitle = "Beta",
            initialBody = "Original Beta",
            baseRev = 1L,
            onFlushPrevious = { prev -> flushed.add(prev.recordId to prev.bodyDraft) },
        )

        // Alpha dirty draft flushed strictly to note_alpha; Beta completely untouched!
        assertEquals(listOf("note_alpha" to "Dirty Alpha Uncommitted Text"), flushed)
        assertEquals("Original Beta", betaSession.bodyDraft)
        assertEquals("note_beta", betaSession.recordId)

        // PF-55 Draft Recovery Inbox retains Alpha's dirty draft until explicitly committed
        val drafts = mgr.recoverableDrafts("ws1")
        assertEquals(1, drafts.size)
        assertEquals("note_alpha", drafts.single().recordId)
    }

    @Test
    fun pd010HundredThousandWordStatsAndWikiLinksAndDiff() {
        val text = buildString {
            repeat(20_000) { idx ->
                append("alpha beta gamma delta [[LinkedNote]]\n")
                if (idx % 50 == 49) append("\n")
            }
        }
        val stats = LargeDocumentAnalyzer.computeStatsSinglePass(text)
        assertEquals(100_000, stats.wordCount)
        assertEquals(400, stats.paragraphCount)
        assertEquals(listOf("LinkedNote"), LargeDocumentAnalyzer.extractWikiLinks(text))

        val buf = LargeDocumentBuffer(text)
        buf.insertAt(50_000, " INSERTED ")
        assertEquals(text.length + 10, buf.length())

        val diff = LargeDocumentAnalyzer.diffLines("line1\nold2", "line1\nnew2")
        assertEquals(listOf("EQUAL", "REMOVED", "ADDED"), diff.map { it.kind })
    }
}
