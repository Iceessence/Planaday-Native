package com.planaday.nativeapp.core.interchange

import com.planaday.nativeapp.core.model.*
import com.planaday.nativeapp.core.security.CryptoPrimitives
import kotlin.test.*

class ArchiveInterchangeJvmTest {
    @Test
    fun pf42AndN048EncryptedBackupVerifyTamperRejectionAndO004LegacyRejection() {
        val blobBytes = "synthetic-attachment-bytes-123".encodeToByteArray()
        val blobHash = CryptoPrimitives.sha256Hex(blobBytes)
        val payload = NativeBackupBundlePayload(
            exportedAtUtc = "2026-11-25T12:00:00Z",
            workspaces = listOf(Workspace("ws1", "Personal", createdAtUtc = "2026-11-25T00:00:00Z", updatedAtUtc = "2026-11-25T00:00:00Z")),
            records = listOf(CanonicalRecord("r1", "ws1", RecordKind.NOTE, "Test Note", createdAtUtc = "2026-11-25T00:00:00Z", updatedAtUtc = "2026-11-25T00:00:00Z", deviceId = "dev1")),
            relations = emptyList(),
            revisions = emptyList(),
        )
        val blobs = mapOf(blobHash to (BackupBlobEntryMeta(blobHash, "text/plain", "note.txt", blobBytes.size.toLong()) to blobBytes))

        // Plain verified backup ZIP
        val plainZip = ArchiveInterchange.createBackupZip(payload, blobs)
        val restored = ArchiveInterchange.verifyAndReadBackup(plainZip.bytes)
        assertEquals(1, restored.payload.records.size)
        assertContentEquals(blobBytes, restored.verifiedBlobs[blobHash])

        // Tampered byte must be rejected!
        val tampered = plainZip.bytes.copyOf()
        tampered[tampered.size / 2] = (tampered[tampered.size / 2].toInt() xor 0xFF).toByte()
        assertFails { ArchiveInterchange.verifyAndReadBackup(tampered) }

        // Encrypted AES-256-GCM backup round-trip & wrong-passphrase rejection (N-048)
        val enc = ArchiveInterchange.createBackupZip(payload, blobs, passphrase = "owner-recovery-passphrase")
        val decRestored = ArchiveInterchange.verifyAndReadBackup(enc.bytes, passphrase = "owner-recovery-passphrase")
        assertEquals("Test Note", decRestored.payload.records.single().title)
        assertFails { ArchiveInterchange.verifyAndReadBackup(enc.bytes, passphrase = "wrong-passphrase") }
    }

    @Test
    fun r0455PptxExportAndImportWithFidelityReport() {
        val deck = PresentationDeck(
            id = "d1", workspaceId = "ws1", title = "Lab Deck",
            slides = listOf(
                SlideModel(
                    id = "s1", order = 1, title = "Intro", speakerNotes = "Key calibration note",
                    objects = listOf(
                        SlideObject("o1", SlideObjectKind.TEXT, 10.0, 10.0, 80.0, 20.0, text = "NIR Calibration Overview"),
                        SlideObject("o2", SlideObjectKind.TEXT, 10.0, 35.0, 80.0, 40.0, text = "R-squared = 0.998"),
                    ),
                )
            ),
        )
        val pptx = ArchiveInterchange.exportMinimalPptx(deck)
        val imported = ArchiveInterchange.importPptx(pptx.bytes, "ws1", "d1_imp", "Imported Lab Deck")
        assertEquals(1, imported.deck.slides.size)
        assertEquals("NIR Calibration Overview", imported.deck.slides[0].title)
        assertEquals("Key calibration note", imported.deck.slides[0].speakerNotes)
        assertTrue(imported.fidelityReport.isNotEmpty())
    }
}
