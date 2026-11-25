package com.planaday.nativeapp.core.interchange

import com.planaday.nativeapp.core.domain.CalendarDateMath
import com.planaday.nativeapp.core.model.*
import com.planaday.nativeapp.core.security.InputSafetyGuard
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * RFC 4180 state-machine CSV/TSV codec (PD-006):
 * accurately preserves quoted delimiters, escaped double-quotes (""), and embedded CRLF/LF newlines,
 * with optional formula-injection neutralization on export.
 */
object Rfc4180CsvCodec {
    fun parse(input: String, delimiter: Char = ','): List<List<String>> {
        if (input.isEmpty()) return emptyList()
        val rows = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val len = input.length

        while (i < len) {
            val ch = input[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < len && input[i + 1] == '"') {
                        field.append('"')
                        i += 2
                    } else {
                        inQuotes = false
                        i++
                    }
                } else {
                    field.append(ch)
                    i++
                }
            } else {
                when (ch) {
                    '"' -> {
                        if (field.isEmpty()) inQuotes = true else field.append(ch)
                        i++
                    }
                    delimiter -> {
                        currentRow.add(field.toString())
                        field.setLength(0)
                        i++
                    }
                    '\r' -> {
                        currentRow.add(field.toString())
                        field.setLength(0)
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                        i += if (i + 1 < len && input[i + 1] == '\n') 2 else 1
                    }
                    '\n' -> {
                        currentRow.add(field.toString())
                        field.setLength(0)
                        rows.add(currentRow)
                        currentRow = mutableListOf()
                        i++
                    }
                    else -> {
                        field.append(ch)
                        i++
                    }
                }
            }
        }
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString())
            rows.add(currentRow)
        }
        return rows
    }

    fun encode(
        rows: List<List<String>>,
        delimiter: Char = ',',
        neutralizeFormulas: Boolean = false,
    ): String {
        return rows.joinToString("\r\n") { row ->
            row.joinToString(delimiter.toString()) { raw ->
                val v = if (neutralizeFormulas) InputSafetyGuard.neutralizeCsvFormulaCell(raw) else raw
                if (v.contains(delimiter) || v.contains('"') || v.contains('\n') || v.contains('\r')) {
                    "\"${v.replace("\"", "\"\"")}\""
                } else v
            }
        }
    }

    fun exportCsvArtifact(filename: String, rows: List<List<String>>, neutralizeFormulas: Boolean = true): ExportArtifact {
        val safeName = InputSafetyGuard.sanitizeFilename(filename, "export.csv")
        val content = encode(rows, ',', neutralizeFormulas)
        return ExportArtifact(safeName, "text/csv; charset=utf-8", content.encodeToByteArray())
    }
}

/**
 * RFC 5545 iCalendar (.ics) exporter & importer (C-0060, N-014):
 * handles empty calendars with valid UTF-8 VCALENDAR envelope, line unfolding, TZID, RRULE, EXDATE,
 * exclusive multi-day all-day DTEND, and UID deduplication.
 */
object Rfc5545IcsCodec {
    private val ICS_DAYS = listOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")

    private fun escapeText(s: String): String = s
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")

    private fun unescapeText(s: String): String = s
        .replace("\\n", "\n")
        .replace("\\N", "\n")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\\", "\\")

