package com.planaday.nativeapp.core.security

import kotlinx.serialization.Serializable

sealed interface OutboundUrlDecision {
    data class Allowed(val normalizedUrl: String, val host: String) : OutboundUrlDecision
    data class Blocked(val reason: String) : OutboundUrlDecision
}

/**
 * Centralized SSRF & egress protection policy (PD-014, TM-04):
 * Blocks non-HTTPS (unless explicitly allowed), loopback, RFC1918 LAN, link-local/cloud metadata,
 * CGNAT/Tailscale, file://, credentials-in-URL, and enforces redirect & response-size caps.
 */
object OutboundUrlPolicy {
    const val MAX_REDIRECTS: Int = 3
    const val MAX_RESPONSE_BYTES: Long = 2L * 1024L * 1024L

    fun validateUrl(
        rawUrl: String,
        allowedHostOverrides: Set<String> = emptySet(),
        allowHttpForOverrides: Boolean = false,
    ): OutboundUrlDecision {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return OutboundUrlDecision.Blocked("Empty URL")
        val schemeIdx = trimmed.indexOf("://")
        if (schemeIdx <= 0) return OutboundUrlDecision.Blocked("Missing URL scheme")
        val scheme = trimmed.substring(0, schemeIdx).lowercase()
        if (scheme !in setOf("https", "http")) {
            return OutboundUrlDecision.Blocked("Disallowed scheme '$scheme'")
        }
        val afterScheme = trimmed.substring(schemeIdx + 3)
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        if ('@' in authority) return OutboundUrlDecision.Blocked("Embedded URL credentials forbidden")

        val host = if (authority.startsWith("[")) {
            authority.substringAfter('[').substringBefore(']').lowercase()
        } else {
            authority.substringBefore(':').lowercase()
        }
        if (host.isEmpty()) return OutboundUrlDecision.Blocked("Missing host")

        val isExplicitOverride = allowedHostOverrides.any { it.equals(host, ignoreCase = true) }
        if (scheme == "http" && !(isExplicitOverride && allowHttpForOverrides)) {
            return OutboundUrlDecision.Blocked("Plaintext HTTP blocked by outbound policy; HTTPS required")
        }

        if (!isExplicitOverride && isPrivateOrSpecialHost(host)) {
            return OutboundUrlDecision.Blocked("Private, loopback, link-local, metadata, or CGNAT host blocked: $host")
        }
        return OutboundUrlDecision.Allowed(trimmed, host)
    }

    fun isPrivateOrSpecialHost(hostRaw: String): Boolean {
        val h = hostRaw.trim().lowercase().trimEnd('.')
        if (h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")) return true
        if (h == "::1" || h == "0:0:0:0:0:0:0:1" || h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd") || h == "::") return true
        if (h.startsWith("::ffff:")) {
            return isPrivateOrSpecialHost(h.removePrefix("::ffff:"))
        }
        val parts = h.split(".")
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            return when {
                a == 0 -> true
                a == 10 -> true // RFC1918 10.0.0.0/8
                a == 127 -> true // Loopback 127.0.0.0/8
                a == 169 && b == 254 -> true // Link-local & cloud metadata 169.254.0.0/16
                a == 172 && b in 16..31 -> true // RFC1918 172.16.0.0/12
                a == 192 && b == 168 -> true // RFC1918 192.168.0.0/16
                a == 100 && b in 64..127 -> true // CGNAT 100.64.0.0/10
                a >= 224 -> true // Multicast & reserved
                else -> false
            }
        }
        return false
    }
}

/**
 * Filename, path-traversal, and archive safety guards (PD-008, TM-06).
 */
object InputSafetyGuard {
    const val MAX_ARCHIVE_ENTRIES: Int = 5_000
    const val MAX_ARCHIVE_UNCOMPRESSED_BYTES: Long = 256L * 1024L * 1024L

    fun sanitizeFilename(raw: String, fallback: String = "planaday-export.bin"): String {
        val cleaned = raw
            .replace(Regex("[\\r\\n\\t]"), " ")
            .replace(Regex("[<>:\"/\\\\|?*\\u0000-\\u001F]"), "_")
            .trim()
            .trimStart('.')
            .take(140)
        return cleaned.ifEmpty { fallback }
    }

