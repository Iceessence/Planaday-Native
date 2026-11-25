package com.planaday.nativeapp.core.interchange

import com.planaday.nativeapp.core.model.*
import com.planaday.nativeapp.core.security.CryptoPrimitives
import kotlin.test.*

class CoreInterchangeContractTest {
    @Test
    fun pd006Rfc4180CsvQuotedCommasQuotesAndMultilineRoundTrip() {
        val inputRows = listOf(
            listOf("Name", "Notes", "Formula"),
            listOf("Sample, \"A\"", "Line 1\nLine 2", "=CMD|' /C calc'!A0"),
        )
        val csv = Rfc4180CsvCodec.encode(inputRows, neutralizeFormulas = false)
        val parsed = Rfc4180CsvCodec.parse(csv)
        assertEquals(inputRows, parsed)

        val neutralized = Rfc4180CsvCodec.encode(inputRows, neutralizeFormulas = true)
        val parsedSafe = Rfc4180CsvCodec.parse(neutralized)
        assertEquals("'=CMD|' /C calc'!A0", parsedSafe[1][2])
    }

    @Test
    fun c0060EmptyIcsEnvelopeAndFullEventRoundTrip() {
        val empty = Rfc5545IcsCodec.exportCalendar(emptyList(), "Personal")
        val emptyText = empty.bytes.decodeToString()
        assertEquals("personal.ics", empty.filename)
        assertTrue(emptyText.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(emptyText.trimEnd().endsWith("END:VCALENDAR"))

        val ev = CalendarEvent(
            id = "ev1", workspaceId = "ws1", calendarId = "cal1",
            title = "Conference, Day 1", description = "Line1\nLine2",
            startDateIso = "2026-12-01", endDateIso = "2026-12-03", allDay = true,
            recurrence = RecurrenceRule(RecurrenceFreq.WEEKLY, interval = 2, byWeekday = listOf(2), exceptionsIso = listOf("2026-12-15")),
            icsUid = "uid-conf-1", createdAtUtc = "2026-11-25T00:00:00Z", updatedAtUtc = "2026-11-25T00:00:00Z",
        )
        val exported = Rfc5545IcsCodec.exportCalendar(listOf(ev))
        val reimported = Rfc5545IcsCodec.parseCalendar(exported.bytes.decodeToString(), "ws1", "cal1", "2026-11-25T00:00:00Z")
        assertEquals(1, reimported.size)
        assertEquals("Conference, Day 1", reimported[0].title)
        assertEquals("2026-12-03", reimported[0].endDateIso) // Inclusive end date restored accurately from RFC 5545 exclusive DTEND
        assertEquals(listOf("2026-12-15"), reimported[0].recurrence?.exceptionsIso)
    }

    @Test
    fun pd018PresentationHtmlFidelityAndC0065BookExport() {
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4)
        val pngHash = CryptoPrimitives.sha256Hex(pngBytes)
        val deck = PresentationDeck(
            id = "deck1", workspaceId = "ws1", title = "Quarterly Lab Review", aspectRatio = "4:3",
            slides = listOf(
                SlideModel(
                    id = "s1", order = 1, transition = "fade", speakerNotes = "Mention NIR bias.",
                    objects = listOf(
                        SlideObject("o1", SlideObjectKind.IMAGE, 5.0, 5.0, 40.0, 40.0, rotationDeg = 15.0, opacity = 0.85, blobSha256 = pngHash),
                        SlideObject("o2", SlideObjectKind.TABLE, 50.0, 10.0, 45.0, 30.0, tableRows = listOf(listOf("Metric", "Value"), listOf("RMSE", "0.21"))),
                        SlideObject("o3", SlideObjectKind.CODE, 5.0, 55.0, 90.0, 30.0, text = "val x = 42"),
                    ),
                )
            ),
        )
        val htmlArtifact = RichDocumentExporters.exportPresentationHtmlDeck(deck) { if (it == pngHash) "image/png" to pngBytes else null }
        val html = htmlArtifact.bytes.decodeToString()
        assertEquals("quarterly-lab-review.html", htmlArtifact.filename)
        assertTrue(html.contains("aspect-ratio:4 / 3"))
        assertTrue(html.contains("data-transition=\"fade\""))
        assertTrue(html.contains("data:image/png;base64,"))
        assertTrue(html.contains("rotate(15.0deg)"))
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("<code>val x = 42</code>"))
        assertTrue(html.contains("Mention NIR bias."))

        val book = BookProject("b1", "ws1", title = "Starfall Chronicles")
        val ch = BookChapter("c1", "b1", "ws1", 1, "Dawn", manuscriptText = "First paragraph.\n\nSecond paragraph.")
        val md = RichDocumentExporters.exportBookMarkdown(book, listOf(ch))
        val doc = RichDocumentExporters.exportBookHtmlDoc(book, listOf(ch))
        assertEquals("starfall-chronicles.md", md.filename)
        assertTrue(md.bytes.decodeToString().contains("## Chapter 1: Dawn"))
        assertEquals("starfall-chronicles.doc", doc.filename)
        assertTrue(doc.bytes.decodeToString().contains("<p>Second paragraph.</p>"))
    }
}
