package com.planaday.nativeapp.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Hardware/TEE-backed AndroidKeyStore AES-256-GCM credential vault (API 29+, Update 2 §28 & §29).
 * Protects sync bearer tokens and sensitive credentials at rest without ever leaving plaintext keys on disk.
 */
class AndroidKeystoreCredentialVault(
    private val keystoreAlias: String = "planaday_native_cred_v1",
) : SecureCredentialVault {
    override val backendDescription: String = "AndroidKeyStore AES-256-GCM Vault"

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(keystoreAlias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            keystoreAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        gen.init(spec)
        return gen.generateKey()
    }

    override fun encryptSecret(keyAlias: String, plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD("PNDY_CRED_V1|$keyAlias".encodeToByteArray())
        val iv = cipher.iv
        val enc = cipher.doFinal(plaintext.encodeToByteArray())
        val combined = ByteArray(1 + iv.size + enc.size)
        combined[0] = iv.size.toByte()
        iv.copyInto(combined, 1)
        enc.copyInto(combined, 1 + iv.size)
        return Base64.getEncoder().encodeToString(combined)
    }

    override fun decryptSecret(keyAlias: String, ciphertextB64: String): String? = runCatching {
        val combined = Base64.getDecoder().decode(ciphertextB64)
        val ivLen = combined[0].toInt() and 0xFF
        val iv = combined.copyOfRange(1, 1 + ivLen)
        val enc = combined.copyOfRange(1 + ivLen, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        cipher.updateAAD("PNDY_CRED_V1|$keyAlias".encodeToByteArray())
        cipher.doFinal(enc).decodeToString()
    }.getOrNull()
}
