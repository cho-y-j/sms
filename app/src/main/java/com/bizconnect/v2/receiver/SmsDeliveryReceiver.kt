package com.bizconnect.v2.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SmsDeliveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (resultCode) {
            Activity.RESULT_OK -> Log.d("SmsDelivery", "SMS delivered successfully")
            else -> Log.w("SmsDelivery", "SMS delivery failed: $resultCode")
        }
    }
}
