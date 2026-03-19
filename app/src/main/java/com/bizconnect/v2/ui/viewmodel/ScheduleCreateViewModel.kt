package com.bizconnect.v2.ui.viewmodel

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.local.db.dao.ScheduledMessageDao
import com.bizconnect.v2.domain.ai.AiAssistant
import com.bizconnect.v2.data.local.db.entity.ScheduledMessageEntity
import com.bizconnect.v2.receiver.AlarmReceiver
import com.bizconnect.v2.ui.business.Recipient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

data class ScheduleCreateUiState(
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val recipients: List<Recipient> = emptyList(),
    val message: String = "",
    val imageUri: String? = null,
    val scheduledAt: Long = System.currentTimeMillis() + 3600_000, // 1 hour from now
    val repeatType: String = "none",
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ScheduleCreateViewModel @Inject constructor(
    private val scheduledMessageDao: ScheduledMessageDao,
    @ApplicationContext private val context: Context,
    private val aiAssistant: AiAssistant,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val scheduleId: String? = savedStateHandle["scheduleId"]

    private val _uiState = MutableStateFlow(ScheduleCreateUiState())
    val uiState: StateFlow<ScheduleCreateUiState> = _uiState

    init {
        if (scheduleId != null) {
            loadExistingSchedule(scheduleId)
        }
    }

    private fun loadExistingSchedule(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val entity = scheduledMessageDao.getById(id)
                if (entity != null) {
                    // Parse existing comma-separated values into Recipient list
                    val phones = entity.recipients.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val names = (entity.recipientNames ?: "").split(",").map { it.trim() }
                    val recipientList = phones.mapIndexed { index, phone ->
                        Recipient(
                            name = names.getOrElse(index) { phone },
                            phone = phone
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isEditing = true,
                        recipients = recipientList,
                        message = entity.message,
                        imageUri = entity.localImagePath,
                        scheduledAt = entity.scheduledAt,
                        repeatType = entity.repeatType
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "예약 메시지를 찾을 수 없습니다"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun setRecipients(value: List<Recipient>) {
        _uiState.value = _uiState.value.copy(recipients = value)
    }

    fun updateMessage(value: String) {
        _uiState.value = _uiState.value.copy(message = value)
    }

    fun updateScheduledAt(value: Long) {
        _uiState.value = _uiState.value.copy(scheduledAt = value)
    }

    fun updateRepeatType(value: String) {
        _uiState.value = _uiState.value.copy(repeatType = value)
    }

    fun updateImageUri(value: String?) {
        _uiState.value = _uiState.value.copy(imageUri = value)
    }

    suspend fun generateAiMessage(prompt: String): String {
        return aiAssistant.generateMessage(prompt)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun save() {
        val state = _uiState.value
        if (state.recipients.isEmpty()) {
            _uiState.value = state.copy(error = "수신자를 선택해주세요")
            return
        }
        if (state.message.isBlank()) {
            _uiState.value = state.copy(error = "메시지를 입력해주세요")
            return
        }
        if (state.scheduledAt <= System.currentTimeMillis()) {
            _uiState.value = state.copy(error = "예약 시간은 현재 시간 이후여야 합니다")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val id = scheduleId ?: UUID.randomUUID().toString()
                val recipientsStr = state.recipients.joinToString(",") { it.phone }
                val recipientNamesStr = state.recipients.joinToString(",") { it.name }

                val entity = ScheduledMessageEntity(
                    id = id,
                    userId = "local",
                    recipients = recipientsStr,
                    recipientNames = recipientNamesStr.ifBlank { null },
                    message = state.message,
                    localImagePath = state.imageUri,
                    isMms = state.imageUri != null,
                    scheduledAt = state.scheduledAt,
                    repeatType = state.repeatType,
                    isActive = true
                )

                if (scheduleId != null) {
                    scheduledMessageDao.update(entity)
                } else {
                    scheduledMessageDao.insert(entity)
                }

                // Set alarm
                scheduleAlarm(entity)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSaved = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save schedule", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "저장 실패: ${e.message}"
                )
            }
        }
    }

    private fun scheduleAlarm(entity: ScheduledMessageEntity) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("schedule_id", entity.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            entity.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entity.scheduledAt, pendingIntent)
            } else {
                // Fallback to inexact alarm
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entity.scheduledAt, pendingIntent)
                Log.w(TAG, "Exact alarm not permitted, using inexact alarm")
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entity.scheduledAt, pendingIntent)
        }
        Log.d(TAG, "Alarm set for schedule ${entity.id} at ${entity.scheduledAt}")
    }

    companion object {
        private const val TAG = "ScheduleCreateVM"
    }
}
