package com.bizconnect.v2.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bizconnect.v2.domain.engine.ApprovalManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver for SMS approval notification action buttons.
 * Handles Approve and Cancel actions from notification UI.
 */
@AndroidEntryPoint
class ApprovalActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ApprovalActionReceiver"
        private const val ACTION_APPROVE = "com.bizconnect.v2.action.APPROVE_SMS"
        private const val ACTION_CANCEL = "com.bizconnect.v2.action.CANCEL_SMS"
        private const val EXTRA_TASK_ID = "task_id"
        private const val EXTRA_TASK_IDS = "task_ids"
    }

    @Inject
    lateinit var approvalManager: ApprovalManager

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.w(TAG, "Received null context or intent")
            return
        }

        Log.d(TAG, "Received action: ${intent.action}")

        when (intent.action) {
            ACTION_APPROVE -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                val taskIds = intent.getStringArrayExtra(EXTRA_TASK_IDS)

                if (taskId != null) {
                    handleApprovalSingle(taskId)
                } else if (taskIds != null) {
                    handleApprovalBatch(taskIds.toList())
                } else {
                    Log.w(TAG, "No task ID or IDs found in approval intent")
                }
            }

            ACTION_CANCEL -> {
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                val taskIds = intent.getStringArrayExtra(EXTRA_TASK_IDS)

                if (taskId != null) {
                    handleCancellationSingle(taskId)
                } else if (taskIds != null) {
                    handleCancellationBatch(taskIds.toList())
                } else {
                    Log.w(TAG, "No task ID or IDs found in cancel intent")
                }
            }

            else -> Log.w(TAG, "Unknown action: ${intent.action}")
        }
    }

    private fun handleApprovalSingle(taskId: String) {
        Log.d(TAG, "Approving single task: $taskId")
        scope.launch {
            try {
                approvalManager.handleApproval(taskId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle approval for single task", e)
            }
        }
    }

    private fun handleApprovalBatch(taskIds: List<String>) {
        Log.d(TAG, "Approving batch of ${taskIds.size} tasks")
        scope.launch {
            try {
                approvalManager.handleBatchApproval(taskIds)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle batch approval", e)
            }
        }
    }

    private fun handleCancellationSingle(taskId: String) {
        Log.d(TAG, "Cancelling single task: $taskId")
        scope.launch {
            try {
                approvalManager.handleCancellation(taskId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle cancellation for single task", e)
            }
        }
    }

    private fun handleCancellationBatch(taskIds: List<String>) {
        Log.d(TAG, "Cancelling batch of ${taskIds.size} tasks")
        scope.launch {
            try {
                for (taskId in taskIds) {
                    approvalManager.handleCancellation(taskId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle batch cancellation", e)
            }
        }
    }
}
