package com.bizconnect.v2.app

import android.app.role.RoleManager
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.provider.Telephony
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import com.bizconnect.v2.ui.navigation.MainScreen
import com.bizconnect.v2.ui.theme.BizConnectTheme
import com.bizconnect.v2.data.remote.TokenManager
import com.bizconnect.v2.data.sync.SmsSyncManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    @Inject
    lateinit var smsSyncManager: SmsSyncManager

    @Inject
    lateinit var appPreferences: com.bizconnect.v2.data.preferences.AppPreferences

    @Inject
    lateinit var messageTemplateDao: com.bizconnect.v2.data.local.db.dao.MessageTemplateDao

    @Inject
    lateinit var tokenManager: TokenManager

    private var isDefaultSmsApp by mutableStateOf(false)
    private var contentObserver: ContentObserver? = null

    private val defaultSmsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkDefaultSmsApp()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge for proper IME padding
        WindowCompat.setDecorFitsSystemWindows(window, false)

        checkDefaultSmsApp()
        registerMmsContentObserver()
        uploadFcmTokenOnStart()
        fetchConfigOnStart()
        // Only sync if user has explicitly consented (Google Play privacy policy)
        if (appPreferences.isSmsSyncConsented()) {
            syncSmsHistoryToServer()
        }
        // Process any stuck pending messages from web
        processPendingWebMessages()

        setContent {
            val fontScale by dataStore.data
                .map { it[floatPreferencesKey("font_scale")] ?: 1.0f }
                .collectAsState(initial = 1.0f)

            // null = follow system, true = force dark, false is treated as follow system
            val isDarkMode by dataStore.data
                .map { prefs ->
                    val value = prefs[booleanPreferencesKey("dark_mode")]
                    if (value == true) true else null // null = follow system
                }
                .collectAsState(initial = null)

            val density = LocalDensity.current

            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale)
            ) {
                BizConnectTheme(forceDarkMode = isDarkMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {
                        MainScreen()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkDefaultSmsApp()
    }

    private fun checkDefaultSmsApp() {
        isDefaultSmsApp = Telephony.Sms.getDefaultSmsPackage(this) == packageName
    }

    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            try {
                startActivity(intent)
            } catch (_: Exception) { }
        }
    }

    /**
     * Register ContentObserver for SMS+MMS changes.
     * Triggers incremental sync when new messages arrive in system provider.
     */
    private fun registerMmsContentObserver() {
        val handler = Handler(Looper.getMainLooper())
        var lastSyncTime = 0L

        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                val now = System.currentTimeMillis()
                if (now - lastSyncTime < 1000) return // 1초 디바운스 (SmsSender가 Room에 systemSmsId 포함 저장하므로 레이스 컨디션 해결됨)
                lastSyncTime = now
                Log.d("MainActivity", "Content change detected → syncing")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // 기본 앱이 아니면 약간 대기 (삼성 메시지가 먼저 저장하도록)
                        val isDefault = android.provider.Telephony.Sms.getDefaultSmsPackage(applicationContext) == packageName
                        if (!isDefault) {
                            kotlinx.coroutines.delay(500) // 0.5초 대기
                        }
                        smsSyncManager.performIncrementalSync()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Sync error", e)
                    }
                }
            }
        }

        // Watch SMS, MMS, and RCS changes
        contentResolver.registerContentObserver(
            Uri.parse("content://mms-sms/"),
            true,
            contentObserver ?: return
        )
        // 삼성 RCS 메시지 감시
        try {
            contentResolver.registerContentObserver(
                Uri.parse("content://im/chat"),
                true,
                contentObserver ?: return
            )
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentObserver?.let { contentResolver.unregisterContentObserver(it) }
    }

    /**
     * 서버에서 AI API 키 등 설정을 받아와 로컬에 저장
     * Uses TokenManager for automatic 401 retry with token refresh.
     */
    private fun fetchConfigOnStart() {
        val accessToken = appPreferences.getAccessToken()
        if (accessToken.isNullOrBlank() || accessToken.startsWith("offline_")) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. config에서 AI 키 가져오기
                val configReqBuilder = okhttp3.Request.Builder()
                    .url("https://sm.on1.kr/api/admin/config")
                    .get()
                val configResp = tokenManager.executeAuthenticated(configReqBuilder)
                val configBody = configResp.body?.string() ?: "[]"
                configResp.close()
                try {
                    val arr = org.json.JSONArray(configBody)
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        if (item.optString("key") == "deepseek_api_key") {
                            val key = item.optString("value", "")
                            if (key.isNotBlank()) appPreferences.setDeepSeekApiKey(key)
                        }
                    }
                } catch (_: Exception) { }

                // 2. user/me에서 role, tier 가져오기
                val meReqBuilder = okhttp3.Request.Builder()
                    .url("https://sm.on1.kr/api/user/me")
                    .get()
                val meResp = tokenManager.executeAuthenticated(meReqBuilder)
                val meBody = meResp.body?.string() ?: "{}"
                meResp.close()
                try {
                    val json = org.json.JSONObject(meBody)
                    appPreferences.setUserRole(json.optString("role", "user"))
                    appPreferences.setSubscriptionTier(json.optString("tier", "free"))
                } catch (_: Exception) { }

                android.util.Log.d("ConfigSync", "Config synced from server")
            } catch (e: Exception) {
                android.util.Log.e("ConfigSync", "Config sync failed", e)
            }
        }
    }

    private fun uploadFcmTokenOnStart() {
        android.util.Log.d("FCM_UPLOAD", "=== uploadFcmTokenOnStart called ===")
        val accessToken = appPreferences.getAccessToken()
        android.util.Log.d("FCM_UPLOAD", "accessToken: ${accessToken?.take(20) ?: "NULL"}")
        if (accessToken.isNullOrBlank() || accessToken.startsWith("offline_")) {
            android.util.Log.d("FCM_UPLOAD", "No valid token, skipping FCM upload")
            return
        }

        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        android.util.Log.e("FCM_UPLOAD", "FCM token fetch FAILED", task.exception)
                        return@addOnCompleteListener
                    }
                    val fcmToken = task.result
                    android.util.Log.d("FCM_UPLOAD", "FCM token obtained: ${fcmToken?.take(20)}...")

                    if (fcmToken.isNullOrBlank()) return@addOnCompleteListener

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val body = org.json.JSONObject().put("token", fcmToken).toString()
                            val requestBuilder = okhttp3.Request.Builder()
                                .url("https://sm.on1.kr/api/fcm/token")
                                .put(okhttp3.RequestBody.create(
                                    "application/json".toMediaType(), body))
                            val resp = tokenManager.executeAuthenticated(requestBuilder)
                            android.util.Log.d("FCM_UPLOAD", "Server response: ${resp.code}")
                            resp.close()
                        } catch (e: Exception) {
                            android.util.Log.e("FCM_UPLOAD", "Upload to server failed", e)
                        }
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("FCM_UPLOAD", "Firebase init error", e)
        }
    }

    /**
     * 폰 연락처를 서버에 동기화
     * Uses TokenManager for automatic 401 retry with token refresh.
     */
    private fun syncContactsToServer() {
        val accessToken = appPreferences.getAccessToken()
        if (accessToken.isNullOrBlank() || accessToken.startsWith("offline_")) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cursor = contentResolver.query(
                    android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                    ), null, null, null
                )

                val contacts = mutableListOf<org.json.JSONObject>()
                val seenPhones = mutableSetOf<String>()
                cursor?.use {
                    while (it.moveToNext()) {
                        val name = it.getString(0) ?: continue
                        val phone = it.getString(1)?.replace(Regex("[^0-9+]"), "") ?: continue
                        if (phone.length < 10 || seenPhones.contains(phone)) continue
                        seenPhones.add(phone)
                        contacts.add(org.json.JSONObject().apply {
                            put("name", name)
                            put("phone", phone)
                        })
                    }
                }

                if (contacts.isEmpty()) return@launch

                val payload = org.json.JSONObject()
                payload.put("contacts", org.json.JSONArray(contacts))

                val requestBuilder = okhttp3.Request.Builder()
                    .url("https://sm.on1.kr/api/user/contacts/import")
                    .post(okhttp3.RequestBody.create("application/json".toMediaType(), payload.toString()))
                val resp = tokenManager.executeAuthenticated(requestBuilder)
                android.util.Log.d("ContactSync", "Contacts sync result: ${resp.code}")
                resp.close()
            } catch (e: Exception) {
                android.util.Log.e("ContactSync", "Contacts sync failed", e)
            }
        }
    }

    /**
     * 폰 SMS 이력을 서버에 동기화 (최근 200건)
     * Uses TokenManager for automatic 401 retry with token refresh.
     */
    private fun syncSmsHistoryToServer() {
        val accessToken = appPreferences.getAccessToken()
        if (accessToken.isNullOrBlank() || accessToken.startsWith("offline_")) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 시스템 SMS 읽기
                val cursor = contentResolver.query(
                    android.provider.Telephony.Sms.CONTENT_URI,
                    arrayOf("_id", "thread_id", "address", "body", "date", "type"),
                    null, null, "date DESC LIMIT 200"
                )

                val messages = mutableListOf<org.json.JSONObject>()
                cursor?.use {
                    while (it.moveToNext()) {
                        val address = it.getString(2) ?: continue
                        val body = it.getString(3) ?: continue
                        val date = it.getLong(4)
                        val type = if (it.getInt(5) == 2) "sent" else "received"
                        val threadId = it.getLong(1)

                        messages.add(org.json.JSONObject().apply {
                            put("threadId", threadId)
                            put("recipientPhone", address)
                            put("body", body)
                            put("timestamp", date)
                            put("type", type)
                            put("isMms", false)
                        })
                    }
                }

                if (messages.isEmpty()) return@launch

                val payload = org.json.JSONObject()
                payload.put("messages", org.json.JSONArray(messages))

                val requestBuilder = okhttp3.Request.Builder()
                    .url("https://sm.on1.kr/api/user/sms-history/sync")
                    .post(okhttp3.RequestBody.create("application/json".toMediaType(), payload.toString()))
                val resp = tokenManager.executeAuthenticated(requestBuilder)
                android.util.Log.d("SmsSync", "SMS sync result: ${resp.code}")
                resp.close()
            } catch (e: Exception) {
                android.util.Log.e("SmsSync", "SMS sync failed", e)
            }
        }
    }

    /**
     * Uses TokenManager for automatic 401 retry with token refresh.
     */
    private fun processPendingWebMessages() {
        val accessToken = appPreferences.getAccessToken()
        if (accessToken.isNullOrBlank() || accessToken.startsWith("offline_")) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val requestBuilder = okhttp3.Request.Builder()
                    .url("https://sm.on1.kr/api/user/sms/pending")
                    .get()
                val resp = tokenManager.executeAuthenticated(requestBuilder)
                val body = resp.body?.string() ?: ""
                resp.close()

                val json = org.json.JSONObject(body)
                val data = json.optJSONArray("data") ?: return@launch
                val count = data.length()
                if (count == 0) return@launch

                Log.d("PendingSync", "Found $count pending messages, processing...")

                val smsManager = if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    applicationContext.getSystemService(android.telephony.SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    android.telephony.SmsManager.getDefault()
                }

                for (i in 0 until count) {
                    val msg = data.getJSONObject(i)
                    val msgId = msg.getString("id")
                    val phone = msg.getString("phone")
                    val msgBody = msg.getString("message")

                    if (i > 0) kotlinx.coroutines.delay(3000) // 3초 간격

                    try {
                        val parts = smsManager.divideMessage(msgBody)
                        if (parts.size == 1) smsManager.sendTextMessage(phone, null, msgBody, null, null)
                        else smsManager.sendMultipartTextMessage(phone, null, parts, null, null)

                        // Report success
                        val statusBody = org.json.JSONObject().put("status", "sent").toString()
                        val statusReqBuilder = okhttp3.Request.Builder()
                            .url("https://sm.on1.kr/api/user/sms/$msgId/status")
                            .put(okhttp3.RequestBody.create("application/json".toMediaType(), statusBody))
                        tokenManager.executeAuthenticated(statusReqBuilder).close()
                        Log.d("PendingSync", "Sent pending SMS $msgId to $phone")
                    } catch (e: Exception) {
                        val statusBody = org.json.JSONObject().put("status", "failed").put("error", e.message ?: "").toString()
                        val statusReqBuilder = okhttp3.Request.Builder()
                            .url("https://sm.on1.kr/api/user/sms/$msgId/status")
                            .put(okhttp3.RequestBody.create("application/json".toMediaType(), statusBody))
                        try { tokenManager.executeAuthenticated(statusReqBuilder).close() } catch (_: Exception) { }
                    }
                }
            } catch (e: Exception) {
                Log.e("PendingSync", "Failed to process pending messages", e)
            }
        }
    }

    /**
     * 로컬 템플릿을 서버에 동기화
     * Uses TokenManager for automatic 401 retry with token refresh.
     */
    private fun syncTemplatesToServer() {
        val accessToken = appPreferences.getAccessToken()
        if (accessToken.isNullOrBlank() || accessToken.startsWith("offline_")) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val templates = messageTemplateDao.getAllFlow().first()

                if (templates.isEmpty()) return@launch

                for (template in templates) {
                    try {
                        val body = org.json.JSONObject().apply {
                            put("title", template.name)
                            put("content", template.content)
                            put("category", template.category)
                        }.toString()

                        val requestBuilder = okhttp3.Request.Builder()
                            .url("https://sm.on1.kr/api/user/templates")
                            .post(okhttp3.RequestBody.create("application/json".toMediaType(), body))
                        tokenManager.executeAuthenticated(requestBuilder).close()
                    } catch (_: Exception) { }
                }
                Log.d("TemplateSync", "Synced ${templates.size} templates to server")
            } catch (e: Exception) {
                Log.e("TemplateSync", "Template sync failed", e)
            }
        }
    }

    fun requestDefaultSmsApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                defaultSmsLauncher.launch(intent)
            }
        } else {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            defaultSmsLauncher.launch(intent)
        }
    }
}
