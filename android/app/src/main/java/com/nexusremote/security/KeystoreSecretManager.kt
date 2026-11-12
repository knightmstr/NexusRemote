package com.nexusremote.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.Certificate
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed Android Keystore Credential Manager hardened for OWASP MASVS compliance.
 * - Uses per-device AES-256-GCM keys for true cryptographic erasure via keyStore.deleteEntry(alias).
 * - Binds storage keys as Additional Authenticated Data (AAD) via cipher.updateAAD() to prevent ciphertext substitution.
 * - Enforces setUnlockedDeviceRequired(true) on API 28+ and attempts StrongBox backed key generation with TEE fallback.
 * - Formats ADB RSA public key into native 524-byte RSAPublicKey struct and signs 20-byte tokens using NONEwithRSA
 *   with pre-built SHA-1 ASN.1 DigestInfo header.
 */
class KeystoreSecretManager(private val context: Context) {

    companion object {
        private const val TAG = "KeystoreSecretManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEVICE_KEY_PREFIX = "nexus_key_"
        private const val ADB_RSA_KEY_ALIAS = "nexus_remote_adb_rsa_key"
        private const val PREFS_NAME = "nexus_remote_secure_vault"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12

        // SHA-1 ASN.1 DigestInfo header (15 bytes) required for NONEwithRSA PKCS#1 v1.5 signing of 20-byte ADB challenge
        private val SHA1_DIGEST_INFO_PREFIX = byteArrayOf(
            0x30.toByte(), 0x21.toByte(), 0x30.toByte(), 0x09.toByte(),
            0x06.toByte(), 0x05.toByte(), 0x2b.toByte(), 0x0e.toByte(),
            0x03.toByte(), 0x02.toByte(), 0x1a.toByte(), 0x05.toByte(),
            0x00.toByte(), 0x04.toByte(), 0x14.toByte()
        )
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private val encryptedPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        ensureAdbRsaKeyExists()
    }

