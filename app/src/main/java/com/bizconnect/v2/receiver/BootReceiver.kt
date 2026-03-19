package com.bizconnect.v2.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bizconnect.v2.service.CallbackService
import com.bizconnect.v2.service.SyncService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Boot completed, starting services")

        try {
            // Restart scheduled services and alarms
            Intent(context, SyncService::class.java).apply {
                context.startService(this)
            }

            Intent(context, CallbackService::class.java).apply {
                context.startService(this)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting services on boot", e)
        }
    }

    companion object {
        const val TAG = "BootReceiver"
    }
}
