package com.planaday.nativeapp.server.storage

import com.planaday.nativeapp.core.security.CryptoPrimitives
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Staged content-addressed SHA-256 server blob store (PD-015, CONT-003).
 * Computes SHA-256 independently, rejects any digest mismatch, stages to .tmp, and atomically renames.
 */
class ServerBlobStore(private val rootDir: File) {
    init {
        rootDir.mkdirs()
    }

    private fun blobPath(sha256: String): File {
        val clean = sha256.lowercase()
        require(Regex("^[0-9a-f]{64}$").matches(clean)) { "Invalid SHA-256 format" }
        val shard = File(rootDir, clean.substring(0, 2))
        shard.mkdirs()
        return File(shard, "$clean.bin")
    }

    fun storeStagedVerified(expectedSha256: String, bytes: ByteArray): Pair<String, Long> {
        val cleanExpected = expectedSha256.lowercase()
        require(Regex("^[0-9a-f]{64}$").matches(cleanExpected)) { "Invalid SHA-256 path parameter" }
        val actual = CryptoPrimitives.sha256Hex(bytes)
        if (!actual.equals(cleanExpected, ignoreCase = true)) {
            throw IllegalArgumentException("SHA-256 content mismatch: path=$cleanExpected computed=$actual")
        }
        val dest = blobPath(actual)
        if (!dest.exists() || dest.length() != bytes.size.toLong()) {
            val tmp = File(dest.parentFile, "$actual.${CryptoPrimitives.randomHexToken(6)}.tmp")
            tmp.writeBytes(bytes)
            try {
                Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Throwable) {
                Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
        return actual to bytes.size.toLong()
    }

    fun readVerified(sha256: String): ByteArray? {
        val file = blobPath(sha256)
        if (!file.exists()) return null
        val bytes = file.readBytes()
        val computed = CryptoPrimitives.sha256Hex(bytes)
        check(computed.equals(sha256, ignoreCase = true)) { "Server blob integrity corruption detected for $sha256" }
        return bytes
    }

    fun totalStats(): Pair<Long, Long> {
        var count = 0L
        var bytes = 0L
        rootDir.walkTopDown().filter { it.isFile && it.name.endsWith(".bin") }.forEach {
            count++
            bytes += it.length()
        }
        return count to bytes
    }
}
