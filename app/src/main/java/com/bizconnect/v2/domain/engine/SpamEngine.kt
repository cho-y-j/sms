package com.bizconnect.v2.domain.engine

import android.util.Log
import com.bizconnect.v2.data.local.db.dao.SpamFilterDao
import com.bizconnect.v2.data.remote.spam.SpamApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spam detection and filtering engine.
 * Checks incoming messages against filters and blocks spam.
 */
@Singleton
class SpamEngine @Inject constructor(
    private val spamFilterDao: SpamFilterDao,
    private val spamApiService: SpamApiService
) {
    companion object {
        private const val TAG = "SpamEngine"
        private val VERIFICATION_CODE_PATTERN = Regex("""(\d{4,6})""")
    }

    /**
     * Check if a phone number is an overseas (non-Korean) number.
     * Korean mobile: starts with 01 (010, 011, 016, 017, 018, 019)
     * Korean landline: starts with 02-09
     * Korean with country code: starts with +82 or 0082
     * Everything else = overseas
     */
    fun isOverseasNumber(address: String): Boolean {
        val cleaned = address.trim().replace("[\\s\\-()]".toRegex(), "")

        // Korean country code
        if (cleaned.startsWith("+82") || cleaned.startsWith("0082")) {
            return false
        }

        // If it starts with +, it's an international number (non-Korean)
        if (cleaned.startsWith("+") || cleaned.startsWith("00")) {
            return true
        }

        // Korean domestic numbers start with 0 (01x mobile, 02-09 landline)
        if (cleaned.startsWith("0") && cleaned.length >= 9) {
            return false
        }

        // Short numbers (service numbers) are Korean
        if (cleaned.length <= 6) {
            return false
        }

        // Numbers without leading 0 or + that are long enough could be overseas
        if (!cleaned.startsWith("0") && cleaned.length >= 10) {
            return true
        }

        return false
    }

    /**
     * Check if a number is in the overseas exception list
     */
    private suspend fun isOverseasException(address: String): Boolean {
        val cleaned = address.trim().replace("[\\s\\-()]".toRegex(), "")
        val exceptions = spamFilterDao.getByTypeList("overseas_exception")
        return exceptions.any { exception ->
            val exCleaned = exception.value.trim().replace("[\\s\\-()]".toRegex(), "")
            cleaned == exCleaned || cleaned.endsWith(exCleaned) || exCleaned.endsWith(cleaned)
        }
    }

    /**
     * Check if a message is spam
     * Returns true if message matches any active filter
     */
    suspend fun isSpam(address: String, body: String, checkOverseas: Boolean = false): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Check overseas blocking
                if (checkOverseas && isOverseasNumber(address)) {
                    if (!isOverseasException(address)) {
                        Log.d(TAG, "Overseas number blocked: $address")
                        return@withContext true
                    }
                }

                // Check if number is blocked
                if (isBlocked(address)) {
                    return@withContext true
                }

                // Check keyword filters
                val filters = spamFilterDao.getAllActive()

                for (filter in filters) {
                    when {
                        filter.type == "keyword" && body.contains(filter.pattern, ignoreCase = true) -> {
                            Log.d(TAG, "Message matched keyword filter: ${filter.pattern}")
                            return@withContext true
                        }
                        filter.type == "regex" -> {
                            try {
                                val regex = Regex(filter.pattern)
                                if (regex.containsMatchIn(body)) {
                                    Log.d(TAG, "Message matched regex filter: ${filter.pattern}")
                                    return@withContext true
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Invalid regex filter: ${filter.pattern}", e)
                            }
                        }
                    }
                }

                return@withContext false
            } catch (e: Exception) {
                Log.e(TAG, "Error checking spam", e)
                return@withContext false
            }
        }
    }

    /**
     * Check if a phone number is blocked
     */
    suspend fun isBlocked(address: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val blockedNumber = spamFilterDao.getBlockedNumber(address)
                return@withContext blockedNumber != null && blockedNumber.isActive
            } catch (e: Exception) {
                Log.e(TAG, "Error checking blocked number", e)
                return@withContext false
            }
        }
    }

    /**
     * Add number to spam list
     */
    suspend fun blockNumber(number: String) {
        return withContext(Dispatchers.IO) {
            try {
                val blockEntry = com.bizconnect.v2.data.local.db.entity.SpamFilterEntity(
                    id = number,
                    type = "number",
                    pattern = number,
                    value = number,
                    reason = "User blocked",
                    isActive = true,
                    createdAt = System.currentTimeMillis(),
                    createdBy = "manual"
                )
                spamFilterDao.insert(blockEntry)
                Log.d(TAG, "Number blocked: $number")
            } catch (e: Exception) {
                Log.e(TAG, "Error blocking number: $number", e)
            }
        }
    }

    /**
     * Remove number from spam list
     */
    suspend fun unblockNumber(number: String) {
        return withContext(Dispatchers.IO) {
            try {
                spamFilterDao.deleteById(number)
                Log.d(TAG, "Number unblocked: $number")
            } catch (e: Exception) {
                Log.e(TAG, "Error unblocking number: $number", e)
            }
        }
    }

    /**
     * Add keyword filter
     */
    suspend fun addKeywordFilter(keyword: String) {
        return withContext(Dispatchers.IO) {
            try {
                val filterId = keyword.hashCode().toString()
                val filter = com.bizconnect.v2.data.local.db.entity.SpamFilterEntity(
                    id = filterId,
                    type = "keyword",
                    pattern = keyword,
                    value = keyword,
                    reason = "Keyword filter",
                    isActive = true,
                    createdAt = System.currentTimeMillis(),
                    createdBy = "manual"
                )
                spamFilterDao.insert(filter)
                Log.d(TAG, "Keyword filter added: $keyword")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding keyword filter: $keyword", e)
            }
        }
    }

    /**
     * Remove keyword filter
     */
    suspend fun removeKeywordFilter(keyword: String) {
        return withContext(Dispatchers.IO) {
            try {
                val filterId = keyword.hashCode().toString()
                spamFilterDao.deleteById(filterId)
                Log.d(TAG, "Keyword filter removed: $keyword")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing keyword filter: $keyword", e)
            }
        }
    }

    /**
     * Auto-detect verification code in message
     * Returns the code if found, null otherwise
     */
    fun extractVerificationCode(body: String): String? {
        return try {
            val match = VERIFICATION_CODE_PATTERN.find(body)
            val code = match?.value

            if (code != null) {
                Log.d(TAG, "Verification code detected: $code")
            }

            code
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting verification code", e)
            null
        }
    }

    /**
     * Check phone number against external spam databases (APick / IPQS)
     */
    suspend fun checkExternalDb(phoneNumber: String): SpamApiService.SpamCheckResult {
        return spamApiService.checkNumber(phoneNumber)
    }

    /**
     * Get spam statistics
     */
    suspend fun getSpamStats(): SpamStats {
        return withContext(Dispatchers.IO) {
            try {
                val blockedNumbers = spamFilterDao.getBlockedNumbers()
                val keywordFilters = spamFilterDao.getKeywordFilters()

                // Get last 30 days blocked count
                val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000)
                val last30DaysBlocked = spamFilterDao.getBlockedCountSince(thirtyDaysAgo)

                return@withContext SpamStats(
                    totalBlocked = blockedNumbers.count { it.isActive } + last30DaysBlocked,
                    blockedNumbers = blockedNumbers.count { it.isActive },
                    keywordFilters = keywordFilters.count { it.isActive },
                    last30DaysBlocked = last30DaysBlocked
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error getting spam stats", e)
                return@withContext SpamStats(
                    totalBlocked = 0,
                    blockedNumbers = 0,
                    keywordFilters = 0,
                    last30DaysBlocked = 0
                )
            }
        }
    }
}

data class SpamStats(
    val totalBlocked: Int,
    val blockedNumbers: Int,
    val keywordFilters: Int,
    val last30DaysBlocked: Int
)
