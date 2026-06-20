package com.bizconnect.v2.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Helper for the battery-optimization exemption that keeps the background
 * auto-callback alive.
 *
 * The callback runs from a manifest PHONE_STATE receiver after a call ends.
 * Aggressive OEM battery managers (Samsung/Xiaomi/Oppo/Vivo/Huawei) will kill
 * such background work unless the app is exempt from battery optimization — this
 * is the #1 reason auto-callback fails on "some phones". The request is
 * user-initiated (shown when the user turns auto-callback on), which is the
 * acceptable pattern for a messaging/communication app.
 */
object BatteryOptimization {

    fun isIgnoring(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Intent for the system "allow to run in background" dialog, or null if the
     * app is already exempt. Launch it with an ActivityResult launcher.
     */
    @SuppressLint("BatteryLife")
    fun createRequestIntent(context: Context): Intent? {
        if (isIgnoring(context)) return null
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }
}
