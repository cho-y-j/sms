package com.bizconnect.v2.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PermissionUtil @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun hasSmsPermissions(): Boolean {
        return hasPermission(Manifest.permission.SEND_SMS) &&
                hasPermission(Manifest.permission.RECEIVE_SMS) &&
                hasPermission(Manifest.permission.READ_SMS)
    }

    fun hasCallPermissions(): Boolean {
        return hasPermission(Manifest.permission.READ_PHONE_STATE) &&
                hasPermission(Manifest.permission.READ_CALL_LOG)
    }

    fun hasContactPermissions(): Boolean {
        return hasPermission(Manifest.permission.READ_CONTACTS)
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }

    fun getRequiredSmsPermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_MMS,
            Manifest.permission.READ_PHONE_STATE
        )
    }

    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_MMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.INTERNET,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.VIBRATE,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissions.toTypedArray()
    }
}
