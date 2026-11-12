package com.nexusremote.security

import android.util.Log
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Trust-On-First-Use (TOFU) X509TrustManager implementation for encrypted Smart TV sockets.
 * Enforces OWASP MASVS TLS Pinning requirements by computing the SHA-256 fingerprint of the TV's
 * leaf certificate upon initial pairing, storing it securely via KeystoreSecretManager, and
 * rejecting future SSL/TLS connections if the leaf certificate fingerprint changes.
 */
class TofuTrustManager(
    private val deviceId: String,
    private val keystoreSecretManager: KeystoreSecretManager
) : X509TrustManager {

    companion object {
        private const val TAG = "TofuTrustManager"
        private const val SECRET_KEY_FINGERPRINT = "tls_cert_fingerprint"

        /**
         * Creates a customized SSLContext using this Trust-On-First-Use TrustManager.
         */
        fun createSslContext(
            deviceId: String,
            keystoreSecretManager: KeystoreSecretManager
        ): SSLContext {
            val tofuTrustManager = TofuTrustManager(deviceId, keystoreSecretManager)
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(tofuTrustManager), null)
            return sslContext
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // Client cert validation handled by specific protocol handshake where applicable
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) {
            throw CertificateException("Empty server certificate chain received from TV ($deviceId)")
        }

        val leafCert = chain[0]
        val digest = MessageDigest.getInstance("SHA-256").digest(leafCert.encoded)
        val currentFingerprint = digest.joinToString(":") { "%02X".format(it) }

        val pinnedFingerprint = keystoreSecretManager.getSecret(deviceId, SECRET_KEY_FINGERPRINT)

        if (pinnedFingerprint.isNullOrBlank()) {
            // Trust-On-First-Use (TOFU): Pin certificate fingerprint on initial successful handshake
            keystoreSecretManager.storeSecret(deviceId, SECRET_KEY_FINGERPRINT, currentFingerprint)
            Log.i(TAG, "TOFU: Pinned new TLS SHA-256 leaf certificate fingerprint for device '$deviceId': $currentFingerprint")
        } else {
            // Verify leaf certificate against pinned fingerprint
            if (!pinnedFingerprint.equals(currentFingerprint, ignoreCase = true)) {
                Log.e(
                    TAG,
                    "TLS PINNING ERROR: Certificate fingerprint mismatch for device '$deviceId'! Expected: $pinnedFingerprint, Received: $currentFingerprint"
                )
                throw CertificateException(
                    "TLS Certificate Fingerprint Mismatch for device '$deviceId'. Potential Man-in-the-Middle (MitM) attack."
                )
            }
            Log.d(TAG, "TLS Certificate Fingerprint verified successfully for device '$deviceId'")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
