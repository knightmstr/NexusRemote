package com.nexusremote.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.Certificate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed Android Keystore Credential Manager.
 * Ensures pairing tokens, client keys, and Pre-Shared Keys are NEVER stored in plain text.
 * All credentials are encrypted using AES-GCM 256-bit with hardware isolation.
 */
class KeystoreSecretManager(private val context: Context) {

    companion object {
        private const val TAG = "KeystoreSecretManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "nexus_remote_master_aes_key"
        private const val ADB_RSA_KEY_ALIAS = "nexus_remote_adb_rsa_key"
        private const val PREFS_NAME = "nexus_remote_secure_vault"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private val encryptedPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        ensureMasterAesKeyExists()
        ensureAdbRsaKeyExists()
    }

    /**
     * Stores an encrypted device authentication token (e.g. webOS client-key, Tizen token, Sony PSK).
     */
    fun storeSecret(deviceId: String, secretKeyName: String, plainSecret: String) {
        try {
            val masterKey = keyStore.getKey(MASTER_KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, masterKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainSecret.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

            val base64Payload = Base64.encodeToString(combined, Base64.NO_WRAP)
            val storageKey = "${deviceId}_${secretKeyName}"
            encryptedPrefs.edit().putString(storageKey, base64Payload).apply()
            Log.d(TAG, "Securely encrypted and stored secret for device '$deviceId' ($secretKeyName)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed storing secret for device $deviceId: ${e.message}", e)
        }
    }

    /**
     * Retrieves and decrypts a device authentication secret.
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

            val masterKey = keyStore.getKey(MASTER_KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)

            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed decrypting secret for device $deviceId: ${e.message}", e)
            null
        }
    }

    /**
     * Removes credentials for a deleted device.
     */
    fun removeDeviceSecrets(deviceId: String) {
        val editor = encryptedPrefs.edit()
        encryptedPrefs.all.keys.filter { it.startsWith("${deviceId}_") }.forEach { key ->
            editor.remove(key)
        }
        editor.apply()
        Log.d(TAG, "Cleared stored secrets for device '$deviceId'")
    }

    /**
     * Retrieves the device's RSA public key for ADB network pairing.
     */
    fun getAdbPublicKeyBytes(): ByteArray? {
        return try {
            val certificate: Certificate? = keyStore.getCertificate(ADB_RSA_KEY_ALIAS)
            certificate?.publicKey?.encoded
        } catch (e: Exception) {
            Log.e(TAG, "Failed retrieving ADB public key: ${e.message}", e)
            null
        }
    }

    /**
     * Signs an ADB auth token challenge using the hardware-backed RSA private key.
     */
    fun signAdbChallenge(challenge: ByteArray): ByteArray? {
        return try {
            val privateKey = keyStore.getKey(ADB_RSA_KEY_ALIAS, null) as? PrivateKey ?: return null
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initSign(privateKey)
            signature.update(challenge)
            signature.sign()
        } catch (e: Exception) {
            Log.e(TAG, "Failed signing ADB token: ${e.message}", e)
            null
        }
    }

    private fun ensureMasterAesKeyExists() {
        if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            try {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
                Log.i(TAG, "Created hardware-backed AES-256 master key in Android Keystore")
            } catch (e: Exception) {
                Log.e(TAG, "Failed creating AES master key: ${e.message}", e)
            }
        }
    }

    private fun ensureAdbRsaKeyExists() {
        if (!keyStore.containsAlias(ADB_RSA_KEY_ALIAS)) {
            try {
                val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    ADB_RSA_KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setKeySize(2048)
                    .build()
                keyPairGenerator.init(keyGenParameterSpec)
                keyPairGenerator.generateKeyPair()
                Log.i(TAG, "Created hardware-backed RSA-2048 ADB key pair in Android Keystore")
            } catch (e: Exception) {
                Log.e(TAG, "Failed creating RSA key pair: ${e.message}", e)
            }
        }
    }
}
