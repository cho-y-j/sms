package com.bizconnect.v2.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.local.db.dao.ScheduledMessageDao
import com.bizconnect.v2.data.local.db.entity.ScheduledMessageEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScheduledMessagesViewModel @Inject constructor(
    private val scheduledMessageDao: ScheduledMessageDao
) : ViewModel() {

    val scheduledMessages: StateFlow<List<ScheduledMessageEntity>> =
        scheduledMessageDao.getAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteSchedule(id: String) {
        viewModelScope.launch {
            scheduledMessageDao.deleteById(id)
        }
    }

    fun toggleActive(id: String, isActive: Boolean) {
        viewModelScope.launch {
            if (isActive) {
                scheduledMessageDao.activate(id)
            } else {
                scheduledMessageDao.deactivate(id)
            }
        }
    }
}