    /**
     * Retrieves or generates a per-device AES-256-GCM key in the Android Keystore.
     */
    private fun getOrCreateDeviceAesKey(deviceId: String): SecretKey {
        val alias = "${DEVICE_KEY_PREFIX}${deviceId}"
        if (keyStore.containsAlias(alias)) {
            val key = keyStore.getKey(alias, null) as? SecretKey
            if (key != null) return key
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(true)
        }

        // Attempt StrongBox hardware security module first, fallback gracefully to standard TEE if unavailable
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val strongBoxSpec = builder.setIsStrongBoxBacked(true).build()
                keyGenerator.init(strongBoxSpec)
                val key = keyGenerator.generateKey()
                Log.i(TAG, "Generated StrongBox-backed AES-256 key for device '$deviceId'")
                return key
            } catch (e: Exception) {
                Log.w(TAG, "StrongBox unavailable for $alias, falling back to standard TEE: ${e.message}")
            }
        }

        val standardSpec = builder.setIsStrongBoxBacked(false).build()
        keyGenerator.init(standardSpec)
        val key = keyGenerator.generateKey()
        Log.i(TAG, "Generated TEE-backed AES-256 key for device '$deviceId'")
        return key
    }

    /**
     * Stores an encrypted device authentication secret (webOS client-key, Tizen token, Sony PSK, TLS cert fingerprint).
     * Binds "${deviceId}::${secretKeyName}" as Additional Authenticated Data (AAD) to prevent cross-device ciphertext substitution.
     */
    fun storeSecret(deviceId: String, secretKeyName: String, plainSecret: String) {
        try {
            val secretKey = getOrCreateDeviceAesKey(deviceId)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            // Bind storage key as Additional Authenticated Data (AAD)
            val aadLabel = "${deviceId}::${secretKeyName}"
            cipher.updateAAD(aadLabel.toByteArray(Charsets.UTF_8))

            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainSecret.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            val base64Payload = Base64.encodeToString(combined, Base64.NO_WRAP)
            val storageKey = "${deviceId}_${secretKeyName}"
            encryptedPrefs.edit().putString(storageKey, base64Payload).apply()
            Log.d(TAG, "Securely encrypted secret for device '$deviceId' ($secretKeyName) with per-device AES key and AAD")
        } catch (e: Exception) {
            Log.e(TAG, "Failed storing secret for device $deviceId: ${e.message}", e)
        }
    }

    /**
     * Retrieves and decrypts a device authentication secret using per-device AES key and AAD verification.
     */
    fun getSecret(deviceId: String, secretKeyName: String): String? {
        val storageKey = "${deviceId}_${secretKeyName}"
        val base64Payload = encryptedPrefs.getString(storageKey, null) ?: return null
        return try {
            val combined = Base64.decode(base64Payload, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH) return null

            val iv = ByteArray(GCM_IV_LENGTH)
            val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
            System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

            val secretKey = getOrCreateDeviceAesKey(deviceId)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            // Verify Additional Authenticated Data (AAD) label
            val aadLabel = "${deviceId}::${secretKeyName}"
            cipher.updateAAD(aadLabel.toByteArray(Charsets.UTF_8))

            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed decrypting secret for device $deviceId (AAD or key mismatch): ${e.message}", e)
            null
        }
    }

    /**
     * Cryptographically erases credentials for a deleted device by deleting its unique AES key from Android Keystore.
     */
    fun removeDeviceSecrets(deviceId: String) {
        // 1. Remove encrypted payloads from SharedPreferences
        val editor = encryptedPrefs.edit()
        encryptedPrefs.all.keys.filter { it.startsWith("${deviceId}_") }.forEach { key ->
            editor.remove(key)
        }
        editor.apply()

        // 2. Cryptographic Erasure: Delete per-device AES key from Android Keystore
        val alias = "${DEVICE_KEY_PREFIX}${deviceId}"
        try {
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
                Log.i(TAG, "Cryptographically erased Android Keystore AES key '$alias' for device '$deviceId'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleting Keystore entry $alias: ${e.message}", e)
        }
    }

    /**
     * Retrieves the device's RSA public key formatted into the native 524-byte ADB RSAPublicKey struct format.
     */
    fun getAdbPublicKeyBytes(): ByteArray? {
        val structBytes = getAdbPublicKeyStructBytes() ?: return null
        val base64KeyStr = Base64.encodeToString(structBytes, Base64.NO_WRAP) + " nexus@nexusremote\u0000"
        return base64KeyStr.toByteArray(Charsets.UTF_8)
    }

    /**
     * Formats the Keystore RSA public key into native 524-byte ADB RSAPublicKey C-struct format:
     * struct RSAPublicKey {
     *     int len;                 /* 4 bytes: RSANUMWORDS = 64 (2048-bit / 32) */
     *     uint32_t n0inv;          /* 4 bytes: -N[0]^-1 mod 2^32 */
     *     uint32_t n[64];          /* 256 bytes: Modulus N in little-endian 32-bit words */
     *     uint32_t rr[64];         /* 256 bytes: R^2 mod N in little-endian 32-bit words */
     * };
     */
    fun getAdbPublicKeyStructBytes(): ByteArray? {
        return try {
            val cert: Certificate = keyStore.getCertificate(ADB_RSA_KEY_ALIAS) ?: return null
            val rsaPubKey = cert.publicKey as? RSAPublicKey ?: return null

            val n = rsaPubKey.modulus // 2048-bit BigInteger Modulus
            val r32 = BigInteger.ONE.shiftLeft(32) // 2^32

            // Compute n0inv = -N[0]^-1 mod 2^32
            val n0 = n.mod(r32)
            val n0invBig = r32.subtract(n0.modInverse(r32)).mod(r32)
            val n0inv = n0invBig.toLong().toInt()

            // Compute R^2 mod N where R = 2^2048
            val r = BigInteger.ONE.shiftLeft(2048)
            val rr = r.multiply(r).mod(n)

            val buffer = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(64) // RSANUMWORDS = 64
            buffer.putInt(n0inv)

            // 64 32-bit words of Modulus N (little-endian word order)
            val mask32 = BigInteger.valueOf(0xFFFFFFFFL)
            for (i in 0 until 64) {
                val word = n.shiftRight(i * 32).and(mask32).toLong().toInt()
                buffer.putInt(word)
            }

            // 64 32-bit words of RR = R^2 mod N (little-endian word order)
            for (i in 0 until 64) {
                val word = rr.shiftRight(i * 32).and(mask32).toLong().toInt()
                buffer.putInt(word)
            }

            buffer.array()
        } catch (e: Exception) {
            Log.e(TAG, "Failed constructing 524-byte ADB RSAPublicKey struct: ${e.message}", e)
            null
        }
    }

    /**
     * Signs a 20-byte ADB auth challenge using the hardware-backed RSA private key.
     * Uses NONEwithRSA with pre-built SHA-1 ASN.1 DigestInfo header.
     */
    fun signAdbChallenge(challenge: ByteArray): ByteArray? {
        return try {
            val privateKey = keyStore.getKey(ADB_RSA_KEY_ALIAS, null) as? PrivateKey ?: return null

            // Prepend SHA-1 ASN.1 DigestInfo header to 20-byte ADB challenge token
            val paddedChallenge = ByteArray(SHA1_DIGEST_INFO_PREFIX.size + challenge.size)
            System.arraycopy(SHA1_DIGEST_INFO_PREFIX, 0, paddedChallenge, 0, SHA1_DIGEST_INFO_PREFIX.size)
            System.arraycopy(challenge, 0, paddedChallenge, SHA1_DIGEST_INFO_PREFIX.size, challenge.size)

            val signature = Signature.getInstance("NONEwithRSA")
            signature.initSign(privateKey)
            signature.update(paddedChallenge)
            signature.sign()
        } catch (e: Exception) {
            Log.e(TAG, "Failed signing ADB token challenge: ${e.message}", e)
            null
        }
    }

    private fun ensureAdbRsaKeyExists() {
        if (!keyStore.containsAlias(ADB_RSA_KEY_ALIAS)) {
            val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
            val builder = KeyGenParameterSpec.Builder(
                ADB_RSA_KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setKeySize(2048)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setUnlockedDeviceRequired(true)
            }

            // Attempt StrongBox backed key generation first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val strongBoxSpec = builder.setIsStrongBoxBacked(true).build()
                    keyPairGenerator.init(strongBoxSpec)
                    keyPairGenerator.generateKeyPair()
                    Log.i(TAG, "Generated StrongBox-backed RSA-2048 key pair for ADB authentication")
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "StrongBox unavailable for ADB RSA key, falling back to standard TEE: ${e.message}")
                }
            }

            val standardSpec = builder.setIsStrongBoxBacked(false).build()
            keyPairGenerator.init(standardSpec)
            keyPairGenerator.generateKeyPair()
            Log.i(TAG, "Generated TEE-backed RSA-2048 key pair for ADB authentication")
        }
    }
}