    fun exportCalendar(
        events: List<CalendarEvent>,
        calendarName: String = "Planaday Calendar",
        dtStampUtc: String = "20261125T000000Z",
    ): ExportArtifact {
        val lines = mutableListOf(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//Planaday Native//Productivity OS//EN",
            "CALSCALE:GREGORIAN",
            "X-WR-CALNAME:${escapeText(calendarName)}",
        )

        for (ev in events) {
            lines.add("BEGIN:VEVENT")
            lines.add("UID:${ev.icsUid ?: "${ev.id}@planaday.native"}")
            lines.add("DTSTAMP:$dtStampUtc")
            lines.add("SUMMARY:${escapeText(ev.title)}")
            if (ev.allDay) {
                lines.add("DTSTART;VALUE=DATE:${ev.startDateIso.replace("-", "")}")
                val exclEnd = CalendarDateMath.addDays(ev.endDateIso, 1).replace("-", "")
                lines.add("DTEND;VALUE=DATE:$exclEnd")
            } else {
                val st = "${ev.startDateIso.replace("-", "")}T${(ev.startTimeLocal ?: "09:00").replace(":", "")}00"
                val et = "${ev.endDateIso.replace("-", "")}T${(ev.endTimeLocal ?: "10:00").replace(":", "")}00"
                if (ev.floating) {
                    lines.add("DTSTART:$st")
                    lines.add("DTEND:$et")
                } else {
                    lines.add("DTSTART;TZID=${ev.timeZone}:$st")
                    lines.add("DTEND;TZID=${ev.timeZone}:$et")
                }
            }
            ev.location?.takeIf { it.isNotBlank() }?.let { lines.add("LOCATION:${escapeText(it)}") }
            ev.description.takeIf { it.isNotBlank() }?.let { lines.add("DESCRIPTION:${escapeText(it)}") }
            lines.add("STATUS:${ev.eventStatus.uppercase()}")
            ev.recurrence?.let { rule ->
                val parts = mutableListOf("FREQ=${rule.freq.name}")
                if (rule.interval > 1) parts.add("INTERVAL=${rule.interval}")
                if (rule.byWeekday.isNotEmpty()) {
                    val pref = rule.bySetPos?.toString() ?: ""
                    parts.add("BYDAY=${rule.byWeekday.joinToString(",") { "$pref${ICS_DAYS[it]}" }}")
                }
                rule.untilDateIso?.let { parts.add("UNTIL=${it.replace("-", "")}T235959Z") }
                rule.count?.let { parts.add("COUNT=$it") }
                lines.add("RRULE:${parts.joinToString(";")}")
                if (rule.exceptionsIso.isNotEmpty()) {
                    val ex = rule.exceptionsIso.joinToString(",") { d ->
                        if (ev.allDay) d.replace("-", "") else "${d.replace("-", "")}T${(ev.startTimeLocal ?: "09:00").replace(":", "")}00"
                    }
                    lines.add("EXDATE${if (ev.allDay) ";VALUE=DATE" else ""}:$ex")
                }
            }
            lines.add("END:VEVENT")
        }
        lines.add("END:VCALENDAR")
        val text = lines.joinToString("\r\n") + "\r\n"
        val filename = InputSafetyGuard.sanitizeFilename("${calendarName.lowercase().replace(' ', '-')}.ics", "calendar.ics")
        return ExportArtifact(filename, "text/calendar; charset=utf-8", text.encodeToByteArray())
    }

    fun parseCalendar(icsText: String, workspaceId: String, calendarId: String, nowUtc: String): List<CalendarEvent> {
        val unfolded = icsText.replace(Regex("\r?\n[ \t]"), "")
        val lines = unfolded.split(Regex("\r?\n"))
        val events = mutableListOf<CalendarEvent>()
        var inEvent = false
        var props = mutableMapOf<String, MutableList<Pair<String, String>>>()

        for (line in lines) {
            if (line == "BEGIN:VEVENT") {
                inEvent = true
                props = mutableMapOf()
                continue
            }
            if (line == "END:VEVENT" && inEvent) {
                inEvent = false
                val dtStart = props["DTSTART"]?.firstOrNull() ?: continue
                val startParsed = parseIcsDate(dtStart.first, dtStart.second)
                val dtEnd = props["DTEND"]?.firstOrNull()
                var endDate = startParsed.dateIso
                var endTime = startParsed.timeHHMM
                if (dtEnd != null) {
                    val ep = parseIcsDate(dtEnd.first, dtEnd.second)
                    if (startParsed.allDay) {
                        val incl = CalendarDateMath.addDays(ep.dateIso, -1)
                        endDate = if (incl >= startParsed.dateIso) incl else startParsed.dateIso
                    } else {
                        endDate = ep.dateIso
                        endTime = ep.timeHHMM
                    }
                }
                val uid = props["UID"]?.firstOrNull()?.first ?: "ics-${events.size + 1}"
                val summary = unescapeText(props["SUMMARY"]?.firstOrNull()?.first ?: "Imported Event")
                val rruleRaw = props["RRULE"]?.firstOrNull()?.first
                val exDates = props["EXDATE"].orEmpty().flatMap { it.first.split(",") }
                    .filter { it.length >= 8 }
                    .map { "${it.substring(0, 4)}-${it.substring(4, 6)}-${it.substring(6, 8)}" }
                val recurrence = rruleRaw?.let { parseRrule(it, exDates) }
                events.add(
                    CalendarEvent(
                        id = uid,
                        workspaceId = workspaceId,
                        calendarId = calendarId,
                        title = summary,
                        description = unescapeText(props["DESCRIPTION"]?.firstOrNull()?.first ?: ""),
                        startDateIso = startParsed.dateIso,
                        endDateIso = endDate,
                        startTimeLocal = startParsed.timeHHMM,
                        endTimeLocal = endTime,
                        allDay = startParsed.allDay,
                        floating = startParsed.floating,
                        timeZone = startParsed.tz,
                        location = props["LOCATION"]?.firstOrNull()?.first?.let(::unescapeText),
                        recurrence = recurrence,
                        icsUid = uid,
                        createdAtUtc = nowUtc,
                        updatedAtUtc = nowUtc,
                    )
                )
                continue
            }
            if (!inEvent || ':' !in line) continue
            val colon = line.indexOf(':')
            val left = line.substring(0, colon)
            val value = line.substring(colon + 1).trim()
            val semi = left.indexOf(';')
            val key = (if (semi >= 0) left.substring(0, semi) else left).uppercase()
            val params = if (semi >= 0) left.substring(semi + 1) else ""
            props.getOrPut(key) { mutableListOf() }.add(value to params)
        }
        return events
    }

