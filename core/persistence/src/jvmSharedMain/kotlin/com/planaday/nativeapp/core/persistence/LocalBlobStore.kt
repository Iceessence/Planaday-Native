package com.planaday.nativeapp.core.persistence

import com.planaday.nativeapp.core.security.CryptoPrimitives
import com.planaday.nativeapp.core.security.InputSafetyGuard
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Content-addressed SHA-256 local attachment blob store with atomic .tmp staging (PD-015, PF-02).
 * Never shares a live directory with legacy Planaday installations (O-004).
 */
class LocalBlobStore(private val rootDir: File) {
    init {
        rootDir.mkdirs()
    }

    private fun blobFile(sha256: String): File {
        val clean = sha256.lowercase()
        require(Regex("^[0-9a-f]{64}$").matches(clean)) { "Invalid SHA-256 digest: $sha256" }
        val shard = File(rootDir, clean.substring(0, 2))
        shard.mkdirs()
        return File(shard, "$clean.bin")
    }

    /** Atomically stages bytes to .tmp, verifies SHA-256, then moves into content-addressed path. */
    fun putVerified(bytes: ByteArray, expectedSha256: String? = null): String {
        val computed = CryptoPrimitives.sha256Hex(bytes)
        if (expectedSha256 != null) {
            require(computed.equals(expectedSha256, ignoreCase = true)) {
                "Local blob SHA-256 mismatch: expected $expectedSha256, computed $computed"
            }
        }
        val dest = blobFile(computed)
        if (dest.exists() && dest.length() == bytes.size.toLong()) return computed
        val tmp = File(dest.parentFile, "$computed.${CryptoPrimitives.randomHexToken(6)}.tmp")
        tmp.writeBytes(bytes)
        try {
            Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Throwable) {
            Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return computed
    }

    fun getVerified(sha256: String): ByteArray? {
        val f = blobFile(sha256)
        if (!f.exists()) return null
        val bytes = f.readBytes()
        val actual = CryptoPrimitives.sha256Hex(bytes)
        check(actual.equals(sha256, ignoreCase = true)) {
            "Corrupt local blob detected on disk for $sha256 (computed $actual)"
        }
        return bytes
    }

    fun exists(sha256: String): Boolean = try {
        blobFile(sha256).exists()
    } catch (_: Throwable) {
        false
    }

    fun deleteUnreferenced(sha256: String): Boolean = try {
        blobFile(sha256).delete()
    } catch (_: Throwable) {
        false
    }

    fun relativePathFor(sha256: String): String {
        val clean = sha256.lowercase()
        return "${clean.substring(0, 2)}/$clean.bin"
    }

    /**
     * Bounded-memory O(1) streaming SHA-256 ingest for large attachments (100 MB - 1 GB+, Master §9 / PF-02).
     * Streams via a 64 KiB buffer into a .tmp staging file, computes SHA-256 on the fly, verifies expectedSha256,
     * and atomically moves into the content-addressed store without ever loading the full file into heap RAM.
     */
    fun putStreamVerified(input: InputStream, expectedSha256: String? = null): Pair<String, Long> {
        val staging = File(rootDir, "stream_${CryptoPrimitives.randomHexToken(8)}.tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytes = 0L
        try {
            staging.outputStream().buffered(65_536).use { out ->
                val buf = ByteArray(65_536)
                while (true) {
                    val read = input.read(buf)
                    if (read < 0) break
                    digest.update(buf, 0, read)
                    out.write(buf, 0, read)
                    totalBytes += read
                }
            }
            val computed = digest.digest().joinToString("") { "%02x".format(it) }
            if (expectedSha256 != null && !computed.equals(expectedSha256, ignoreCase = true)) {
                staging.delete()
                throw IllegalArgumentException("Streaming blob SHA-256 mismatch: expected $expectedSha256, computed $computed")
            }
            val dest = blobFile(computed)
            if (dest.exists() && dest.length() == totalBytes) {
                staging.delete()
                return computed to totalBytes
            }
            try {
                Files.move(staging.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Throwable) {
                Files.move(staging.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            return computed to totalBytes
        } catch (t: Throwable) {
            staging.delete()
            throw t
        }
    }

    /** Verifies an on-disk blob via bounded O(1) 64 KiB streaming SHA-256 digest without loading into memory. */
    fun verifyStreamOnDisk(sha256: String): Boolean {
        val f = blobFile(sha256)
        if (!f.exists()) return false
        val digest = MessageDigest.getInstance("SHA-256")
        f.inputStream().buffered(65_536).use { ins ->
            val buf = ByteArray(65_536)
            while (true) {
                val r = ins.read(buf)
                if (r < 0) break
                digest.update(buf, 0, r)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(sha256, ignoreCase = true)
    }

    fun safeFilename(raw: String): String = InputSafetyGuard.sanitizeFilename(raw)
}
