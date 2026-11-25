package com.planaday.nativeapp.core.security

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class CoreSecurityContractTest {
    @Test
    fun pd014SsrfOutboundPolicyBlocksPrivateLoopbackMetadataAndPlainHttp() {
        val blocked = listOf(
            "http://example.com/feed.ics",
            "https://127.0.0.1/secret",
            "https://localhost:8080/api",
            "https://169.254.169.254/latest/meta-data/",
            "https://10.0.0.1/internal",
            "https://172.16.0.5/internal",
            "https://192.168.1.10/admin",
            "https://100.64.0.1/cgnat",
            "file:///etc/passwd",
            "https://user:pass@example.com/",
        )
        for (url in blocked) {
            assertTrue(OutboundUrlPolicy.validateUrl(url) is OutboundUrlDecision.Blocked, "Should block: $url")
        }
        assertTrue(OutboundUrlPolicy.validateUrl("https://calendar.example.org/public.ics") is OutboundUrlDecision.Allowed)
        assertTrue(
            OutboundUrlPolicy.validateUrl(
                "http://127.0.0.1:8420/feed.ics",
                allowedHostOverrides = setOf("127.0.0.1"),
                allowHttpForOverrides = true,
            ) is OutboundUrlDecision.Allowed
        )
    }

    @Test
    fun cryptoAesGcmEd25519EntitlementAndDataSurvival() {
        val hash = CryptoPrimitives.sha256Hex("planaday-native".encodeToByteArray())
        assertEquals(64, hash.length)

        val key = CryptoPrimitives.pbkdf2Sha256("correct-horse", CryptoPrimitives.randomBytes(16), iterations = 10_000)
        val encrypted = CryptoPrimitives.aesGcmEncrypt("sensitive lab record".encodeToByteArray(), key, "aad".encodeToByteArray())
        assertEquals("sensitive lab record", CryptoPrimitives.aesGcmDecrypt(encrypted, key, "aad".encodeToByteArray()).decodeToString())
        assertFails { CryptoPrimitives.aesGcmDecrypt(encrypted, CryptoPrimitives.randomBytes(32), "aad".encodeToByteArray()) }

        // N-045: Offline Ed25519 entitlement + data always readable on invalid signature
        val (pub, priv) = CryptoPrimitives.generateEd25519KeyPairBase64()
        val payloadJson = Json.encodeToString(
            OfflineEntitlementPayload("lic-1", "Test Owner", "desktop_pro", "2026-11-25T00:00:00Z")
        )
        val sig = CryptoPrimitives.signEd25519(payloadJson.encodeToByteArray(), priv)
        val valid = OfflineEntitlementVerifier.verify(SignedOfflineEntitlement(payloadJson, sig), pub)
        assertTrue(valid.valid)
        assertTrue(valid.localDataReadableAndExportable)

        val tampered = OfflineEntitlementVerifier.verify(SignedOfflineEntitlement(payloadJson.replace("Test Owner", "Spoof"), sig), pub)
        assertFalse(tampered.valid)
        assertTrue(tampered.localDataReadableAndExportable, "Local user data MUST remain readable even on invalid license")
    }

    @Test
    fun pd005AndR0325SafeLearningInterpreterAndDebuggerBreakpoints() {
        val code = """
            let a = 10
            let b = a * 2 + 5
            print b
            assert_eq b, 25
        """.trimIndent()
        val res = SafeLearningInterpreter.executeWithDebugger(code, breakpoints = setOf(2))
        assertTrue(res.succeeded, "Error: ${res.error}")
        assertEquals(listOf("25"), res.stdout)
        val bpStep = res.trace.first { it.lineNumber == 2 }
        assertTrue(bpStep.hitBreakpoint)
        assertEquals(25.0, bpStep.variables["b"])
    }
}