    private data class ParsedIcsDate(val dateIso: String, val timeHHMM: String?, val allDay: Boolean, val tz: String, val floating: Boolean)

    private fun parseIcsDate(raw: String, params: String): ParsedIcsDate {
        val tzMatch = Regex("TZID=([^;:]+)", RegexOption.IGNORE_CASE).find(params)?.groupValues?.get(1)
        val isDateOnly = params.contains("VALUE=DATE", ignoreCase = true) || Regex("^\\d{8}$").matches(raw)
        val dateIso = "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}"
        if (isDateOnly) return ParsedIcsDate(dateIso, null, true, tzMatch ?: "UTC", true)
        val hh = raw.substring(9, 11)
        val mm = raw.substring(11, 13)
        val isUtc = raw.endsWith("Z")
        return ParsedIcsDate(dateIso, "$hh:$mm", false, if (isUtc) "UTC" else (tzMatch ?: "UTC"), tzMatch == null && !isUtc)
    }

    private fun parseRrule(raw: String, exDates: List<String>): RecurrenceRule {
        val parts = raw.split(";").mapNotNull { p ->
            val eq = p.indexOf('=')
            if (eq > 0) p.substring(0, eq).uppercase() to p.substring(eq + 1).uppercase() else null
        }.toMap()
        val freq = when (parts["FREQ"]) {
            "WEEKLY" -> RecurrenceFreq.WEEKLY
            "MONTHLY" -> RecurrenceFreq.MONTHLY
            "YEARLY" -> RecurrenceFreq.YEARLY
            else -> RecurrenceFreq.DAILY
        }
        val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val weekdays = mutableListOf<Int>()
        var setPos: Int? = null
        parts["BYDAY"]?.split(",")?.forEach { tok ->
            val m = Regex("^(-?\\d)?(SU|MO|TU|WE|TH|FR|SA)$").matchEntire(tok)
            if (m != null) {
                if (m.groupValues[1].isNotEmpty()) setPos = m.groupValues[1].toInt()
                val idx = ICS_DAYS.indexOf(m.groupValues[2])
                if (idx >= 0) weekdays.add(idx)
            }
        }
        val until = parts["UNTIL"]?.takeIf { it.length >= 8 }?.let {
            "${it.substring(0, 4)}-${it.substring(4, 6)}-${it.substring(6, 8)}"
        }
        val count = parts["COUNT"]?.toIntOrNull()
        return RecurrenceRule(freq, interval, weekdays, setPos, untilDateIso = until, count = count, exceptionsIso = exDates)
    }
}

/**
 * High-fidelity Presentation HTML Exporter (PD-018) & Book Markdown/HTML-DOC Exporter (C-0065).
 */
object RichDocumentExporters {
    private fun escHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    @OptIn(ExperimentalEncodingApi::class)
    fun exportPresentationHtmlDeck(
        deck: PresentationDeck,
        blobResolver: (String) -> Pair<String, ByteArray>? = { null }, // sha256 -> (mimeType, bytes)
    ): ExportArtifact {
        val cssRatio = if (deck.aspectRatio == "4:3") "4 / 3" else "16 / 9"
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">")
        sb.append("<title>${escHtml(deck.title)}</title>")
        sb.append("<style>body{margin:0;font-family:system-ui,sans-serif;background:#111;color:#111;}")
        sb.append(".slide{position:relative;width:960px;aspect-ratio:$cssRatio;margin:24px auto;overflow:hidden;box-shadow:0 12px 32px rgba(0,0,0,.35);}")
        sb.append(".obj{position:absolute;box-sizing:border-box;} table{width:100%;border-collapse:collapse;} td,th{border:1px solid #cbd5e1;padding:6px;}</style></head><body>\n")

