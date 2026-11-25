package com.planaday.nativeapp.core.security

import java.io.File
import java.util.concurrent.TimeUnit

data class ExternalCompileResult(
    val toolUsed: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
    val truncated: Boolean,
)

/**
 * Windows/JVM Desktop isolated external compiler/runner adapter (R-0322, PD-005).
 * Executes strictly in a separate OS process inside a disposable temp working directory,
 * with stripped environment, wall-clock timeout, and hard output byte cap — NEVER inside app process!
 */
object DesktopExternalToolchainAdapter {
    const val MAX_OUTPUT_BYTES: Int = 64 * 1024

    fun runIsolatedCommand(
        command: List<String>,
        files: Map<String, String>,
        timeoutMs: Long = 8_000L,
        userExplicitlyOptedIn: Boolean,
    ): ExternalCompileResult {
        require(userExplicitlyOptedIn) {
            "External toolchain execution requires explicit user opt-in (PD-005 security boundary)."
        }
        require(command.isNotEmpty()) { "Command cannot be empty" }
        val tempDir = java.nio.file.Files.createTempDirectory("planaday-sandbox-").toFile()
        try {
            for ((relPath, content) in files) {
                require(InputSafetyGuard.isSafeArchiveEntryPath(relPath)) { "Unsafe sandbox file path: $relPath" }
                val f = File(tempDir, relPath)
                f.parentFile?.mkdirs()
                f.writeText(content, Charsets.UTF_8)
            }
            val pb = ProcessBuilder(command)
            pb.directory(tempDir)
            // Keep minimal PATH/SystemRoot so OS toolchains launch while stripping sensitive user tokens/env
            val inheritedPath = pb.environment()["PATH"] ?: pb.environment()["Path"] ?: ""
            val sysRoot = pb.environment()["SystemRoot"] ?: ""
            pb.environment().clear()
            if (inheritedPath.isNotEmpty()) pb.environment()["PATH"] = inheritedPath
            if (sysRoot.isNotEmpty()) pb.environment()["SystemRoot"] = sysRoot

            val proc = pb.start()
            val finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return ExternalCompileResult(
                    toolUsed = command.first(),
                    exitCode = -1,
                    stdout = "",
                    stderr = "Process terminated after ${timeoutMs}ms timeout",
                    timedOut = true,
                    truncated = false,
                )
            }
            val rawOut = proc.inputStream.readNBytes(MAX_OUTPUT_BYTES + 1)
            val rawErr = proc.errorStream.readNBytes(MAX_OUTPUT_BYTES + 1)
            val truncated = rawOut.size > MAX_OUTPUT_BYTES || rawErr.size > MAX_OUTPUT_BYTES
            return ExternalCompileResult(
                toolUsed = command.first(),
                exitCode = proc.exitValue(),
                stdout = rawOut.take(MAX_OUTPUT_BYTES).toByteArray().decodeToString(),
                stderr = rawErr.take(MAX_OUTPUT_BYTES).toByteArray().decodeToString(),
                timedOut = false,
                truncated = truncated,
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
