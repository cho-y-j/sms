package com.bizconnect.v2.ui.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SendHistoryItem(
    val recipient: String,
    val messagePreview: String,
    val date: String,
    val method: String,   // "phone" or "paid"
    val status: String,   // "success", "failed", "pending"
    val cost: Int = 0,
    val timestamp: Long = 0L
)

@HiltViewModel
class SendHistoryViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val appPreferences: AppPreferences
) : ViewModel() {

    val allHistory = mutableStateListOf<SendHistoryItem>()
    var isLoading by mutableStateOf(true)
        private set

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            val results = mutableListOf<SendHistoryItem>()

            // 1. Load phone sent SMS from device content provider
            try {
                val phoneSent = loadPhoneSentSms()
                results.addAll(phoneSent)
            } catch (e: Exception) {
                Log.e("SendHistory", "Failed to load phone SMS history", e)
            }

            // 2. Load paid/web sent history from server API
            try {
                val paidSent = loadPaidSentHistory()
                results.addAll(paidSent)
            } catch (e: Exception) {
                Log.e("SendHistory", "Failed to load paid send history", e)
            }

            // Sort by timestamp descending (newest first)
            results.sortByDescending { it.timestamp }

            allHistory.clear()
            allHistory.addAll(results)
            isLoading = false
        }
    }

    private fun loadPhoneSentSms(): List<SendHistoryItem> {
        val items = mutableListOf<SendHistoryItem>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
        val contentResolver: ContentResolver = appContext.contentResolver

        try {
            val cursor: Cursor? = contentResolver.query(
                Telephony.Sms.Sent.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.STATUS
                ),
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT 200"
            )

            cursor?.use {
                val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                val statusIdx = it.getColumnIndex(Telephony.Sms.STATUS)

                while (it.moveToNext()) {
                    val address = it.getString(addressIdx) ?: "알 수 없음"
                    val body = it.getString(bodyIdx) ?: ""
                    val timestamp = it.getLong(dateIdx)
                    val statusCode = it.getInt(statusIdx)

                    val status = when (statusCode) {
                        Telephony.Sms.STATUS_COMPLETE -> "success"
                        Telephony.Sms.STATUS_FAILED -> "failed"
                        Telephony.Sms.STATUS_PENDING -> "pending"
                        else -> "success" // STATUS_NONE (-1) typically means sent OK
                    }

                    items.add(
                        SendHistoryItem(
                            recipient = address,
                            messagePreview = body.take(80),
                            date = dateFormat.format(Date(timestamp)),
                            method = "phone",
                            status = status,
                            cost = 0,
                            timestamp = timestamp
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("SendHistory", "Error reading sent SMS", e)
        }

        return items
    }

    private fun loadPaidSentHistory(): List<SendHistoryItem> {
        val items = mutableListOf<SendHistoryItem>()
        val token = appPreferences.getAccessToken() ?: return items

        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://sm.on1.kr/api/user/sms/history")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            response.close()

            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return items

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)

            for (i in 0 until data.length()) {
                val record = data.getJSONObject(i)
                val recipient = record.optString("phone", record.optString("recipient", "알 수 없음"))
                val name = record.optString("name", "")
                val message = record.optString("message", "")
                val createdAt = record.optString("createdAt", "0")
                val timestamp = try { createdAt.toLong() } catch (_: Exception) { 0L }
                val status = record.optString("status", "sent")
                val method = record.optString("method", "paid")
                val displayName = if (name.isNotBlank()) "$name ($recipient)" else recipient

                items.add(
                    SendHistoryItem(
                        recipient = displayName,
                        messagePreview = message.take(80),
                        date = if (timestamp > 0) dateFormat.format(Date(timestamp)) else "",
                        method = method,
                        status = if (status == "sent") "success" else status,
                        cost = 0,
                        timestamp = timestamp
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("SendHistory", "Error loading paid history", e)
        }

        return items
    }

    fun refresh() {
        loadHistory()
    }
}