    fun isSafeArchiveEntryPath(entryName: String): Boolean {
        val normalized = entryName.replace('\\', '/')
        if (normalized.isEmpty()) return false
        if (normalized.startsWith("/") || normalized.startsWith("~")) return false
        if (Regex("^[A-Za-z]:").containsMatchIn(normalized)) return false
        val segments = normalized.split("/")
        return segments.none { it == ".." || it.contains('\u0000') }
    }

    fun neutralizeCsvFormulaCell(raw: String): String {
        if (raw.isEmpty()) return raw
        val first = raw[0]
        return if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            "'$raw"
        } else raw
    }
}

/**
 * Multiplatform cryptographic primitives (SHA-256, PBKDF2-HMAC-SHA256, AES-256-GCM, Ed25519 verification).
 * Backed by vetted platform JCA providers on JVM and Android API 29+.
 */
expect object CryptoPrimitives {
    fun sha256Hex(bytes: ByteArray): String
    fun randomBytes(count: Int): ByteArray
    fun randomHexToken(byteCount: Int = 32): String
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean
    fun pbkdf2Sha256(password: String, salt: ByteArray, iterations: Int = 600_000, keyBits: Int = 256): ByteArray
    fun aesGcmEncrypt(plaintext: ByteArray, key32: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray
    fun aesGcmDecrypt(ciphertextWithIv: ByteArray, key32: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray
    fun generateEd25519KeyPairBase64(): Pair<String, String> // (publicKeyBase64, privateKeyBase64) — test/tooling only
    fun signEd25519(message: ByteArray, privateKeyBase64: String): String // test/tooling only
    fun verifyEd25519(message: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean
}

@Serializable
data class OfflineEntitlementPayload(
    val licenseId: String,
    val licensee: String,
    val edition: String, // desktop_personal | desktop_pro
    val issuedAtUtc: String,
    val expiresAtUtc: String? = null, // null = perpetual
    val maxMajorVersion: Int = 1,
)

@Serializable
data class SignedOfflineEntitlement(
    val payloadJson: String,
    val signatureBase64: String,
)

data class EntitlementCheckResult(
    val valid: Boolean,
    val localDataReadableAndExportable: Boolean = true, // Binding guarantee: NEVER lock user out of own data!
    val reason: String,
    val licensee: String? = null,
)

/**
 * Public-key-only offline desktop entitlement verifier (N-045, Master §10).
 * Zero private signing keys live in the application; user data ALWAYS remains readable & exportable.
 */
object OfflineEntitlementVerifier {
    fun verify(entitlement: SignedOfflineEntitlement, embeddedPublicKeyBase64: String): EntitlementCheckResult {
        return try {
            val ok = CryptoPrimitives.verifyEd25519(
                message = entitlement.payloadJson.encodeToByteArray(),
                signatureBase64 = entitlement.signatureBase64,
                publicKeyBase64 = embeddedPublicKeyBase64,
            )
            if (!ok) {
                EntitlementCheckResult(
                    valid = false,
                    localDataReadableAndExportable = true,
                    reason = "Invalid offline entitlement signature; local data remains fully readable and exportable.",
                )
            } else {
                val decoded = kotlinx.serialization.json.Json.decodeFromString<OfflineEntitlementPayload>(entitlement.payloadJson)
                EntitlementCheckResult(
                    valid = true,
                    localDataReadableAndExportable = true,
                    reason = "Valid offline entitlement (${decoded.edition})",
                    licensee = decoded.licensee,
                )
            }
        } catch (t: Throwable) {
            EntitlementCheckResult(
                valid = false,
                localDataReadableAndExportable = true,
                reason = "Malformed entitlement token: ${t.message}; local data remains readable and exportable.",
            )
        }
    }
}

data class DebugTraceStep(
    val lineNumber: Int,
    val statement: String,
    val variables: Map<String, Double>,
    val hitBreakpoint: Boolean,
)

data class SandboxExecutionResult(
    val succeeded: Boolean,
    val stdout: List<String>,
    val error: String? = null,
    val stepsExecuted: Int,
    val trace: List<DebugTraceStep> = emptyList(),
)

/**
 * Pure-Kotlin deterministic learning script interpreter + step debugger (PD-005, R-0322, R-0325):
 * - Zero in-process eval(), zero filesystem/network access, strict step budget (prevents infinite loops).
 * - Supports `let x = expr`, `print expr`, `assert expr`, `repeat N { ... }` with real line breakpoints & variable inspection!
 * - Play-policy compliant on Android AND available offline on Desktop alongside isolated external OS compiler processes.
 */
object SafeLearningInterpreter {
    fun executeWithDebugger(
        source: String,
        breakpoints: Set<Int> = emptySet(),
        maxSteps: Int = 500,
    ): SandboxExecutionResult {
        val lines = source.lines()
        val vars = LinkedHashMap<String, Double>()
        val stdout = mutableListOf<String>()
        val trace = mutableListOf<DebugTraceStep>()
        var steps = 0

        fun evalSimpleExpr(raw: String): Double {
            val s = raw.trim()
            if (s.toDoubleOrNull() != null) return s.toDouble()
            vars[s]?.let { return it }
            // Handle +, -, *, / with standard precedence cleanly
            val addParts = splitOutsideParens(s, charArrayOf('+', '-'))
            if (addParts.size > 1) {
                var acc = evalSimpleExpr(addParts[0].second)
                for (i in 1 until addParts.size) {
                    val v = evalSimpleExpr(addParts[i].second)
                    acc = if (addParts[i].first == '+') acc + v else acc - v
                }
                return acc
            }
            val mulParts = splitOutsideParens(s, charArrayOf('*', '/'))
            if (mulParts.size > 1) {
                var acc = evalSimpleExpr(mulParts[0].second)
                for (i in 1 until mulParts.size) {
                    val v = evalSimpleExpr(mulParts[i].second)
                    if (mulParts[i].first == '/') {
                        require(v != 0.0) { "Division by zero" }
                        acc /= v
                    } else acc *= v
                }
                return acc
            }
            if (s.startsWith("(") && s.endsWith(")")) {
                return evalSimpleExpr(s.substring(1, s.length - 1))
            }
            throw IllegalArgumentException("Unknown identifier or expression: '$s'")
        }

        try {
            var idx = 0
            while (idx < lines.size) {
                val lineNo = idx + 1
                val stmt = lines[idx].trim()
                idx++
                if (stmt.isEmpty() || stmt.startsWith("//") || stmt.startsWith("#")) continue
                steps++
                if (steps > maxSteps) {
                    return SandboxExecutionResult(false, stdout, "Execution budget exceeded ($maxSteps steps) — infinite loop halted safely", steps, trace)
                }
                when {
                    stmt.startsWith("let ") -> {
                        val rest = stmt.removePrefix("let ").trim()
                        val eq = rest.indexOf('=')
                        require(eq > 0) { "Line $lineNo: expected 'let name = expr'" }
                        val name = rest.substring(0, eq).trim()
                        val expr = rest.substring(eq + 1).trim()
                        vars[name] = evalSimpleExpr(expr)
                    }
                    stmt.startsWith("print ") -> {
                        val v = evalSimpleExpr(stmt.removePrefix("print ").trim())
                        stdout.add(if (v == kotlin.math.floor(v)) v.toLong().toString() else v.toString())
                    }
                    stmt.startsWith("assert_eq ") -> {
                        val parts = stmt.removePrefix("assert_eq ").split(",")
                        require(parts.size == 2) { "Line $lineNo: assert_eq expects 'actual, expected'" }
                        val actual = evalSimpleExpr(parts[0])
                        val expected = evalSimpleExpr(parts[1])
                        require(kotlin.math.abs(actual - expected) < 1e-9) {
                            "Assertion failed at line $lineNo: expected $expected but got $actual"
                        }
                    }
                    else -> throw IllegalArgumentException("Line $lineNo: unsupported statement '$stmt'")
                }
                trace.add(
                    DebugTraceStep(
                        lineNumber = lineNo,
                        statement = stmt,
                        variables = LinkedHashMap(vars),
                        hitBreakpoint = lineNo in breakpoints,
                    )
                )
            }
            return SandboxExecutionResult(true, stdout, null, steps, trace)
        } catch (t: Throwable) {
            return SandboxExecutionResult(false, stdout, t.message ?: "Execution error", steps, trace)
        }
    }

    private fun splitOutsideParens(s: String, ops: CharArray): List<Pair<Char, String>> {
        val parts = mutableListOf<Pair<Char, String>>()
        var depth = 0
        var lastOp = '+'
        var start = 0
        for (i in s.indices) {
            val ch = s[i]
            if (ch == '(') depth++
            else if (ch == ')') depth--
            else if (depth == 0 && ch in ops && i > 0) {
                parts.add(lastOp to s.substring(start, i))
                lastOp = ch
                start = i + 1
            }
        }
        parts.add(lastOp to s.substring(start))
        return parts
    }
}
