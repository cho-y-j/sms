package com.bizconnect.v2.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bizconnect.v2.ui.auth.LoginScreen
import com.bizconnect.v2.ui.auth.SignupScreen
import com.bizconnect.v2.ui.business.AiSettingsScreen
import com.bizconnect.v2.ui.business.BusinessHomeScreen
import com.bizconnect.v2.ui.business.BulkSendScreen
import com.bizconnect.v2.ui.business.CallbackSettingsScreen
import com.bizconnect.v2.ui.business.CustomerDetailScreen
import com.bizconnect.v2.ui.business.CustomerManagementScreen
import com.bizconnect.v2.ui.business.MessageTemplateEditScreen
import com.bizconnect.v2.ui.business.MessageTemplateScreen
import com.bizconnect.v2.ui.business.PricingScreen
import com.bizconnect.v2.ui.business.ScheduleCreateScreen
import com.bizconnect.v2.ui.business.ScheduledMessagesScreen
import com.bizconnect.v2.ui.business.SpamManagementScreen
import com.bizconnect.v2.ui.compose.ComposeMessageScreen
import com.bizconnect.v2.ui.contacts.ContactsScreen
import com.bizconnect.v2.ui.conversation.ConversationListScreen
import com.bizconnect.v2.ui.message.MessageDetailScreen
import com.bizconnect.v2.ui.settings.AdminSettingsScreen
import com.bizconnect.v2.ui.settings.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val navController = rememberNavController()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var permissionsGranted by remember { mutableStateOf(false) }

    val requiredPermissions = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_PHONE_STATE
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    // Check & request permissions
    LaunchedEffect(Unit) {
        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            permissionsGranted = true
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    val tabRoutes = listOf("conversations", "contacts", "business")

    val navItems = listOf(
        NavItem("대화", Icons.Default.Forum),
        NavItem("연락처", Icons.Default.Person),
        NavItem("비즈니스", Icons.Default.BusinessCenter)
    )

    Scaffold(
        // No Scaffold topBar - handled inside each screen
        bottomBar = {
            val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
            val showBottomBar = currentRoute == null || currentRoute in tabRoutes
            if (showBottomBar) {
                BottomNavBar(
                    items = navItems,
                    selectedIndex = selectedTabIndex,
                    onItemSelected = { index ->
                        selectedTabIndex = index
                        val route = tabRoutes[index]
                        navController.navigate(route) {
                            popUpTo("conversations") {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "conversations",
            modifier = Modifier.padding(paddingValues)
        ) {
            // Main tabs
            composable("conversations") {
                selectedTabIndex = 0
                ConversationListScreen(
                    permissionsGranted = permissionsGranted,
                    onSettingsClick = {
                        navController.navigate("settings") { launchSingleTop = true }
                    },
                    onConversationClick = { threadId ->
                        android.util.Log.d("MainScreen", "Navigate to message_detail/$threadId")
                        navController.navigate("message_detail/$threadId")
                    },
                    onNewMessageClick = {
                        navController.navigate("compose_message")
                    }
                )
            }

            composable("contacts") {
                selectedTabIndex = 1
                ContactsScreen(
                    onContactClick = { contactId ->
                        // TODO: Navigate to contact detail
                    },
                    onCallClick = { phone ->
                        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                            data = android.net.Uri.parse("tel:$phone")
                        }
                        context.startActivity(intent)
                    },
                    onMessageClick = { phone ->
                        // Find existing thread for this phone number
                        val threadId = try {
                            android.provider.Telephony.Threads.getOrCreateThreadId(context, phone)
                        } catch (_: Exception) { 0L }
                        if (threadId > 0) {
                            navController.navigate("message_detail/$threadId")
                        } else {
                            navController.navigate("compose_message?phone=$phone")
                        }
                    }
                )
            }

            composable("business") {
                selectedTabIndex = 2
                BusinessHomeScreen(
                    onBulkSendClick = { navController.navigate("bulk_send") },
                    onScheduledMessagesClick = { navController.navigate("scheduled_messages") },
                    onCallbackSettingsClick = { navController.navigate("callback_settings") },
                    onCustomerManagementClick = { navController.navigate("customer_management") },
                    onSpamManagementClick = { navController.navigate("spam_management") },
                    onMessageTemplateClick = { navController.navigate("message_templates") },
                    onAiSettingsClick = { navController.navigate("ai_settings") },
                    onSettingsClick = { navController.navigate("settings") },
                    onPricingClick = { navController.navigate("pricing") },
                    onLoginClick = { navController.navigate("login") }
                )
            }

            // Detail screens
            composable(
                route = "message_detail/{threadId}",
                arguments = listOf(navArgument("threadId") { type = NavType.LongType })
            ) { backStackEntry ->
                val threadId = backStackEntry.arguments?.getLong("threadId") ?: 0L
                MessageDetailScreen(
                    threadId = threadId,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = "compose_message?phone={phone}",
                arguments = listOf(navArgument("phone") {
                    type = NavType.StringType
                    defaultValue = ""
                })
            ) { backStackEntry ->
                val phone = backStackEntry.arguments?.getString("phone") ?: ""
                ComposeMessageScreen(
                    onBackClick = { navController.popBackStack() },
                    onNavigateToThread = { threadId ->
                        navController.popBackStack()
                        navController.navigate("message_detail/$threadId")
                    },
                    initialPhone = phone.ifEmpty { null }
                )
            }

            composable("settings") {
                SettingsScreen(
                    onBackClick = { navController.popBackStack() },
                    onSpamClick = { navController.navigate("spam_management") },
                    onAdminClick = { navController.navigate("admin_settings") }
                )
            }

            composable("admin_settings") {
                AdminSettingsScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            // Business sub-screens
            composable("bulk_send") {
                BulkSendScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable("scheduled_messages") {
                ScheduledMessagesScreen(
                    onBackClick = { navController.popBackStack() },
                    onNewScheduleClick = { navController.navigate("schedule_create") },
                    onMessageClick = { id -> navController.navigate("schedule_edit/$id") }
                )
            }

            composable("schedule_create") {
                ScheduleCreateScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = "schedule_edit/{scheduleId}",
                arguments = listOf(navArgument("scheduleId") { type = NavType.StringType })
            ) { backStackEntry ->
                val scheduleId = backStackEntry.arguments?.getString("scheduleId") ?: ""
                ScheduleCreateScreen(
                    scheduleId = scheduleId,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable("callback_settings") {
                CallbackSettingsScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable("customer_management") {
                CustomerManagementScreen(
                    onBackClick = { navController.popBackStack() },
                    onContactClick = { phone ->
                        navController.navigate("customer_detail/${android.net.Uri.encode(phone)}")
                    }
                )
            }

            composable(
                route = "customer_detail/{phone}",
                arguments = listOf(navArgument("phone") { type = NavType.StringType })
            ) {
                val phone = it.arguments?.getString("phone") ?: ""
                CustomerDetailScreen(
                    phone = phone,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable("spam_management") {
                SpamManagementScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable("pricing") {
                PricingScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable("ai_settings") {
                AiSettingsScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }

            // Auth screens
            composable("login") {
                LoginScreen(
                    onBackClick = { navController.popBackStack() },
                    onSignupClick = { navController.navigate("signup") },
                    onLoginSuccess = { navController.popBackStack() }
                )
            }

            composable("signup") {
                SignupScreen(
                    onBackClick = { navController.popBackStack() },
                    onSignupSuccess = {
                        navController.popBackStack("login", inclusive = true)
                    }
                )
            }

            composable("message_templates") {
                MessageTemplateScreen(
                    onBackClick = { navController.popBackStack() },
                    onCreateClick = { navController.navigate("message_template_edit") },
                    onEditClick = { id -> navController.navigate("message_template_edit/$id") }
                )
            }

            composable("message_template_edit") {
                MessageTemplateEditScreen(
                    templateId = null,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = "message_template_edit/{templateId}",
                arguments = listOf(navArgument("templateId") { type = NavType.LongType })
            ) { backStackEntry ->
                val templateId = backStackEntry.arguments?.getLong("templateId") ?: 0L
                MessageTemplateEditScreen(
                    templateId = templateId,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
