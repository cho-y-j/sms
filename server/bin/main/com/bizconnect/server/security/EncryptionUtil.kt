package com.bizconnect.server.security

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import java.util.Base64

/**
 * AES-256-GCM encryption for sensitive data at rest
 */
object EncryptionUtil {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_SIZE = 256
    private const val IV_SIZE = 12 // 96 bits for GCM
    private const val TAG_SIZE = 128 // 128 bits for GCM tag
    private val secureRandom = SecureRandom()

    private val encryptionKey = try {
        val keyString = System.getenv("ENCRYPTION_KEY")
            ?: throw IllegalStateException("ENCRYPTION_KEY environment variable not set")
        // Decode Base64 key
        val decodedKey = Base64.getDecoder().decode(keyString)
        if (decodedKey.size != KEY_SIZE / 8) {
            throw IllegalStateException("ENCRYPTION_KEY must be 256 bits (32 bytes)")
        }
        SecretKeySpec(decodedKey, 0, decodedKey.size, "AES")
    } catch (e: IllegalStateException) {
        throw e
    }

    /**
     * Encrypt plaintext using AES-256-GCM
     * Returns: Base64-encoded (IV + ciphertext + tag)
     */
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(IV_SIZE)
        secureRandom.nextBytes(iv)

        val gcmSpec = GCMParameterSpec(TAG_SIZE, iv)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, gcmSpec)

        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        val ciphertext = cipher.doFinal(plaintextBytes)

        // Combine IV + ciphertext
        val combined = iv + ciphertext

        // Encode as Base64
        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Decrypt ciphertext using AES-256-GCM
     * Input: Base64-encoded (IV + ciphertext + tag)
     */
    fun decrypt(encryptedData: String): String {
        try {
            // Decode Base64
            val combined = Base64.getDecoder().decode(encryptedData)

            if (combined.size < IV_SIZE) {
                throw IllegalArgumentException("Invalid encrypted data")
            }

            // Extract IV and ciphertext
            val iv = combined.sliceArray(0 until IV_SIZE)
            val ciphertext = combined.sliceArray(IV_SIZE until combined.size)

            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(TAG_SIZE, iv)
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, gcmSpec)

            val plaintext = cipher.doFinal(ciphertext)
            return plaintext.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to decrypt data: ${e.message}")
        }
    }

    /**
     * Generate a random encryption key (256-bit)
     * For initialization only - use environment variable in production
     */
    fun generateKey(): String {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(KEY_SIZE, secureRandom)
        val key = keyGen.generateKey()
        return Base64.getEncoder().encodeToString(key.encoded)
    }
}
