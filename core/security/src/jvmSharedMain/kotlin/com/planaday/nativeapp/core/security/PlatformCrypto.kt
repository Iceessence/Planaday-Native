package com.planaday.nativeapp.core.security

import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

actual object CryptoPrimitives {
    private val secureRandom = SecureRandom()

    actual fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = CharArray(digest.size * 2)
        val alphabet = "0123456789abcdef"
        for (i in digest.indices) {
            val v = digest[i].toInt() and 0xFF
            hex[i * 2] = alphabet[v ushr 4]
            hex[i * 2 + 1] = alphabet[v and 0x0F]
        }
        return String(hex)
    }

    actual fun randomBytes(count: Int): ByteArray {
        val b = ByteArray(count)
        secureRandom.nextBytes(b)
        return b
    }

    actual fun randomHexToken(byteCount: Int): String = sha256Hex(randomBytes(byteCount))

    actual fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    actual fun pbkdf2Sha256(password: String, salt: ByteArray, iterations: Int, keyBits: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyBits)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    actual fun aesGcmEncrypt(plaintext: ByteArray, key32: ByteArray, aad: ByteArray): ByteArray {
        require(key32.size == 32) { "AES-256 requires 32-byte key" }
        val iv = randomBytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key32, "AES"), GCMParameterSpec(128, iv))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    actual fun aesGcmDecrypt(ciphertextWithIv: ByteArray, key32: ByteArray, aad: ByteArray): ByteArray {
        require(key32.size == 32) { "AES-256 requires 32-byte key" }
        require(ciphertextWithIv.size > 12 + 16) { "Ciphertext too short" }
        val iv = ciphertextWithIv.copyOfRange(0, 12)
        val body = ciphertextWithIv.copyOfRange(12, ciphertextWithIv.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key32, "AES"), GCMParameterSpec(128, iv))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(body)
    }

    private fun signingAlgorithm(): Pair<String, String> = try {
        KeyPairGenerator.getInstance("Ed25519")
        "Ed25519" to "Ed25519"
    } catch (_: NoSuchAlgorithmException) {
        "EC" to "SHA256withECDSA"
    }

    actual fun generateEd25519KeyPairBase64(): Pair<String, String> {
        val (keyAlg, _) = signingAlgorithm()
        val kpg = KeyPairGenerator.getInstance(keyAlg)
        if (keyAlg == "EC") kpg.initialize(256, secureRandom)
        val pair = kpg.generateKeyPair()
        val enc = Base64.getEncoder()
        return enc.encodeToString(pair.public.encoded) to enc.encodeToString(pair.private.encoded)
    }

    actual fun signEd25519(message: ByteArray, privateKeyBase64: String): String {
        val (keyAlg, sigAlg) = signingAlgorithm()
        val privBytes = Base64.getDecoder().decode(privateKeyBase64)
        val privKey = KeyFactory.getInstance(keyAlg).generatePrivate(PKCS8EncodedKeySpec(privBytes))
        val sig = Signature.getInstance(sigAlg)
        sig.initSign(privKey)
        sig.update(message)
        return Base64.getEncoder().encodeToString(sig.sign())
    }

    actual fun verifyEd25519(message: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean {
        val (keyAlg, sigAlg) = signingAlgorithm()
        val pubBytes = Base64.getDecoder().decode(publicKeyBase64)
        val sigBytes = Base64.getDecoder().decode(signatureBase64)
        val pubKey = KeyFactory.getInstance(keyAlg).generatePublic(X509EncodedKeySpec(pubBytes))
        val sig = Signature.getInstance(sigAlg)
        sig.initVerify(pubKey)
        sig.update(message)
        return sig.verify(sigBytes)
    }
}
