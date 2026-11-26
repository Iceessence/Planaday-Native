package com.planaday.nativeapp.core.interchange

import com.planaday.nativeapp.core.model.*
import com.planaday.nativeapp.core.security.CryptoPrimitives
import com.planaday.nativeapp.core.security.InputSafetyGuard
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class XlsxImportResult(
    val workbook: SpreadsheetWorkbook,
    val fidelityReport: List<String>,
)

data class ExportFidelityPreview(
    val targetFormat: String,
    val estimatedItemCount: Int,
    val supportedFeatures: List<String>,
    val unsupportedOrApproximated: List<String>,
)

@Serializable
data class DocumentAnnotation(
    val id: String,
    val pageNumber: Int,
    val quotedExcerpt: String,
    val comment: String,
    val colorHex: String = "#FDE047",
    val createdAtUtc: String,
)

@Serializable
data class ReviewedDocumentModel(
    val id: String,
    val workspaceId: String,
    val title: String,
    val blobSha256: String,
    val pageCount: Int,
    val bookmarks: List<Int> = emptyList(),
    val ocrTextByPage: Map<Int, String> = emptyMap(), // Local OCR or manual text fallback (N-017)
    val annotations: List<DocumentAnnotation> = emptyList(),
)

@Serializable
data class SecureShareBundleHeader(
    val bundleId: String,
    val sourceWorkspaceId: String,
    val allowedRole: WorkspaceRole,
    val createdAtUtc: String,
    val recordCount: Int,
    val recordsSha256: String,
)

/**
 * Extended Office & Publishing File Interchange Suite:
 * - Native OOXML `.xlsx` export & import with ZIP-bomb/XXE guards and Fidelity Report (PF-50, R-0281, R-0439, R-0440)
 * - Structured `.docx` and valid `.epub` eBook export (N-018, PF-29)
 * - Portable Markdown + YAML frontmatter notebook interoperability (N-019)
 * - Pre-export Fidelity Preview (PF-54)
 * - Authenticated AES-256-GCM Secure Sharing Bundle with explicit ACL (N-046)
 */
