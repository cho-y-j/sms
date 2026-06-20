package com.bizconnect.v2.util

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony

/**
 * Single source of truth for default-SMS-app state and the role request.
 *
 * The app MUST be the user's default SMS app for the core flows to work:
 *  - receiving via SMS_DELIVER and writing incoming messages to the system provider,
 *  - sending the auto-callback (business card) from the user's own phone,
 *  - and, per Google Play's SMS permissions policy, to legitimately hold
 *    SEND/READ/RECEIVE_SMS at all.
 *
 * Every request path (first-launch onboarding, the conversation-list banner, and
 * the settings entry) delegates here so the logic never drifts.
 */
object DefaultSmsApp {

    fun isDefault(context: Context): Boolean =
        try {
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
        } catch (_: Exception) {
            false
        }

    /**
     * Intent that asks the user to make this app the default SMS app, or null when
     * the request cannot/should not be made (role unavailable, or already held on Q+).
     * Callers launch it with an ActivityResult launcher and re-check [isDefault] after.
     */
    fun createRequestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) return null
            if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) return null
            return roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
        }
        return Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }
    }
}
