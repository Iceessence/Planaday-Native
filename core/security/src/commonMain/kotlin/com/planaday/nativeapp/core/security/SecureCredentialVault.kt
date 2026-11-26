package com.planaday.nativeapp.core.security

/**
 * OS-protected persistent credential vault (Update 2 §28 & §29).
 * Encrypts sensitive credentials (sync bearer tokens, pairing secrets) using platform-managed
 * key protection before storing in local SQLite `app_preferences`.
 * Never stores plaintext bearer tokens in SQLite or leaves tokens only in volatile memory.
 */
interface SecureCredentialVault {
    val backendDescription: String
    fun encryptSecret(keyAlias: String, plaintext: String): String
    fun decryptSecret(keyAlias: String, ciphertextB64: String): String?
}

sealed interface TransportValidationResult {
    data class Valid(val normalizedUrl: String, val isLocalDevHttp: Boolean) : TransportValidationResult
    data class Rejected(val reason: String) : TransportValidationResult
}

/**
 * Enforces HTTPS transport for all non-local production sync connections (Update 2 §28).
 * Plaintext HTTP is permitted ONLY for loopback (`127.0.0.1`, `localhost`, `[::1]`) or when
 * explicitly enabled as development-only local HTTP.
 */
object SyncTransportSecurityPolicy {
    fun validateServerUrl(rawUrl: String, allowDevelopmentHttp: Boolean = false): TransportValidationResult {
        val trimmed = rawUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return TransportValidationResult.Rejected("Server URL cannot be empty.")
        if (trimmed.startsWith("https://", ignoreCase = true)) {
            return TransportValidationResult.Valid(trimmed, isLocalDevHttp = false)
        }
        if (trimmed.startsWith("http://", ignoreCase = true)) {
            val hostPart = trimmed.substringAfter("://").substringBefore('/').substringBefore('?').let { auth ->
                if (auth.startsWith("[")) auth.substringAfter('[').substringBefore(']') else auth.substringBefore(':')
            }.lowercase()
            val isLoopback = hostPart == "localhost" || hostPart == "127.0.0.1" || hostPart == "::1"
            if (isLoopback || allowDevelopmentHttp) {
                return TransportValidationResult.Valid(trimmed, isLocalDevHttp = true)
            }
            return TransportValidationResult.Rejected(
                "Non-local sync server '$hostPart' requires HTTPS (TLS). Enable 'Development HTTP' only on trusted local networks."
            )
        }
        return TransportValidationResult.Rejected("Sync server URL must begin with https:// (or http:// for local development).")
    }
}
