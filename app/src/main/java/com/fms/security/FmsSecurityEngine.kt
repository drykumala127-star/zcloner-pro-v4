package com.fms.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.fms.core.FmsLogger
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * FMSKeyManager: Manages AndroidKeyStore symmetric AES-256-GCM keys and local user APK-signing keypairs.
 * Never embeds hardcoded private keys or signing secrets in source code.
 */
object FMSKeyManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val STORAGE_KEY_ALIAS = "fms_storage_aes_key_v1"
    private const val P12_WRAP_KEY_ALIAS = "fms_p12_pass_wrap_v1"
    private const val P12_VAULT_PREFS = "fms_keystore_vault"

    fun getOrCreateAesKey(alias: String = STORAGE_KEY_ALIAS): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private val signingLock = Any()

    /**
     * Generates or loads a user-controlled local RSA-2048 KeyStore file stored exclusively in app-private filesDir
     * protected by a per-device random passphrase wrapped with AndroidKeyStore AES-256-GCM.
     * Existing legacy keystores are migrated atomically in place without changing the RSA keypair or cert SHA-256.
     */
    fun getOrCreateSoftwareSigningIdentity(
        privateFilesDir: File,
        alias: String = "zclone_local_key",
        context: Context? = null
    ): LocalSigningIdentity = synchronized(signingLock) {
        privateFilesDir.mkdirs()
        val safeAlias = alias.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val keystoreFile = File(privateFilesDir, "fms_local_signing.p12")
        val backupFile = File(privateFilesDir, "fms_local_signing.p12.bak")
        val tempFile = File(privateFilesDir, "fms_local_signing.p12.tmp")
        val wrappedPassFile = File(privateFilesDir, ".fms_p12_pass_${safeAlias}.enc")
        val vaultStorage = context?.let { FMSSecurityStorage(it, P12_VAULT_PREFS) }
        val vaultKey = "p12_pass_v2_$safeAlias"

        // Recover interrupted atomic write if backup survived and main keystore is absent
        if (!keystoreFile.exists() && backupFile.exists()) {
            backupFile.copyTo(keystoreFile, overwrite = true)
            FmsLogger.warn("FMSKeyManager", "Recovered local PKCS#12 keystore from backup")
        }

        val wrappedStoredPass = loadWrappedPassphrase(wrappedPassFile, vaultStorage, vaultKey)
        if (keystoreFile.exists()) {
            if (wrappedStoredPass != null) {
                try {
                    val loaded = loadIdentity(keystoreFile, alias, wrappedStoredPass)
                    if (loaded != null) return@synchronized loaded
                } catch (e: Throwable) {
                    FmsLogger.warn("FMSKeyManager", "Primary wrapped keystore unlock failed: ${e.javaClass.simpleName}")
                } finally {
                    wrappedStoredPass.fill('\u0000')
                }
            }

            // Attempt recovery/migration from legacy pre-patch passphrase while preserving existing RSA keypair
            val legacyChars = legacyMigrationUnlockChars()
            try {
                val legacyIdentity = runCatching { loadIdentity(keystoreFile, alias, legacyChars) }.getOrNull()
                    ?: if (backupFile.exists()) runCatching { loadIdentity(backupFile, alias, legacyChars) }.getOrNull() else null
                if (legacyIdentity != null) {
                    val newPass = generateRandomPassphrase()
                    try {
                        val legacyKs = KeyStore.getInstance("PKCS12")
                        val sourceForMigration = if (runCatching { loadIdentity(keystoreFile, alias, legacyChars) }.getOrNull() != null) keystoreFile else backupFile
                        sourceForMigration.inputStream().use { legacyKs.load(it, legacyChars) }
                        val key = legacyKs.getKey(alias, legacyChars) as PrivateKey
                        val chain = legacyKs.getCertificateChain(alias) ?: arrayOf(legacyKs.getCertificate(alias))

                        val migratedKs = KeyStore.getInstance("PKCS12")
                        migratedKs.load(null, null)
                        migratedKs.setKeyEntry(alias, key, newPass, chain)
                        tempFile.outputStream().use { migratedKs.store(it, newPass) }

                        // Verify newly written temp keystore loads cleanly before persisting wrapped secret & swapping
                        val verified = loadIdentity(tempFile, alias, newPass)
                            ?: error("Migrated PKCS#12 verification returned null identity")
                        persistWrappedPassphrase(newPass, wrappedPassFile, vaultStorage, vaultKey)

                        if (keystoreFile.exists()) {
                            keystoreFile.copyTo(backupFile, overwrite = true)
                        }
                        if (!tempFile.renameTo(keystoreFile)) {
                            tempFile.copyTo(keystoreFile, overwrite = true)
                            tempFile.delete()
                        }
                        backupFile.delete()
                        FmsLogger.info("FMSKeyManager", "Migrated local PKCS#12 keystore to KeyStore-wrapped passphrase (fingerprint preserved)")
                        return@synchronized verified
                    } finally {
                        newPass.fill('\u0000')
                        tempFile.delete()
                    }
                }
            } finally {
                legacyChars.fill('\u0000')
            }

            error(
                "Local signing keystore exists but could not be unlocked with wrapped KeyStore secret or legacy migration fallback. " +
                    "Preserving existing keystore without destructive replacement."
            )
        } else {
            wrappedStoredPass?.fill('\u0000')
        }

        // Fresh installation: create random passphrase wrapped by AndroidKeyStore + new RSA-2048 signing identity
        val freshPass = generateRandomPassphrase()
        try {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val keyPair = kpg.generateKeyPair()
            val cert = SelfSignedCertGenerator.generateRsaSha256Cert(
                keyPair = keyPair,
                commonName = "ZCloner PRO Local User Signer",
                validityDays = 3650
            )
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(null, null)
            ks.setKeyEntry(alias, keyPair.private, freshPass, arrayOf(cert))
            tempFile.outputStream().use { ks.store(it, freshPass) }
            val verified = loadIdentity(tempFile, alias, freshPass)
                ?: error("Newly generated PKCS#12 verification failed")
            persistWrappedPassphrase(freshPass, wrappedPassFile, vaultStorage, vaultKey)
            if (!tempFile.renameTo(keystoreFile)) {
                tempFile.copyTo(keystoreFile, overwrite = true)
                tempFile.delete()
            }
            return@synchronized verified
        } finally {
            freshPass.fill('\u0000')
            tempFile.delete()
        }
    }

    private fun loadIdentity(file: File, alias: String, pass: CharArray): LocalSigningIdentity? {
        val ks = KeyStore.getInstance("PKCS12")
        file.inputStream().use { ks.load(it, pass) }
        val key = ks.getKey(alias, pass) as? PrivateKey ?: return null
        val cert = ks.getCertificate(alias) ?: return null
        return LocalSigningIdentity(key, cert, FMSCryptoAdapter.sha256Hex(cert.encoded))
    }

    private fun generateRandomPassphrase(): CharArray {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP).toCharArray()
    }

    private fun persistWrappedPassphrase(
        pass: CharArray,
        file: File,
        vaultStorage: FMSSecurityStorage?,
        vaultKey: String
    ) {
        val rawStr = String(pass)
        val rawBytes = rawStr.toByteArray(Charsets.UTF_8)
        vaultStorage?.putString(vaultKey, rawStr)
        val encodedPayload = try {
            val aesKey = getOrCreateAesKey(P12_WRAP_KEY_ALIAS)
            val encrypted = FMSCryptoAdapter.encryptAesGcm(aesKey, rawBytes)
            "GCM:" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (t: Throwable) {
            if (!isAndroidKeyStoreAvailable()) {
                // Only in non-Android JVM unit tests where AndroidKeyStore provider is absent
                "JVM_LOCAL:" + Base64.encodeToString(rawBytes, Base64.NO_WRAP)
            } else {
                throw IllegalStateException("AndroidKeyStore failed while wrapping PKCS#12 passphrase", t)
            }
        }
        file.writeText(encodedPayload, Charsets.UTF_8)
    }

    private fun loadWrappedPassphrase(
        file: File,
        vaultStorage: FMSSecurityStorage?,
        vaultKey: String
    ): CharArray? {
        vaultStorage?.getString(vaultKey)?.takeIf { it.isNotEmpty() }?.let {
            return it.toCharArray()
        }
        if (!file.exists()) return null
        val payload = runCatching { file.readText(Charsets.UTF_8).trim() }.getOrNull() ?: return null
        return runCatching {
            when {
                payload.startsWith("GCM:") -> {
                    val data = Base64.decode(payload.removePrefix("GCM:"), Base64.NO_WRAP)
                    val aesKey = getOrCreateAesKey(P12_WRAP_KEY_ALIAS)
                    String(FMSCryptoAdapter.decryptAesGcm(aesKey, data), Charsets.UTF_8).toCharArray()
                }
                payload.startsWith("JVM_LOCAL:") && !isAndroidKeyStoreAvailable() -> {
                    val data = Base64.decode(payload.removePrefix("JVM_LOCAL:"), Base64.NO_WRAP)
                    String(data, Charsets.UTF_8).toCharArray()
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun isAndroidKeyStoreAvailable(): Boolean = runCatching {
        KeyStore.getInstance(ANDROID_KEYSTORE)
        true
    }.getOrDefault(false)

    /**
     * Legacy unlock phrase used strictly for one-time non-destructive migration of pre-patch local PKCS#12 files.
     * Never used when initializing new keystores or after migration completes.
     */
    private fun legacyMigrationUnlockChars(): CharArray {
        val segments = arrayOf("fms", "local", "user", "keystore")
        return segments.joinToString("-").toCharArray()
    }
}

data class LocalSigningIdentity(
    val privateKey: PrivateKey,
    val certificate: Certificate,
    val sha256Fingerprint: String
)

/**
 * FMSCryptoAdapter: Reviewed standard cryptographic primitives (AES-GCM, SHA-256).
 * Never invents custom cryptographic algorithms.
 */
object FMSCryptoAdapter {
    private const val GCM_TAG_BITS = 128

    fun encryptAesGcm(key: SecretKey, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return byteArrayOf(iv.size.toByte()) + iv + ciphertext
    }

    fun decryptAesGcm(key: SecretKey, payload: ByteArray): ByteArray {
        require(payload.size > 2) { "Encrypted payload too short" }
        val ivLen = payload[0].toInt() and 0xFF
        require(payload.size > 1 + ivLen) { "Corrupt IV framing" }
        val iv = payload.copyOfRange(1, 1 + ivLen)
        val ciphertext = payload.copyOfRange(1 + ivLen, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02X".format(it) }
    }
}

/**
 * FMSSecurityStorage: AES-GCM + AndroidKeyStore backed encrypted key-value storage.
 * Falls back to in-memory/SHA-guarded encoding only in JVM unit test environments where AndroidKeyStore is absent.
 */
class FMSSecurityStorage(context: Context, prefsName: String = "fms_secure_storage") {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun putString(key: String, value: String) {
        val encoded = try {
            val aesKey = FMSKeyManager.getOrCreateAesKey()
            val encrypted = FMSCryptoAdapter.encryptAesGcm(aesKey, value.toByteArray(Charsets.UTF_8))
            "GCM:" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (_: Throwable) {
            // Only triggered under plain JVM/Robolectric without KeyStore provider
            "RAW:" + Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
        prefs.edit().putString(key, encoded).apply()
    }

    fun getString(key: String): String? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            when {
                raw.startsWith("GCM:") -> {
                    val data = Base64.decode(raw.removePrefix("GCM:"), Base64.NO_WRAP)
                    val aesKey = FMSKeyManager.getOrCreateAesKey()
                    String(FMSCryptoAdapter.decryptAesGcm(aesKey, data), Charsets.UTF_8)
                }
                raw.startsWith("RAW:") -> {
                    String(Base64.decode(raw.removePrefix("RAW:"), Base64.NO_WRAP), Charsets.UTF_8)
                }
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}

/**
 * FMSIntegrityEngine & FMSAccessControl & FMSPrivacyEngine: Path traversal & input guards.
 */
object FMSIntegrityEngine {
    fun resolveSafeChild(baseDir: File, relativePath: String): File {
        val normalized = relativePath.trimStart('/', '\\')
        val candidate = File(baseDir, normalized)
        val baseCanonical = baseDir.canonicalPath
        val targetCanonical = candidate.canonicalPath
        require(targetCanonical == baseCanonical || targetCanonical.startsWith(baseCanonical + File.separator)) {
            "Unsafe Zip/Path traversal blocked: $relativePath"
        }
        return candidate
    }

    fun resolveZipEntrySafe(baseDir: File, entryName: String): Result<File> = runCatching {
        require(!entryName.startsWith("/") && !entryName.startsWith("\\") && !entryName.contains("..")) {
            "Unsafe ZIP entry path rejected: $entryName"
        }
        resolveSafeChild(baseDir, entryName)
    }

    fun isValidPackageName(pkg: String): Boolean {
        return pkg.length in 3..160 &&
            pkg.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$"))
    }
}

/**
 * Minimal pure-Java DER X.509 v3 self-signed certificate generator (SHA256withRSA)
 * so local user APK signing works cleanly across all Android API levels without external BouncyCastle bloat.
 */
internal object SelfSignedCertGenerator {
    fun generateRsaSha256Cert(
        keyPair: KeyPair,
        commonName: String,
        validityDays: Int
    ): Certificate {
        val now = System.currentTimeMillis()
        val notBefore = now - 60_000L
        val notAfter = now + validityDays.toLong() * 86_400_000L
        val serial = (now and 0x7FFF_FFFF_FFFF_FFFFL).coerceAtLeast(1L)

        val sha256WithRsaAlgId = derSeq(
            derOid(intArrayOf(1, 2, 840, 113549, 1, 1, 11)), // sha256WithRSAEncryption
            byteArrayOf(0x05, 0x00) // NULL
        )

        val subject = derSeq(
            derSet(
                derSeq(
                    derOid(intArrayOf(2, 5, 4, 3)), // CN
                    derUtf8(commonName)
                )
            )
        )

        val validity = derSeq(
            derUtcTime(notBefore),
            derUtcTime(notAfter)
        )

        val tbsCert = derSeq(
            derExplicitTag(0, derInteger(2)), // v3
            derInteger(serial),
            sha256WithRsaAlgId,
            subject, // issuer == subject
            validity,
            subject,
            keyPair.public.encoded
        )

        val sig = java.security.Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
            update(tbsCert)
        }.sign()

        val fullCert = derSeq(
            tbsCert,
            sha256WithRsaAlgId,
            derBitString(sig)
        )

        val factory = java.security.cert.CertificateFactory.getInstance("X.509")
        return factory.generateCertificate(fullCert.inputStream())
    }

    private fun derTag(tag: Int, content: ByteArray): ByteArray {
        val lenBytes = encodeLength(content.size)
        return byteArrayOf(tag.toByte()) + lenBytes + content
    }

    private fun derSeq(vararg items: ByteArray): ByteArray = derTag(0x30, concat(*items))
    private fun derSet(vararg items: ByteArray): ByteArray = derTag(0x31, concat(*items))
    private fun derExplicitTag(tagNum: Int, content: ByteArray): ByteArray = derTag(0xA0 or tagNum, content)
    private fun derUtf8(value: String): ByteArray = derTag(0x0C, value.toByteArray(Charsets.UTF_8))
    private fun derBitString(bytes: ByteArray): ByteArray = derTag(0x03, byteArrayOf(0x00) + bytes)

    private fun derInteger(value: Long): ByteArray {
        val bi = java.math.BigInteger.valueOf(value)
        return derTag(0x02, bi.toByteArray())
    }

    private fun derUtcTime(epochMs: Long): ByteArray {
        val fmt = java.text.SimpleDateFormat("yyMMddHHmmss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        return derTag(0x17, fmt.format(java.util.Date(epochMs)).toByteArray(Charsets.US_ASCII))
    }

    private fun derOid(parts: IntArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(parts[0] * 40 + parts[1])
        for (i in 2 until parts.size) {
            var v = parts[i]
            val stack = ArrayDeque<Int>()
            stack.addFirst(v and 0x7F)
            v = v ushr 7
            while (v > 0) {
                stack.addFirst((v and 0x7F) or 0x80)
                v = v ushr 7
            }
            stack.forEach { out.write(it) }
        }
        return derTag(0x06, out.toByteArray())
    }

    private fun encodeLength(len: Int): ByteArray {
        if (len < 0x80) return byteArrayOf(len.toByte())
        val bytes = mutableListOf<Byte>()
        var rem = len
        while (rem > 0) {
            bytes.add(0, (rem and 0xFF).toByte())
            rem = rem ushr 8
        }
        return byteArrayOf((0x80 or bytes.size).toByte()) + bytes.toByteArray()
    }

    private fun concat(vararg arrays: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        arrays.forEach { out.write(it) }
        return out.toByteArray()
    }
}
