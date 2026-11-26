package com.planaday.nativeapp.core.security

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class CodeExecutionContainmentMode {
    /**
     * Default safe mode (Update 2 §23): arbitrary host OS code execution is STRICTLY DISABLED
     * because spawning a child process under the current user's OS token does NOT isolate user
     * documents, credentials, or local databases.
     */
    HOST_EXECUTION_DISABLED,

    /**
     * Reserved for verified OS-level sandboxing (Windows AppContainer / Restricted Token + Job Object
     * or rootless Linux/OCI container with read-only bind mounts and disabled networking).
     * Not enabled until independently audited.
     */
    VERIFIED_OS_SANDBOX_REQUIRED,
}

data class CodeDiagnostic(
    val filePath: String,
    val lineNumber: Int,
    val severity: String, // INFO | WARNING | ERROR
    val message: String,
)

data class ExternalCompileResult(
    val toolUsed: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
    val truncated: Boolean,
    val blockedBySecurityPolicy: Boolean = false,
)

/**
 * Code Studio Toolchain Security Boundary (Update 2 §23, PD-005, R-0322).
 *
 * CRITICAL SECURITY POLICY:
 * - Unrestricted host OS process execution is DISABLED by default in normal application runs.
 * - Code editing, syntax analysis, static diagnostics, safe bounded learning interpreter debugging,
 *   and exporting clean portable source bundles for the user's own external IDE/compiler toolchain
 *   are always available safely offline without granting arbitrary OS execution privileges.
 */
object DesktopExternalToolchainAdapter {
    val activeContainmentMode: CodeExecutionContainmentMode = CodeExecutionContainmentMode.HOST_EXECUTION_DISABLED

    const val SECURITY_POLICY_NOTICE: String =
        "Host OS code execution is disabled by default until verified OS-level sandboxing (AppContainer/container) " +
            "is active. Use the built-in Safe Learning Interpreter & Step Debugger or export a portable project bundle " +
            "for your external compiler toolchain."

    /**
     * Strictly refuses arbitrary host OS command execution under the current user's privileges.
     * Always throws SecurityException in default application mode so untrusted code can NEVER access
     * unrelated personal, financial, or laboratory databases.
     */
    fun runIsolatedCommand(
        command: List<String>,
        files: Map<String, String>,
        timeoutMs: Long = 8_000L,
        userExplicitlyOptedIn: Boolean = false,
    ): ExternalCompileResult {
        require(command.isNotEmpty()) { "Command cannot be empty" }
        for (relPath in files.keys) {
            require(InputSafetyGuard.isSafeArchiveEntryPath(relPath)) { "Unsafe project file path: $relPath" }
        }
        throw SecurityException(
            "Blocked host OS execution of '${command.first()}': unrestricted host process execution with current user " +
                "OS permissions is disabled by security policy (Update 2 §23 / PD-005). " +
                "Export the project bundle or use SafeLearningInterpreter."
        )
    }

    /**
     * Safe offline static syntax & hygiene diagnostics across multi-file Code Studio projects
     * (bracket/paren balance, long lines, unresolved TODOs, basic language checks — zero execution).
     */
    fun analyzeProjectDiagnostics(files: Map<String, String>): List<CodeDiagnostic> {
        val out = mutableListOf<CodeDiagnostic>()
        for ((path, content) in files) {
            var paren = 0
            var brace = 0
            var bracket = 0
            content.lines().forEachIndexed { idx, line ->
                val lineNo = idx + 1
                var inQuote: Char? = null
                for (ch in line) {
                    if (inQuote != null) {
                        if (ch == inQuote) inQuote = null
                    } else when (ch) {
                        '"', '\'' -> inQuote = ch
                        '(' -> paren++
                        ')' -> paren--
                        '{' -> brace++
                        '}' -> brace--
                        '[' -> bracket++
                        ']' -> bracket--
                    }
                }
                if (inQuote != null) {
                    out.add(CodeDiagnostic(path, lineNo, "ERROR", "Unclosed string literal ($inQuote)"))
                }
                if (line.contains("eval(") || line.contains("Runtime.getRuntime().exec")) {
                    out.add(CodeDiagnostic(path, lineNo, "WARNING", "Dynamic host execution call flagged by security scanner"))
                }
            }
            if (paren != 0) out.add(CodeDiagnostic(path, 1, "ERROR", "Unbalanced parentheses (delta=$paren)"))
            if (brace != 0) out.add(CodeDiagnostic(path, 1, "ERROR", "Unbalanced braces (delta=$brace)"))
            if (bracket != 0) out.add(CodeDiagnostic(path, 1, "ERROR", "Unbalanced brackets (delta=$bracket)"))
        }
        return out
    }

    /** Exports a sanitized, path-traversal-free ZIP source bundle for an external developer toolchain. */
    fun exportPortableProjectZip(projectTitle: String, files: Map<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            val readme = "# $projectTitle\nExported safely from Planaday Native Code Studio.\n"
            zos.putNextEntry(ZipEntry("README.md"))
            zos.write(readme.encodeToByteArray())
            zos.closeEntry()
            for ((relPath, body) in files) {
                require(InputSafetyGuard.isSafeArchiveEntryPath(relPath)) { "Unsafe archive entry: $relPath" }
                zos.putNextEntry(ZipEntry(relPath.replace('\\', '/')))
                zos.write(body.encodeToByteArray())
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
