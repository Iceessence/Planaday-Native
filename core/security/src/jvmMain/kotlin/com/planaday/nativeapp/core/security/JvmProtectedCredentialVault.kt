package com.planaday.nativeapp.core.security

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.Base64

/**
 * Windows / JVM Desktop OS-protected credential vault (Update 2 §28 & §29).
 * Stores a per-user 256-bit AES-GCM master key in an owner-only ACL file (Windows NTFS ACL restricting
 * access solely to the current OS user principal, or POSIX 0600 on Unix) and encrypts every secret with
 * authenticated AAD binding (`PNDY_CRED_V1|<keyAlias>`) before persistence in SQLite.
 */
class JvmProtectedCredentialVault(private val vaultDir: File) : SecureCredentialVault {
    override val backendDescription: String = "Windows/JVM User-ACL AES-256-GCM Credential Vault"

    private val keyFile: File = File(vaultDir, "planaday_native_vault.key")

    @Synchronized
    private fun loadOrCreateMasterKey(): ByteArray {
        vaultDir.mkdirs()
        if (keyFile.exists()) {
            val raw = Base64.getDecoder().decode(keyFile.readText(Charsets.UTF_8).trim())
            if (raw.size == 32) return raw
        }
        val key = CryptoPrimitives.randomBytes(32)
        keyFile.writeText(Base64.getEncoder().encodeToString(key), Charsets.UTF_8)
        restrictFileToOwnerOnly(keyFile)
        return key
    }

    private fun restrictFileToOwnerOnly(file: File) {
        val path = file.toPath()
        runCatching {
            val aclView = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
            if (aclView != null) {
                val owner = Files.getOwner(path)
                val entry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(
                        AclEntryPermission.READ_DATA,
                        AclEntryPermission.WRITE_DATA,
                        AclEntryPermission.APPEND_DATA,
                        AclEntryPermission.READ_ATTRIBUTES,
                        AclEntryPermission.WRITE_ATTRIBUTES,
                        AclEntryPermission.READ_NAMED_ATTRS,
                        AclEntryPermission.WRITE_NAMED_ATTRS,
                        AclEntryPermission.READ_ACL,
                        AclEntryPermission.WRITE_ACL,
                        AclEntryPermission.DELETE,
                        AclEntryPermission.SYNCHRONIZE,
                    )
                    .build()
                aclView.acl = listOf(entry)
                return
            }
        }
        runCatching {
            Files.setPosixFilePermissions(path, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        }
    }

    override fun encryptSecret(keyAlias: String, plaintext: String): String {
        val key = loadOrCreateMasterKey()
        val aad = "PNDY_CRED_V1|$keyAlias".encodeToByteArray()
        val cipher = CryptoPrimitives.aesGcmEncrypt(plaintext.encodeToByteArray(), key, aad = aad)
        return Base64.getEncoder().encodeToString(cipher)
    }

    override fun decryptSecret(keyAlias: String, ciphertextB64: String): String? = runCatching {
        val key = loadOrCreateMasterKey()
        val aad = "PNDY_CRED_V1|$keyAlias".encodeToByteArray()
        val bytes = Base64.getDecoder().decode(ciphertextB64)
        CryptoPrimitives.aesGcmDecrypt(bytes, key, aad = aad).decodeToString()
    }.getOrNull()
}