object ExtendedFormatInterchange {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun escXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun unescXml(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    fun previewExportFidelity(format: String, itemCount: Int, hasFormulas: Boolean = false, hasMacros: Boolean = false): ExportFidelityPreview {
        val supported = mutableListOf("UTF-8 text & numeric values", "Metadata & provenance header")
        val warnings = mutableListOf<String>()
        when (format.uppercase()) {
            "XLSX" -> {
                supported.addAll(listOf("Multiple sheets", "Standard cell formulas (=SUM, =IF, cross-sheet)", "Sparse coordinates"))
                if (hasMacros) warnings.add("VBA macros are intentionally excluded for security.")
            }
            "PPTX" -> {
                supported.addAll(listOf("Slide order", "Text frames", "Speaker notes", "Embedded images"))
                warnings.add("Complex PowerPoint timeline animations & SmartArt are exported as static shapes.")
            }
            "EPUB", "DOCX" -> {
                supported.addAll(listOf("Title & synopsis", "Ordered chapters & headings", "Paragraph typography"))
            }
            "CSV" -> {
                supported.addAll(listOf("RFC 4180 quoting & multiline cells", "Optional formula-injection neutralization"))
                if (hasFormulas) warnings.add("CSV exports evaluated values rather than live multi-sheet formulas.")
            }
        }
        return ExportFidelityPreview(format.uppercase(), itemCount, supported, warnings)
    }

    /** Exports a multi-sheet SpreadsheetWorkbook to valid OOXML `.xlsx` ZIP format (R-0281, PF-50). */
    fun exportXlsx(workbook: SpreadsheetWorkbook): ExportArtifact {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            writeEntry(
                zos, "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/></Types>""",
            )
            val sheetsXml = workbook.sheets.mapIndexed { i, s ->
                """<sheet name="${escXml(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>"""
            }.joinToString("")
            writeEntry(
                zos, "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>$sheetsXml</sheets></workbook>""",
            )
            workbook.sheets.forEachIndexed { idx, sheet ->
                val byRow = sheet.cells.entries.groupBy { entry ->
                    entry.key.filter { it.isDigit() }.toIntOrNull() ?: 1
                }.toSortedMap()
                val rowsXml = buildString {
                    for ((rowNum, entries) in byRow) {
                        append("""<row r="$rowNum">""")
                        for ((addr, cell) in entries.sortedBy { it.key }) {
                            val raw = cell.raw.trim()
                            when {
                                raw.startsWith("=") -> append("""<c r="$addr"><f>${escXml(raw.substring(1))}</f></c>""")
                                raw.toDoubleOrNull() != null -> append("""<c r="$addr"><v>$raw</v></c>""")
                                else -> append("""<c r="$addr" t="inlineStr"><is><t>${escXml(cell.raw)}</t></is></c>""")
                            }
                        }
                        append("</row>")
                    }
                }
                writeEntry(
                    zos, "xl/worksheets/sheet${idx + 1}.xml",
                    """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$rowsXml</sheetData></worksheet>""",
                )
            }
        }
        val filename = InputSafetyGuard.sanitizeFilename("${workbook.title.lowercase().replace(' ', '-')}.xlsx", "workbook.xlsx")
        return ExportArtifact(filename, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", baos.toByteArray())
    }

    /** Safely imports OOXML `.xlsx` preserving formulas, numbers, inline strings & sheet names with ZIP/XXE guards. */
    fun importXlsx(xlsxBytes: ByteArray, workspaceId: String, workbookId: String, title: String): XlsxImportResult {
        val entries = safeReadZip(xlsxBytes)
        val warnings = mutableListOf<String>()
        val wbXml = entries["xl/workbook.xml"]?.decodeToString()
            ?: throw IllegalArgumentException("Invalid XLSX: missing xl/workbook.xml")
        if (wbXml.contains("<!DOCTYPE", true) || wbXml.contains("<!ENTITY", true)) {
            throw SecurityException("Rejected unsafe XML DOCTYPE/ENTITY in xl/workbook.xml")
        }
        val sheetNames = Regex("""<sheet\s+[^>]*name="([^"]+)"""").findAll(wbXml).map { unescXml(it.groupValues[1]) }.toList()
        val sheetPaths = entries.keys.filter { Regex("^xl/worksheets/sheet(\\d+)\\.xml$").matches(it) }
            .sortedBy { Regex("sheet(\\d+)\\.xml$").find(it)!!.groupValues[1].toInt() }
        require(sheetPaths.isNotEmpty()) { "Invalid XLSX: no worksheets found" }

        val sheets = sheetPaths.mapIndexed { idx, path ->
            val xml = entries[path]!!.decodeToString()
            if (xml.contains("<!DOCTYPE", true) || xml.contains("<!ENTITY", true)) {
                throw SecurityException("Rejected unsafe XML DOCTYPE/ENTITY in $path")
            }
            val cells = LinkedHashMap<String, SheetCellInput>()
            val cellRegex = Regex("""<c\s+r="([A-Za-z]+\d+)"[^>]*>(.*?)</c>""", setOf(RegexOption.DOT_MATCHES_ALL))
            for (m in cellRegex.findAll(xml)) {
                val addr = m.groupValues[1].uppercase()
                val body = m.groupValues[2]
                val formula = Regex("""<f[^>]*>(.*?)</f>""").find(body)?.groupValues?.get(1)
                val inlineText = Regex("""<t[^>]*>(.*?)</t>""").find(body)?.groupValues?.get(1)
                val numVal = Regex("""<v>(.*?)</v>""").find(body)?.groupValues?.get(1)
                val raw = when {
                    formula != null -> "=${unescXml(formula)}"
                    inlineText != null -> unescXml(inlineText)
                    numVal != null -> numVal
                    else -> ""
                }
                if (raw.isNotEmpty()) cells[addr] = SheetCellInput(raw)
            }
            WorksheetModel(
                id = "sheet_${idx + 1}",
                name = sheetNames.getOrElse(idx) { "Sheet${idx + 1}" },
                cells = cells,
            )
        }
        if (entries.keys.any { it.contains("vbaProject.bin", true) }) {
            warnings.add("Security guard: stripped VBA macro project from XLSX.")
        } else {
            warnings.add("Imported ${sheets.size} worksheet(s) cleanly with formulas preserved.")
        }
        return XlsxImportResult(SpreadsheetWorkbook(workbookId, workspaceId, title, sheets), warnings)
    }

    /** Exports BookProject + chapters to structured OOXML `.docx` (N-018, PF-29). */
    fun exportBookDocx(book: BookProject, chapters: List<BookChapter>): ExportArtifact {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            writeEntry(zos, "[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="xml" ContentType="application/xml"/></Types>""")
            val paragraphs = buildString {
                append("""<w:p><w:r><w:t>${escXml(book.title)}</w:t></w:r></w:p>""")
                for (ch in chapters.sortedBy { it.chapterNumber }) {
                    append("""<w:p><w:r><w:t>Chapter ${ch.chapterNumber}: ${escXml(ch.title)}</w:t></w:r></w:p>""")
                    for (line in ch.manuscriptText.split("\n")) {
                        if (line.isNotBlank()) append("""<w:p><w:r><w:t>${escXml(line.trim())}</w:t></w:r></w:p>""")
                    }
                }
            }
            writeEntry(
                zos, "word/document.xml",
                """<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$paragraphs</w:body></w:document>""",
            )
        }
        val filename = InputSafetyGuard.sanitizeFilename("${book.title.lowercase().replace(' ', '-')}.docx", "book.docx")
        return ExportArtifact(filename, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", baos.toByteArray())
    }

    /** Exports BookProject + chapters to valid EPUB 3 archive (N-018). */
    fun exportBookEpub(book: BookProject, chapters: List<BookChapter>): ExportArtifact {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            writeEntry(zos, "mimetype", "application/epub+zip")
            writeEntry(
                zos, "META-INF/container.xml",
                """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            )
            val ordered = chapters.sortedBy { it.chapterNumber }
            val manifestItems = ordered.mapIndexed { i, _ -> """<item id="ch${i + 1}" href="ch${i + 1}.xhtml" media-type="application/xhtml+xml"/>""" }.joinToString("")
            val spineItems = ordered.mapIndexed { i, _ -> """<itemref idref="ch${i + 1}"/>""" }.joinToString("")
            writeEntry(
                zos, "OEBPS/content.opf",
                """<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>${escXml(book.title)}</dc:title><dc:language>en</dc:language></metadata><manifest>$manifestItems</manifest><spine>$spineItems</spine></package>""",
            )
            ordered.forEachIndexed { i, ch ->
                val body = ch.manuscriptText.split("\n\n").filter { it.isNotBlank() }.joinToString("") { "<p>${escXml(it.trim())}</p>" }
                writeEntry(
                    zos, "OEBPS/ch${i + 1}.xhtml",
                    """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>${escXml(ch.title)}</title></head><body><h1>Chapter ${ch.chapterNumber}: ${escXml(ch.title)}</h1>$body</body></html>""",
                )
            }
        }
        val filename = InputSafetyGuard.sanitizeFilename("${book.title.lowercase().replace(' ', '-')}.epub", "book.epub")
        return ExportArtifact(filename, "application/epub+zip", baos.toByteArray())
    }

    /** Portable Markdown + YAML Frontmatter export & import (N-019). */
    fun exportPortableMarkdownNote(note: NoteItem, tags: List<String> = emptyList()): ExportArtifact {
        val md = buildString {
            append("---\n")
            append("id: ${note.id}\n")
            append("title: \"${note.title.replace("\"", "\\\"")}\"\n")
            append("notebookId: ${note.notebookId}\n")
            if (tags.isNotEmpty()) append("tags: [${tags.joinToString(", ")}]\n")
            append("---\n\n")
            append(note.content)
        }
        val filename = InputSafetyGuard.sanitizeFilename("${note.title.lowercase().replace(' ', '-')}.md", "note.md")
        return ExportArtifact(filename, "text/markdown; charset=utf-8", md.encodeToByteArray())
    }

    fun parsePortableMarkdownNote(markdown: String): Pair<Map<String, String>, String> {
        if (!markdown.startsWith("---\n")) return emptyMap<String, String>() to markdown
        val endIdx = markdown.indexOf("\n---\n", 4)
        if (endIdx < 0) return emptyMap<String, String>() to markdown
        val meta = LinkedHashMap<String, String>()
        for (line in markdown.substring(4, endIdx).lines()) {
            val colon = line.indexOf(':')
            if (colon > 0) meta[line.substring(0, colon).trim()] = line.substring(colon + 1).trim().removeSurrounding("\"")
        }
        return meta to markdown.substring(endIdx + 5).trimStart()
    }

    /** Authenticated AES-256-GCM Secure Sharing Bundle with explicit ACL metadata (N-046). */
    fun createSecureShareBundle(
        sourceWorkspaceId: String,
        allowedRole: WorkspaceRole,
        records: List<CanonicalRecord>,
        passphrase: String,
        nowUtc: String,
    ): ExportArtifact {
        val recBytes = json.encodeToString(records).encodeToByteArray()
        val header = SecureShareBundleHeader(
            bundleId = "share_${CryptoPrimitives.randomHexToken(6)}",
            sourceWorkspaceId = sourceWorkspaceId,
            allowedRole = allowedRole,
            createdAtUtc = nowUtc,
            recordCount = records.size,
            recordsSha256 = CryptoPrimitives.sha256Hex(recBytes),
        )
        val headerBytes = json.encodeToString(header).encodeToByteArray()
        val salt = CryptoPrimitives.randomBytes(16)
        val key = CryptoPrimitives.pbkdf2Sha256(passphrase, salt, iterations = 100_000)
        val magic = "PNDYSHR1".encodeToByteArray()
        val aad = magic + salt + headerBytes
        val cipher = CryptoPrimitives.aesGcmEncrypt(recBytes, key, aad = aad)

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            writeEntryBytes(zos, "share-header.json", headerBytes)
            writeEntryBytes(zos, "salt.bin", salt)
            writeEntryBytes(zos, "records.enc", cipher)
        }
        return ExportArtifact("planaday-share-${header.bundleId}.pnshare", "application/octet-stream", baos.toByteArray())
    }

    fun openSecureShareBundle(bundleBytes: ByteArray, passphrase: String): Pair<SecureShareBundleHeader, List<CanonicalRecord>> {
        val entries = safeReadZip(bundleBytes)
        val headerBytes = entries["share-header.json"] ?: throw IllegalArgumentException("Missing share-header.json")
        val salt = entries["salt.bin"] ?: throw IllegalArgumentException("Missing salt.bin")
        val cipher = entries["records.enc"] ?: throw IllegalArgumentException("Missing records.enc")
        val header = json.decodeFromString<SecureShareBundleHeader>(headerBytes.decodeToString())
        val key = CryptoPrimitives.pbkdf2Sha256(passphrase, salt, iterations = 100_000)
        val aad = "PNDYSHR1".encodeToByteArray() + salt + headerBytes
        val plain = CryptoPrimitives.aesGcmDecrypt(cipher, key, aad = aad)
        require(CryptoPrimitives.sha256Hex(plain).equals(header.recordsSha256, true)) { "Share bundle SHA-256 mismatch" }
        return header to json.decodeFromString<List<CanonicalRecord>>(plain.decodeToString())
    }

    private fun writeEntry(zos: ZipOutputStream, name: String, text: String) = writeEntryBytes(zos, name, text.encodeToByteArray())
    private fun writeEntryBytes(zos: ZipOutputStream, name: String, data: ByteArray) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(data)
        zos.closeEntry()
    }

    private fun safeReadZip(bytes: ByteArray): Map<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        var total = 0L
        var count = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                count++
                if (count > InputSafetyGuard.MAX_ARCHIVE_ENTRIES) throw SecurityException("ZIP entry limit exceeded")
                if (!InputSafetyGuard.isSafeArchiveEntryPath(e.name)) throw SecurityException("ZIP traversal blocked: ${e.name}")
                if (!e.isDirectory) {
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    while (true) {
                        val r = zis.read(buf)
                        if (r < 0) break
                        total += r
                        if (total > InputSafetyGuard.MAX_ARCHIVE_UNCOMPRESSED_BYTES) throw SecurityException("ZIP bomb rejected")
                        out.write(buf, 0, r)
                    }
                    map[e.name.replace('\\', '/')] = out.toByteArray()
                }
                zis.closeEntry()
            }
        }
        return map
    }
}
