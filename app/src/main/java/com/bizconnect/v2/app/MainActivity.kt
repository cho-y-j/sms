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
import com.bizconnect.v2.data.sync.SmsSyncManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    @Inject
    lateinit var smsSyncManager: SmsSyncManager

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
                if (now - lastSyncTime < 3000) return // 3초 디바운스
                lastSyncTime = now
                Log.d("MainActivity", "Content change detected → syncing")
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        smsSyncManager.performIncrementalSync()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Sync error", e)
                    }
                }
            }
        }

        // Watch both SMS and MMS changes
        contentResolver.registerContentObserver(
            Uri.parse("content://mms-sms/"),
            true,
            contentObserver!!
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        contentObserver?.let { contentResolver.unregisterContentObserver(it) }
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
