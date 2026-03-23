package com.bizconnect.server.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date

/**
 * Firebase Cloud Messaging HTTP v1 API Service
 * Uses service account credentials to send data messages to app
 */
class FcmService {
    private val serviceAccountPath = System.getenv("FIREBASE_SERVICE_ACCOUNT_PATH")
        ?: "/home/ubuntu/bizconnect-v2/firebase-service-account.json"

    private var projectId: String = ""
    private var clientEmail: String = ""
    private var privateKey: RSAPrivateKey? = null
    private var cachedToken: String? = null
    private var tokenExpiry: Long = 0

    init {
        loadServiceAccount()
    }

    private fun loadServiceAccount() {
        try {
            val file = File(serviceAccountPath)
            if (!file.exists()) {
                println("FCM: Service account file not found at $serviceAccountPath")
                return
            }
            val json = Json.parseToJsonElement(file.readText()).jsonObject
            projectId = json["project_id"]?.jsonPrimitive?.content ?: ""
            clientEmail = json["client_email"]?.jsonPrimitive?.content ?: ""

            val pkPem = json["private_key"]?.jsonPrimitive?.content ?: ""
            val pkClean = pkPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\n", "")
                .replace("\n", "")
                .trim()

            val keyBytes = Base64.getDecoder().decode(pkClean)
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec) as RSAPrivateKey

            println("FCM: Loaded service account for project $projectId")
        } catch (e: Exception) {
            println("FCM: Failed to load service account: ${e.message}")
        }
    }

    /**
     * Get OAuth2 access token for FCM API
     * Tokens are cached for ~50 minutes (expire at 60)
     */
    private fun getAccessToken(): String? {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiry) {
            return cachedToken
        }

        val pk = privateKey ?: return null
        val now = Date()
        val expiry = Date(now.time + 3600_000) // 1 hour

        // Create JWT assertion
        val jwt = JWT.create()
            .withIssuer(clientEmail)
            .withAudience("https://oauth2.googleapis.com/token")
            .withClaim("scope", "https://www.googleapis.com/auth/firebase.messaging")
            .withIssuedAt(now)
            .withExpiresAt(expiry)
            .sign(Algorithm.RSA256(null, pk))

        // Exchange JWT for access token
        try {
            val conn = URL("https://oauth2.googleapis.com/token").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.doOutput = true
            conn.outputStream.write(
                "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$jwt".toByteArray()
            )

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val json = Json.parseToJsonElement(body).jsonObject
                cachedToken = json["access_token"]?.jsonPrimitive?.content
                tokenExpiry = System.currentTimeMillis() + 3000_000 // cache for 50 min
                conn.disconnect()
                return cachedToken
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                println("FCM: Token exchange failed: ${conn.responseCode} $err")
                conn.disconnect()
            }
        } catch (e: Exception) {
            println("FCM: Token exchange error: ${e.message}")
        }
        return null
    }

    /**
     * Send data message to a specific device
     * @param fcmToken Device FCM token
     * @param data Key-value pairs to send as data message
     */
    fun sendDataMessage(fcmToken: String, data: Map<String, String>): Boolean {
        if (projectId.isBlank()) {
            println("FCM: Project ID not set, skipping push")
            return false
        }

        val accessToken = getAccessToken()
        if (accessToken == null) {
            println("FCM: No access token available")
            return false
        }

        try {
            val payload = buildJsonObject {
                putJsonObject("message") {
                    put("token", fcmToken)
                    putJsonObject("data") {
                        for ((k, v) in data) put(k, v)
                    }
                    putJsonObject("android") {
                        put("priority", "high")
                    }
                }
            }

            val url = URL("https://fcm.googleapis.com/v1/projects/$projectId/messages:send")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.doOutput = true
            conn.outputStream.write(payload.toString().toByteArray())

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                println("FCM: Push sent successfully to ${fcmToken.take(10)}...")
                conn.disconnect()
                return true
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
                println("FCM: Push failed: $responseCode $err")
                // If token is invalid, clear cached token
                if (responseCode == 401) {
                    cachedToken = null
                    tokenExpiry = 0
                }
                conn.disconnect()
            }
        } catch (e: Exception) {
            println("FCM: Push error: ${e.message}")
        }
        return false
    }

    /**
     * Send SMS batch notification to app
     */
    fun notifySmsBatch(fcmToken: String, jobId: String, count: Int): Boolean {
        return sendDataMessage(fcmToken, mapOf(
            "type" to "web_sms_batch",
            "jobId" to jobId,
            "count" to count.toString(),
            "timestamp" to System.currentTimeMillis().toString()
        ))
    }
}