        for (slide in deck.slides.sortedBy { it.order }) {
            sb.append("""<section class="slide" data-transition="${escHtml(slide.transition)}" style="background:${escHtml(slide.backgroundHex)};">""")
            for (obj in slide.objects.sortedBy { it.zIndex }) {
                val style = "left:${obj.xPct}%;top:${obj.yPct}%;width:${obj.widthPct}%;height:${obj.heightPct}%;transform:rotate(${obj.rotationDeg}deg);opacity:${obj.opacity};z-index:${obj.zIndex};"
                sb.append("""<div class="obj" style="$style">""")
                when (obj.kind) {
                    SlideObjectKind.IMAGE -> {
                        val resolved = obj.blobSha256?.let(blobResolver)
                        if (resolved != null) {
                            val b64 = Base64.encode(resolved.second)
                            sb.append("""<img src="data:${escHtml(resolved.first)};base64,$b64" alt="${escHtml(obj.text)}" style="width:100%;height:100%;object-fit:contain;"/>""")
                        } else {
                            sb.append("<div>[Image: ${escHtml(obj.text)}]</div>")
                        }
                    }
                    SlideObjectKind.TABLE -> {
                        sb.append("<table>")
                        for (row in obj.tableRows) {
                            sb.append("<tr>")
                            for (cell in row) sb.append("<td>${escHtml(cell)}</td>")
                            sb.append("</tr>")
                        }
                        sb.append("</table>")
                    }
                    SlideObjectKind.CODE -> {
                        sb.append("""<pre><code>${escHtml(obj.text)}</code></pre>""")
                    }
                    else -> {
                        sb.append("""<div>${escHtml(obj.text)}</div>""")
                    }
                }
                sb.append("</div>")
            }
            if (slide.speakerNotes.isNotBlank()) {
                sb.append("""<aside class="speaker-notes" hidden>${escHtml(slide.speakerNotes)}</aside>""")
            }
            sb.append("</section>\n")
        }
        sb.append("</body></html>")
        val filename = InputSafetyGuard.sanitizeFilename("${deck.title.lowercase().replace(' ', '-')}.html", "presentation.html")
        return ExportArtifact(filename, "text/html; charset=utf-8", sb.toString().encodeToByteArray())
    }

    fun exportBookMarkdown(book: BookProject, chapters: List<BookChapter>): ExportArtifact {
        val sb = StringBuilder()
        sb.append("# ${book.title}\n\n")
        if (book.synopsis.isNotBlank()) sb.append("> ${book.synopsis}\n\n")
        for (ch in chapters.sortedBy { it.chapterNumber }) {
            sb.append("## Chapter ${ch.chapterNumber}: ${ch.title}\n\n")
            sb.append(ch.manuscriptText.trim()).append("\n\n")
        }
        val filename = InputSafetyGuard.sanitizeFilename("${book.title.lowercase().replace(' ', '-')}.md", "book.md")
        return ExportArtifact(filename, "text/markdown; charset=utf-8", sb.toString().encodeToByteArray())
    }

    fun exportBookHtmlDoc(book: BookProject, chapters: List<BookChapter>): ExportArtifact {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>${escHtml(book.title)}</title></head><body>")
        sb.append("<h1>${escHtml(book.title)}</h1>")
        for (ch in chapters.sortedBy { it.chapterNumber }) {
            sb.append("<h2>Chapter ${ch.chapterNumber}: ${escHtml(ch.title)}</h2>")
            for (para in ch.manuscriptText.split("\n\n")) {
                if (para.isNotBlank()) sb.append("<p>${escHtml(para.trim())}</p>")
            }
        }
        sb.append("</body></html>")
        val filename = InputSafetyGuard.sanitizeFilename("${book.title.lowercase().replace(' ', '-')}.doc", "book.doc")
        return ExportArtifact(filename, "application/msword", sb.toString().encodeToByteArray())
    }
}

@Serializable
data class BackupBlobEntryMeta(
    val sha256: String,
    val mimeType: String,
    val originalFilename: String,
    val byteSize: Long,
)

@Serializable
data class NativeBackupManifestV1(
    val formatId: String = BuildIdentity.BACKUP_FORMAT_ID,
    val formatVersion: Int = 1,
    val appVersion: String = BuildIdentity.CURRENT_APP_VERSION,
    val schemaVersion: Int = BuildIdentity.CURRENT_CLIENT_SCHEMA_VERSION,
    val exportedAtUtc: String,
    val recordCount: Int,
    val revisionCount: Int,
    val blobCount: Int,
    val recordsJsonSha256: String,
    val blobs: Map<String, BackupBlobEntryMeta>,
    val encrypted: Boolean = false,
)

@Serializable
data class NativeBackupBundlePayload(
    val formatId: String = BuildIdentity.BACKUP_FORMAT_ID,
    val exportedAtUtc: String,
    val workspaces: List<Workspace>,
    val records: List<CanonicalRecord>,
    val relations: List<RecordRelation>,
    val revisions: List<RevisionSnapshot>,
)

data class VerifiedRestoreBundle(
    val manifest: NativeBackupManifestV1,
    val payload: NativeBackupBundlePayload,
    val verifiedBlobs: Map<String, ByteArray>,
)

data class PptxImportResult(
    val deck: PresentationDeck,
    val importedBlobs: Map<String, Pair<String, ByteArray>>, // sha256 -> (mime, bytes)
    val fidelityReport: List<String>,
)
