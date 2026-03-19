package com.bizconnect.v2.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileUploader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "FileUploader"
    private val storage = FirebaseStorage.getInstance()
    private val storageRef = storage.reference.child("shared_files")

    data class UploadResult(
        val success: Boolean,
        val downloadUrl: String = "",
        val fileName: String = "",
        val fileSize: Long = 0,
        val errorMessage: String? = null
    )

    /**
     * Upload file to Firebase Storage and return download URL.
     * Files are stored under shared_files/{uuid}_{filename}
     */
    suspend fun uploadFile(uri: Uri): UploadResult {
        return try {
            val fileName = getFileName(uri) ?: "file_${System.currentTimeMillis()}"
            val fileSize = getFileSize(uri)
            val uniqueName = "${UUID.randomUUID().toString().take(8)}_$fileName"

            Log.d(TAG, "Uploading: $fileName ($fileSize bytes)")

            val fileRef = storageRef.child(uniqueName)

            // Upload
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return UploadResult(false, errorMessage = "파일을 열 수 없습니다")

            fileRef.putStream(inputStream).await()
            inputStream.close()

            // Get download URL and shorten it
            val longUrl = fileRef.downloadUrl.await().toString()
            val downloadUrl = shortenUrl(longUrl) ?: longUrl

            Log.d(TAG, "Upload success: $downloadUrl (original: ${longUrl.take(60)}...)")

            UploadResult(
                success = true,
                downloadUrl = downloadUrl,
                fileName = fileName,
                fileSize = fileSize
            )
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

    /**
     * Shorten URL using is.gd API (free, no ads, no API key needed).
     */
    private fun shortenUrl(longUrl: String): String? {
        return try {
            val encoded = URLEncoder.encode(longUrl, "UTF-8")
            val apiUrl = URL("https://is.gd/create.php?format=simple&url=$encoded")
            val conn = apiUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val shortUrl = conn.inputStream.bufferedReader().readText().trim()
            conn.disconnect()
            if (shortUrl.startsWith("http")) shortUrl else null
        } catch (e: Exception) {
            Log.w(TAG, "URL shortening failed: ${e.message}")
            null
        }
    }

    /**
     * Format file size for display.
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
        }
    }
}
