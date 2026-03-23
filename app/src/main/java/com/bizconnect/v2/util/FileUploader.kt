package com.bizconnect.v2.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import com.bizconnect.v2.data.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileUploader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {
    private val TAG = "FileUploader"
    private val SERVER_URL = "https://sm.on1.kr/api/user/upload/image"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    data class UploadResult(
        val success: Boolean,
        val downloadUrl: String = "",
        val previewUrl: String = "",
        val fileName: String = "",
        val fileSize: Long = 0,
        val errorMessage: String? = null
    )

    /**
     * Upload file to BizConnect server (→ S3).
     * Returns short URL: sm.on1.kr/i/abc123.jpg
     */
    suspend fun uploadFile(uri: Uri): UploadResult {
        return try {
            val fileName = getFileName(uri) ?: "file_${System.currentTimeMillis()}"
            val fileSize = getFileSize(uri)

            Log.d(TAG, "Uploading: $fileName ($fileSize bytes)")

            // Read file as Base64
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return UploadResult(false, errorMessage = "파일을 열 수 없습니다")
            val bytes = inputStream.readBytes()
            inputStream.close()

            // Detect MIME type
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val base64Data = "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"

            // Upload to server
            val token = appPreferences.getAccessToken()
                ?: return UploadResult(false, errorMessage = "로그인이 필요합니다")

            val payload = JSONObject().apply {
                put("data", base64Data)
                put("fileName", fileName)
            }.toString()

            val request = Request.Builder()
                .url(SERVER_URL)
                .addHeader("Authorization", "Bearer $token")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()

            if (response.isSuccessful) {
                val json = JSONObject(body)
                val publicUrl = json.optString("publicUrl", "")
                val previewUrl = json.optString("previewUrl", "")

                Log.d(TAG, "Upload success: $previewUrl")

                UploadResult(
                    success = true,
                    downloadUrl = previewUrl.ifEmpty { publicUrl },
                    previewUrl = previewUrl,
                    fileName = fileName,
                    fileSize = fileSize
                )
            } else {
                val error = try { JSONObject(body).optString("error", "업로드 실패") } catch (_: Exception) { "업로드 실패" }
                UploadResult(false, errorMessage = error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed: ${e.message}", e)
            UploadResult(false, errorMessage = e.message)
        }
    }

    private fun getFileName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex)
                else null
            }
        } catch (_: Exception) { null }
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex >= 0) cursor.getLong(sizeIndex)
                else 0L
            } ?: 0L
        } catch (_: Exception) { 0L }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
        }
    }
}
