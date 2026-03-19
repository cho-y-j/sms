package com.bizconnect.v2.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bizconnect.v2.ui.business.BulkSendScreen
import com.bizconnect.v2.ui.business.BusinessHomeScreen
import com.bizconnect.v2.ui.business.CallbackSettingsScreen
import com.bizconnect.v2.ui.business.CustomerManagementScreen
import com.bizconnect.v2.ui.business.ScheduleCreateScreen
import com.bizconnect.v2.ui.business.ScheduledMessagesScreen
import com.bizconnect.v2.ui.business.SpamManagementScreen
import com.bizconnect.v2.ui.compose.ComposeMessageScreen
import com.bizconnect.v2.ui.contacts.ContactsScreen
import com.bizconnect.v2.ui.conversation.ConversationListScreen
import com.bizconnect.v2.ui.message.MessageDetailScreen
import com.bizconnect.v2.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    // Conversation tab
    object ConversationList : Screen("conversation_list")
    object MessageDetail : Screen("message_detail/{threadId}")
    object ComposeMessage : Screen("compose_message")

    // Contacts tab
    object ContactsList : Screen("contacts_list")

    // Business tab
    object BusinessHome : Screen("business_home")
    object BulkSend : Screen("bulk_send")
    object ScheduledMessages : Screen("scheduled_messages")
    object CallbackSettings : Screen("callback_settings")
    object CustomerManagement : Screen("customer_management")
    object SpamManagement : Screen("spam_management")

    // Settings
    object Settings : Screen("settings")
}

/**
 * Standalone navigation graph. The primary navigation is now handled by MainScreen.
 * This is kept for compatibility but MainScreen.kt is the main entry point.
 */
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.ConversationList.route
    ) {
        // Conversation screens
        composable(Screen.ConversationList.route) {
            ConversationListScreen(
                onConversationClick = { threadId ->
                    navController.navigate("message_detail/$threadId")
                },
                onNewMessageClick = {
                    navController.navigate(Screen.ComposeMessage.route)
                }
            )
        }

        composable(
            route = Screen.MessageDetail.route,
            arguments = listOf(navArgument("threadId") { type = NavType.LongType })
        ) { backStackEntry ->
            val threadId = backStackEntry.arguments?.getLong("threadId") ?: 0L
            MessageDetailScreen(
                threadId = threadId,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.ComposeMessage.route) {
            ComposeMessageScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // Contacts screens
        composable(Screen.ContactsList.route) {
            ContactsScreen(
                onContactClick = { contactId ->
                    // TODO: Navigate to contact detail
                }
            )
        }

        // Business screens
        composable(Screen.BusinessHome.route) {
            BusinessHomeScreen(
                onBulkSendClick = {
                    navController.navigate(Screen.BulkSend.route)
                },
                onScheduledMessagesClick = {
                    navController.navigate(Screen.ScheduledMessages.route)
                },
                onCallbackSettingsClick = {
                    navController.navigate(Screen.CallbackSettings.route)
                },
                onCustomerManagementClick = {
                    navController.navigate(Screen.CustomerManagement.route)
                },
                onSpamManagementClick = {
                    navController.navigate(Screen.SpamManagement.route)
                }
            )
        }

        composable(Screen.BulkSend.route) {
            BulkSendScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.ScheduledMessages.route) {
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

        composable(Screen.CallbackSettings.route) {
            CallbackSettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.CustomerManagement.route) {
            CustomerManagementScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.SpamManagement.route) {
            SpamManagementScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        // Settings
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
