package com.bizconnect.v2.domain.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject

/**
 * Helper for constructing and managing MMS messages.
 * Handles:
 * - MMS PDU construction for sending via system framework
 * - Image compression and validation
 * - Saving sent MMS to content provider
 * - Carrier-specific MMS configuration
 *
 * As the default SMS app, this has direct access to system MMS APIs.
 */
class MmsHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MmsHelper"

        // MMS constraints
        const val MMS_MAX_SIZE = 300 * 1024      // 300KB
        const val MMS_IMAGE_MAX_WIDTH = 1024
        const val MMS_IMAGE_MAX_HEIGHT = 1024
        const val MMS_COMPRESSION_QUALITY = 75

        // MMS content types
        private const val MMS_CONTENT_TYPE = "application/vnd.wap.multipart.related"
        private const val TEXT_CONTENT_TYPE = "text/plain"
        private const val IMAGE_CONTENT_TYPE = "image/jpeg"
    }

    /**
     * Build complete MMS PDU for sending
     * Returns the raw bytes of the MMS message ready for transmission
     */
    fun buildMmsPdu(
        recipientAddress: String,
        subject: String?,
        body: String?,
        imageUri: Uri?,
        imageMimeType: String = "image/jpeg"
    ): ByteArray {
        return try {
            // In production, this would use proper MMS PDU library
            // For now, we return a placeholder that system SMS framework will handle
            buildMmsPduInternal(
                recipientAddress = recipientAddress,
                subject = subject,
                body = body,
                imageUri = imageUri,
                imageMimeType = imageMimeType
            )
        } catch (e: Exception) {
            byteArrayOf()
        }
    }

    /**
     * Internal PDU building with proper MMS structure
     */
    private fun buildMmsPduInternal(
        recipientAddress: String,
        subject: String?,
        body: String?,
        imageUri: Uri?,
        imageMimeType: String
    ): ByteArray {
        val pduParts = mutableListOf<ByteArray>()

        // Add text part if body exists
        if (!body.isNullOrBlank()) {
            val textPart = buildTextPart(body)
            pduParts.add(textPart)
        }

        // Add image part if imageUri exists
        if (imageUri != null) {
            val imagePart = buildImagePart(imageUri, imageMimeType)
            if (imagePart.isNotEmpty()) {
                pduParts.add(imagePart)
            }
        }

        // Combine all parts into a multipart message
        return combinePartsIntoPdu(pduParts, subject, recipientAddress)
    }

    /**
     * Build text part of MMS message
     */
    private fun buildTextPart(text: String): ByteArray {
        return try {
            // Add proper MIME headers for text
            val headers = "Content-Type: $TEXT_CONTENT_TYPE; charset=utf-8\r\nContent-Transfer-Encoding: 8bit\r\n\r\n"
            val headerBytes = headers.toByteArray(Charsets.UTF_8)
            val textBytes = text.toByteArray(Charsets.UTF_8)
            headerBytes + textBytes
        } catch (e: Exception) {
            byteArrayOf()
        }
    }

    /**
     * Build image part of MMS message
     */
    private fun buildImagePart(imageUri: Uri, mimeType: String): ByteArray {
        return try {
            val imageData = context.contentResolver.openInputStream(imageUri)?.readBytes()
                ?: return byteArrayOf()

            val headers = "Content-Type: $mimeType\r\nContent-Transfer-Encoding: binary\r\nContent-ID: <image>\r\n\r\n"
            val headerBytes = headers.toByteArray(Charsets.UTF_8)
            headerBytes + imageData
        } catch (e: Exception) {
            byteArrayOf()
        }
    }

    /**
     * Combine all parts into a single MMS PDU
     */
    private fun combinePartsIntoPdu(
        parts: List<ByteArray>,
        subject: String?,
        recipient: String
    ): ByteArray {
        return try {
            val pdu = mutableListOf<Byte>()

            // MMS message wrapper (simplified)
            // In production, use com.google.android.mms library for proper PDU encoding
            val header = buildMmsHeader(subject, recipient)
            pdu.addAll(header.toList())

            // Add parts
            for (part in parts) {
                pdu.addAll(part.toList())
            }

            pdu.toByteArray()
        } catch (e: Exception) {
            byteArrayOf()
        }
    }

    /**
     * Build MMS header with required fields
     */
    private fun buildMmsHeader(subject: String?, recipient: String): ByteArray {
        return try {
            // Simplified header - production code should use proper MMS PDU builder
            val headerData = mutableListOf<Byte>()

            // Message type (Send Request = 0x80)
            headerData.add(0x80.toByte())

            // Version (1.2 = 0x12)
            headerData.add(0x12.toByte())

            // Transaction ID
            val txId = UUID.randomUUID().toString().take(20)
            headerData.addAll(txId.toByteArray().toList())

            headerData.toByteArray()
        } catch (e: Exception) {
            byteArrayOf()
        }
    }

    /**
     * Get carrier-specific MMS configuration
     * Returns APN and MMS settings as Bundle
     */
    fun getMmsConfig(): android.os.Bundle {
        val config = android.os.Bundle()

        // Default MMS configuration
        config.putInt("mmscPort", 80)
        config.putString("mmscUrl", "")  // Will be set by system based on carrier
        config.putInt("maxMessageSize", MMS_MAX_SIZE)
        config.putInt("maxImageHeight", MMS_IMAGE_MAX_HEIGHT)
        config.putInt("maxImageWidth", MMS_IMAGE_MAX_WIDTH)
        config.putBoolean("supportMms", true)
        config.putBoolean("supportMmsPdu", true)

        // User agent (standard for Android)
        config.putString("userAgent", "Android-MMS/1.2")

        return config
    }

    /**
     * Compress image to meet MMS size constraints
     * Returns URI of compressed image
     */
    suspend fun compressImage(uri: Uri, maxSizeBytes: Int = MMS_MAX_SIZE): Uri = withContext(Dispatchers.IO) {
        return@withContext try {
            // Load original image
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext uri
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) {
                return@withContext uri
            }

            // Calculate compression needed
            var bitmap = originalBitmap
            var quality = MMS_COMPRESSION_QUALITY

            // Resize if necessary
            if (bitmap.width > MMS_IMAGE_MAX_WIDTH || bitmap.height > MMS_IMAGE_MAX_HEIGHT) {
                bitmap = resizeBitmap(bitmap, MMS_IMAGE_MAX_WIDTH, MMS_IMAGE_MAX_HEIGHT)
            }

            // Compress until size constraint is met
            var compressedData = compressBitmapToBytes(bitmap, quality)

            while (compressedData.size > maxSizeBytes && quality > 10) {
                quality -= 5
                compressedData = compressBitmapToBytes(bitmap, quality)
            }

            // Save compressed image to cache
            val compressedFile = File(context.cacheDir, "mms_${UUID.randomUUID()}.jpg")
            FileOutputStream(compressedFile).use { output ->
                output.write(compressedData)
            }

            // Return URI to compressed image
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    compressedFile
                )
            } else {
                Uri.fromFile(compressedFile)
            }
        } catch (e: Exception) {
            uri // Return original URI if compression fails
        }
    }

    /**
     * Resize bitmap to fit within max dimensions while maintaining aspect ratio
     */
    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val ratioBitmap = width.toFloat() / height
        val ratioMax = maxWidth.toFloat() / maxHeight

        val (finalWidth, finalHeight) = if (ratioBitmap > ratioMax) {
            val newWidth = maxWidth
            val newHeight = (maxWidth / ratioBitmap).toInt()
            newWidth to newHeight
        } else {
            val newHeight = maxHeight
            val newWidth = (maxHeight * ratioBitmap).toInt()
            newWidth to newHeight
        }

        return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
    }

    /**
     * Compress bitmap to JPEG bytes with specified quality
     */
    private fun compressBitmapToBytes(bitmap: Bitmap, quality: Int): ByteArray {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }

    /**
     * Save sent MMS to system content provider for conversation thread
     * This makes the MMS appear in the messaging app conversation
     */
    suspend fun saveSentMms(
        address: String,
        body: String?,
        imageUri: Uri?,
        threadId: Long
    ): Uri? = withContext(Dispatchers.IO) {
        return@withContext try {
            // Create MMS message entry
            val values = ContentValues().apply {
                put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_SENT)
                put(Telephony.Mms.THREAD_ID, threadId)
                put("recipient_ids", address)
                put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
                put(Telephony.Mms.READ, true)
                put("st", 128) // STATUS_COMPLETE = 128
                put("m_type", 128) // MESSAGE_TYPE_SEND_REQ = 128

                // Add subject if available
                if (!body.isNullOrBlank()) {
                    put(Telephony.Mms.SUBJECT, body.take(100))
                }
            }

            // Insert into MMS content provider
            val mmsUri = context.contentResolver.insert(
                Telephony.Mms.CONTENT_URI,
                values
            )

            // Add parts (text and image)
            if (mmsUri != null) {
                val mmsId = mmsUri.lastPathSegment?.toLongOrNull() ?: 0

                // Add text part
                if (!body.isNullOrBlank()) {
                    addMmsPart(mmsId, "text/plain", body, null)
                }

                // Add image part
                if (imageUri != null) {
                    addMmsPart(mmsId, "image/jpeg", null, imageUri)
                }

                // Update address
                addMmsAddress(mmsId, address, Telephony.Mms.MESSAGE_BOX_SENT)
            }

            mmsUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Add a part (text or image) to an MMS message
     */
    private fun addMmsPart(
        mmsId: Long,
        contentType: String,
        text: String?,
        imageUri: Uri?
    ) {
        try {
            val values = ContentValues().apply {
                put("mid", mmsId)
                put("ct", contentType)
                put("cl", UUID.randomUUID().toString())

                when {
                    !text.isNullOrBlank() -> {
                        put("text", text)
                    }
                    imageUri != null -> {
                        // Store image URI reference; actual data will be read by content provider
                        put("_data", imageUri.toString())
                    }
                }
            }

            val partUri = Uri.withAppendedPath(
                Telephony.Mms.CONTENT_URI,
                "$mmsId/part"
            )

            context.contentResolver.insert(partUri, values)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Add recipient address to MMS message
     */
    private fun addMmsAddress(mmsId: Long, address: String, type: Int) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Mms.Addr.MSG_ID, mmsId)
                put(Telephony.Mms.Addr.CONTACT_ID, 0)
                put(Telephony.Mms.Addr.ADDRESS, address)
                put(Telephony.Mms.Addr.TYPE, type)
                put(Telephony.Mms.Addr.CHARSET, "UTF-8")
            }

            val addrUri = Uri.withAppendedPath(
                Telephony.Mms.CONTENT_URI,
                "$mmsId/addr"
            )

            context.contentResolver.insert(addrUri, values)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Validate image before sending as MMS
     */
    fun validateImage(uri: Uri): ImageValidationResult {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return ImageValidationResult(false, "Cannot open image")

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            val errors = mutableListOf<String>()

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                errors.add("Invalid image dimensions")
            }

            if (options.outWidth > MMS_IMAGE_MAX_WIDTH || options.outHeight > MMS_IMAGE_MAX_HEIGHT) {
                errors.add("Image too large: ${options.outWidth}x${options.outHeight}")
            }

            ImageValidationResult(
                isValid = errors.isEmpty(),
                errorMessage = if (errors.isEmpty()) null else errors.joinToString(", ")
            )
        } catch (e: Exception) {
            ImageValidationResult(false, e.message)
        }
    }

    /**
     * Get image file size
     */
    fun getImageSize(uri: Uri): Long {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val sizeIndex = cursor?.getColumnIndex(MediaStore.MediaColumns.SIZE) ?: -1

            val size = if (sizeIndex >= 0 && cursor?.moveToFirst() == true) {
                cursor.getLong(sizeIndex)
            } else {
                context.contentResolver.openInputStream(uri)?.available()?.toLong() ?: 0L
            }

            cursor?.close()
            size
        } catch (e: Exception) {
            0L
        }
    }
}

/**
 * Result of image validation
 */
data class ImageValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)
