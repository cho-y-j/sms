package com.bizconnect.v2.data.remote.interceptor

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

/**
 * Interceptor that adds security headers and request signing.
 * Implements HMAC-SHA256 signing and device fingerprint headers.
 */
class SecurityInterceptor @Inject constructor(
    private val deviceFingerprint: String
) : Interceptor {

    companion object {
        private const val TAG = "SecurityInterceptor"
        private const val HMAC_SHA256 = "HmacSHA256"
        private const val SIGNATURE_HEADER = "X-Request-Signature"
        private const val DEVICE_HEADER = "X-Device-Id"
        private const val TIMESTAMP_HEADER = "X-Request-Timestamp"
        private const val NONCE_HEADER = "X-Request-Nonce"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Add security headers
        val securedRequest = originalRequest.newBuilder()
            .header(DEVICE_HEADER, deviceFingerprint)
            .header(TIMESTAMP_HEADER, System.currentTimeMillis().toString())
            .header(NONCE_HEADER, generateNonce())
            .build()

        // Sign request if it has a body
        val signedRequest = if (securedRequest.body != null) {
            addRequestSignature(securedRequest)
        } else {
            securedRequest
        }

        return chain.proceed(signedRequest)
    }

    private fun addRequestSignature(request: Request): Request {
        return try {
            val bodyBytes = request.body?.let { body ->
                val buffer = okio.Buffer()
                body.writeTo(buffer)
                buffer.readByteArray()
            } ?: ByteArray(0)

            val signature = generateHmacSignature(bodyBytes)

            request.newBuilder()
                .header(SIGNATURE_HEADER, signature)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add request signature", e)
            request
        }
    }

    private fun generateHmacSignature(data: ByteArray): String {
        return try {
            val key = SecretKeySpec(
                "bizconnect_v2_secret".toByteArray(),
                0,
                "bizconnect_v2_secret".length,
                HMAC_SHA256
            )
            val mac = Mac.getInstance(HMAC_SHA256)
            mac.init(key)
            val rawHmac = mac.doFinal(data)
            rawHmac.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate HMAC signature", e)
            ""
        }
    }

    private fun generateNonce(): String {
        return (0..31)
            .map { "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"[(Math.random() * 62).toInt()] }
            .joinToString("")
    }
}
