package com.bizconnect.v2.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(
    val dataStoreRef: DataStore<Preferences>
) {
    private val dataStore: DataStore<Preferences> = dataStoreRef

    companion object {
        // Auth keys
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_FCM_TOKEN = stringPreferencesKey("fcm_token")

        // Settings keys
        private val KEY_AUTO_APPROVE = booleanPreferencesKey("auto_approve")
        private val KEY_AUTO_APPROVE_THRESHOLD = intPreferencesKey("auto_approve_threshold")
        private val KEY_DAILY_LIMIT = intPreferencesKey("daily_limit")
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        private val KEY_VIBRATE_ENABLED = booleanPreferencesKey("vibrate_enabled")

        // Business / billing keys
        private val KEY_SUBSCRIPTION_TIER = stringPreferencesKey("subscription_tier")
        private val KEY_CREDIT_BALANCE = doublePreferencesKey("credit_balance")
        private val KEY_SMS_COST = doublePreferencesKey("sms_cost")
        private val KEY_LMS_COST = doublePreferencesKey("lms_cost")
        private val KEY_MMS_COST = doublePreferencesKey("mms_cost")
        private val KEY_PAID_TIER_DAILY_LIMIT = intPreferencesKey("paid_tier_daily_limit")

        // AI keys
        private val KEY_DEEPSEEK_API_KEY = stringPreferencesKey("deepseek_api_key")
        private val KEY_AI_ENABLED = booleanPreferencesKey("ai_enabled")
        private val KEY_AI_SYSTEM_PROMPT = stringPreferencesKey("ai_system_prompt")
        private val KEY_AI_DAILY_COUNT = intPreferencesKey("ai_daily_count")
        private val KEY_AI_DAILY_DATE = stringPreferencesKey("ai_daily_date")

        // Spam keys
        private val KEY_OVERSEAS_BLOCK_ENABLED = booleanPreferencesKey("overseas_block_enabled")
        private val KEY_APICK_API_KEY = stringPreferencesKey("apick_api_key")
        private val KEY_IPQS_API_KEY = stringPreferencesKey("ipqs_api_key")
        private val KEY_SPAM_API_ENABLED = booleanPreferencesKey("spam_api_enabled")

        // Role keys
        private val KEY_USER_ROLE = stringPreferencesKey("user_role")

        // SMS Sync consent
        private val KEY_SMS_SYNC_CONSENTED = booleanPreferencesKey("sms_sync_consented")

        // Engine keys
        private val KEY_QUEUE_THROTTLE_MS = longPreferencesKey("queue_throttle_ms")
        private val KEY_LIMIT_MODE = stringPreferencesKey("limit_mode")

        // Defaults
        private const val DEFAULT_DAILY_LIMIT = 50
        private const val DEFAULT_PAID_TIER_DAILY_LIMIT = 149
        private const val DEFAULT_SUBSCRIPTION_TIER = "free"
        private const val DEFAULT_CREDIT_BALANCE = 0.0
        private const val DEFAULT_SMS_COST = 9.8
        private const val DEFAULT_LMS_COST = 29.0
        private const val DEFAULT_MMS_COST = 63.0
        private const val DEFAULT_QUEUE_THROTTLE_MS = 500L
        private const val DEFAULT_LIMIT_MODE = "safe"
    }

    // --- Helpers ---

    private var cache = mutableMapOf<String, Any?>()

    @Suppress("UNCHECKED_CAST")
    private fun <T> readSync(key: Preferences.Key<T>, default: T): T {
        val cached = cache[key.name] as? T
        if (cached != null) return cached
        return runBlocking {
            dataStore.data.first()[key] ?: default
        }.also { cache[key.name] = it }
    }

    private fun <T> writeSync(key: Preferences.Key<T>, value: T) {
        cache[key.name] = value
        runBlocking {
            dataStore.edit { prefs -> prefs[key] = value }
        }
    }

    // --- Auth ---

    fun getUserId(): String? = readSync(KEY_USER_ID, "").ifEmpty { null }

    fun setUserId(userId: String) = writeSync(KEY_USER_ID, userId)

    fun getAccessToken(): String? = readSync(KEY_ACCESS_TOKEN, "").ifEmpty { null }

    fun setAccessToken(token: String) = writeSync(KEY_ACCESS_TOKEN, token)

    fun saveAccessToken(token: String) = setAccessToken(token)

    fun getRefreshToken(): String? = readSync(KEY_REFRESH_TOKEN, "").ifEmpty { null }

    fun setRefreshToken(token: String) = writeSync(KEY_REFRESH_TOKEN, token)

    fun isLoggedIn(): Boolean = getAccessToken() != null

    fun clearAuth() {
        runBlocking {
            dataStore.edit { prefs ->
                prefs.remove(KEY_ACCESS_TOKEN)
                prefs.remove(KEY_REFRESH_TOKEN)
            }
        }
    }

    fun logout() {
        runBlocking {
            dataStore.edit { prefs ->
                prefs.remove(KEY_ACCESS_TOKEN)
                prefs.remove(KEY_REFRESH_TOKEN)
                prefs.remove(KEY_USER_ID)
            }
        }
    }

    // --- Role ---

    fun getUserRole(): String = readSync(KEY_USER_ROLE, "user")

    fun setUserRole(role: String) = writeSync(KEY_USER_ROLE, role)

    // --- FCM ---

    fun saveFcmToken(token: String) = writeSync(KEY_FCM_TOKEN, token)

    fun getFcmToken(): String? = readSync(KEY_FCM_TOKEN, "").ifEmpty { null }

    // --- Settings ---

    fun saveSettings(
        autoApprove: Boolean,
        autoApproveThreshold: Int,
        dailyLimit: Int,
        notificationsEnabled: Boolean,
        soundEnabled: Boolean,
        vibrateEnabled: Boolean
    ) {
        runBlocking {
            dataStore.edit { prefs ->
                prefs[KEY_AUTO_APPROVE] = autoApprove
                prefs[KEY_AUTO_APPROVE_THRESHOLD] = autoApproveThreshold
                prefs[KEY_DAILY_LIMIT] = dailyLimit
                prefs[KEY_NOTIFICATIONS_ENABLED] = notificationsEnabled
                prefs[KEY_SOUND_ENABLED] = soundEnabled
                prefs[KEY_VIBRATE_ENABLED] = vibrateEnabled
            }
        }
    }

    fun getAutoApprove(): Boolean = readSync(KEY_AUTO_APPROVE, false)

    // --- Business / Billing ---

    fun getDailyLimitCount(): Int = readSync(KEY_DAILY_LIMIT, DEFAULT_DAILY_LIMIT)

    fun setDailyLimitCount(limit: Int) = writeSync(KEY_DAILY_LIMIT, limit)

    fun getPaidTierDailyLimit(): Int = readSync(KEY_PAID_TIER_DAILY_LIMIT, DEFAULT_PAID_TIER_DAILY_LIMIT)

    fun setPaidTierDailyLimit(limit: Int) = writeSync(KEY_PAID_TIER_DAILY_LIMIT, limit)

    fun getSubscriptionTier(): String = readSync(KEY_SUBSCRIPTION_TIER, DEFAULT_SUBSCRIPTION_TIER)

    fun setSubscriptionTier(tier: String) = writeSync(KEY_SUBSCRIPTION_TIER, tier)

    fun getCreditBalance(): Double = readSync(KEY_CREDIT_BALANCE, DEFAULT_CREDIT_BALANCE)

    fun setCreditBalance(balance: Double) = writeSync(KEY_CREDIT_BALANCE, balance)

    fun getSmsCost(): Double = readSync(KEY_SMS_COST, DEFAULT_SMS_COST)

    fun setSmsCost(cost: Double) = writeSync(KEY_SMS_COST, cost)

    fun getLmsCost(): Double = readSync(KEY_LMS_COST, DEFAULT_LMS_COST)

    fun setLmsCost(cost: Double) = writeSync(KEY_LMS_COST, cost)

    fun getMmsCost(): Double = readSync(KEY_MMS_COST, DEFAULT_MMS_COST)

    fun setMmsCost(cost: Double) = writeSync(KEY_MMS_COST, cost)

    // --- Engine ---

    fun getQueueThrottleMs(): Long = readSync(KEY_QUEUE_THROTTLE_MS, DEFAULT_QUEUE_THROTTLE_MS)

    fun setQueueThrottleMs(ms: Long) = writeSync(KEY_QUEUE_THROTTLE_MS, ms)

    fun getLimitMode(): String = readSync(KEY_LIMIT_MODE, DEFAULT_LIMIT_MODE)

    fun setLimitMode(mode: String) = writeSync(KEY_LIMIT_MODE, mode)

    // --- AI ---

    fun getDeepSeekApiKey(): String = readSync(KEY_DEEPSEEK_API_KEY, "")

    fun setDeepSeekApiKey(key: String) = writeSync(KEY_DEEPSEEK_API_KEY, key)

    fun getAiEnabled(): Boolean = readSync(KEY_AI_ENABLED, true)

    fun setAiEnabled(enabled: Boolean) = writeSync(KEY_AI_ENABLED, enabled)

    fun getAiSystemPrompt(): String = readSync(KEY_AI_SYSTEM_PROMPT, "")

    fun setAiSystemPrompt(prompt: String) = writeSync(KEY_AI_SYSTEM_PROMPT, prompt)

    /**
     * AI 일일 사용량 체크 및 증가
     * 무료: 10건/일, Business(paid): 500건/일, Premium: 무제한
     */
    fun getAiDailyLimit(): Int {
        return when (getSubscriptionTier()) {
            "premium" -> Int.MAX_VALUE  // 무제한
            "paid" -> 500
            else -> 10
        }
    }

    fun checkAndIncrementAiUsage(): Boolean {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA).format(java.util.Date())
        val savedDate = readSync(KEY_AI_DAILY_DATE, "")
        var count = if (savedDate == today) readSync(KEY_AI_DAILY_COUNT, 0) else 0

        val limit = getAiDailyLimit()
        if (count >= limit) return false

        count++
        writeSync(KEY_AI_DAILY_COUNT, count)
        writeSync(KEY_AI_DAILY_DATE, today)
        return true
    }

    fun getAiUsageToday(): Int {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA).format(java.util.Date())
        val savedDate = readSync(KEY_AI_DAILY_DATE, "")
        return if (savedDate == today) readSync(KEY_AI_DAILY_COUNT, 0) else 0
    }

    /**
     * 비즈니스 기능 접근 가능 여부
     * 무료: 비즈니스 기능 제한
     */
    fun isBusinessFeatureAvailable(): Boolean {
        val tier = getSubscriptionTier()
        return tier == "paid" || tier == "premium"
    }

    // --- Spam ---

    fun getOverseasBlockEnabled(): Boolean = readSync(KEY_OVERSEAS_BLOCK_ENABLED, false)

    fun setOverseasBlockEnabled(enabled: Boolean) = writeSync(KEY_OVERSEAS_BLOCK_ENABLED, enabled)

    fun getApickApiKey(): String = readSync(KEY_APICK_API_KEY, "")

    fun setApickApiKey(key: String) = writeSync(KEY_APICK_API_KEY, key)

    fun getIpqsApiKey(): String = readSync(KEY_IPQS_API_KEY, "")

    fun setIpqsApiKey(key: String) = writeSync(KEY_IPQS_API_KEY, key)

    fun getSpamApiEnabled(): Boolean = readSync(KEY_SPAM_API_ENABLED, false)

    fun setSpamApiEnabled(enabled: Boolean) = writeSync(KEY_SPAM_API_ENABLED, enabled)

    // --- SMS Sync Consent ---

    fun isSmsSyncConsented(): Boolean = readSync(KEY_SMS_SYNC_CONSENTED, false)

    fun setSmsSyncConsented(consented: Boolean) = writeSync(KEY_SMS_SYNC_CONSENTED, consented)
}
