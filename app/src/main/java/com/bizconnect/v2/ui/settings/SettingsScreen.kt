package com.bizconnect.v2.ui.settings

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bizconnect.v2.ui.theme.SamsungBlue
import com.bizconnect.v2.ui.theme.SamsungRed
import com.bizconnect.v2.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit = {},
    onSpamClick: () -> Unit = {},
    onAdminClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val isDefaultSmsApp = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    val roleRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // === 계정 상태 ===
            val isLoggedIn = viewModel.isLoggedIn()
            val userName = viewModel.getUserName()
            val userTier = viewModel.getUserTier()

            if (isLoggedIn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            SamsungBlue.copy(alpha = 0.08f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = SamsungBlue,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = userName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (userTier) {
                                "premium" -> "Premium 회원"
                                "paid" -> "Business 회원"
                                else -> "무료 회원"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SamsungBlue
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "로그인되지 않음",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.Button(
                            onClick = onLoginClick,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = SamsungBlue),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Text("로그인 / 회원가입", fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // === 디스플레이 ===
            SettingsSectionHeader("디스플레이")

            SettingsItem(
                icon = Icons.Default.TextFields,
                title = "글자 크기",
                subtitle = getFontSizeLabel(uiState.fontScale),
                isClickable = false
            )

            // Font size slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("작게", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = uiState.fontScale,
                    onValueChange = { viewModel.updateFontScale(it) },
                    valueRange = 0.8f..2.0f,
                    steps = 11,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text("크게", style = MaterialTheme.typography.labelSmall)
            }

            SettingsToggle(
                icon = Icons.Default.DarkMode,
                title = "다크 모드",
                subtitle = if (uiState.isDarkMode) "켜짐" else "시스템 설정 따르기",
                isChecked = uiState.isDarkMode,
                onCheckedChange = { viewModel.updateDarkMode(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // === 메시지 앱 ===
            SettingsSectionHeader("메시지 앱")

            SettingsItem(
                icon = Icons.Default.Sms,
                title = "기본 메시지 앱",
                subtitle = if (isDefaultSmsApp) "BizConnect (현재 기본앱)" else "기본앱으로 설정하기",
                onClick = {
                    if (!isDefaultSmsApp) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val roleManager = context.getSystemService(RoleManager::class.java)
                            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                            roleRequestLauncher.launch(intent)
                        } else {
                            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
                            roleRequestLauncher.launch(intent)
                        }
                    }
                }
            )

            SettingsToggle(
                icon = Icons.Default.Notifications,
                title = "알림",
                subtitle = "메시지 수신 알림",
                isChecked = uiState.notificationsEnabled,
                onCheckedChange = { viewModel.updateNotifications(it) }
            )

            SettingsItem(
                icon = Icons.Default.Notifications,
                title = "알림 설정",
                subtitle = "알림음, 진동 등 시스템 설정",
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
            )

            SettingsItem(
                icon = Icons.Default.Block,
                title = "스팸 관리",
                subtitle = "번호 차단, 키워드 필터",
                onClick = onSpamClick
            )

            Spacer(modifier = Modifier.height(24.dp))

            // === 보안 ===
            SettingsSectionHeader("보안 및 개인정보")

            SettingsItem(
                icon = Icons.Default.Lock,
                title = "보안 설정",
                subtitle = "앱 잠금, 인증번호 자동복사"
            )

            SettingsItem(
                icon = Icons.Default.Storage,
                title = "백업 및 복원",
                subtitle = "메시지 데이터 관리"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // === 웹 연동 ===
            var showSyncDialog by remember { mutableStateOf(false) }

            if (isLoggedIn) {
                SettingsSectionHeader("웹 연동")

                SettingsItem(
                    icon = Icons.Default.Person,
                    title = "연락처 웹 동기화",
                    subtitle = "폰 연락처를 웹에서 사용할 수 있게 업로드",
                    onClick = { showSyncDialog = true }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // 동기화 확인 다이얼로그
            if (showSyncDialog) {
                AlertDialog(
                    onDismissRequest = { showSyncDialog = false },
                    title = { Text("연락처 웹 동기화") },
                    text = { Text("폰에 저장된 연락처를 웹(sm.on1.kr)에 업로드합니다.\n\n웹에서 문자 발송 시 연락처를 사용할 수 있습니다.\n\n진행하시겠습니까?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showSyncDialog = false
                            viewModel.syncContactsToServer { msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                            }
                    }) { Text("동기화", color = SamsungBlue) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSyncDialog = false }) { Text("취소") }
                    }
                )
            }

            // === 개인정보 ===
            SettingsSectionHeader("개인정보")

            SettingsItem(
                icon = Icons.Default.Info,
                title = "개인정보처리방침",
                subtitle = "(주)다인온",
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://sm.on1.kr/privacy"))
                    context.startActivity(intent)
                }
            )

            // === 비즈니스 ===
            SettingsSectionHeader("비즈니스")

            SettingsItem(
                icon = Icons.AutoMirrored.Filled.Send,
                title = "일일 발송 한도",
                subtitle = "${uiState.dailyLimit}건",
                isClickable = false
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("안전 모드: 199건", style = MaterialTheme.typography.labelMedium)
                    Text("최대 모드: 499건", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = uiState.dailyLimit == 499,
                    onCheckedChange = { viewModel.updateDailyLimit(if (it) 499 else 199) }
                )
            }

            SettingsToggle(
                icon = Icons.Default.Check,
                title = "자동 승인 모드",
                subtitle = "대량 발송 시 자동 승인",
                isChecked = uiState.isAutoApproval,
                onCheckedChange = { viewModel.updateAutoApproval(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // === 정보 ===
            SettingsSectionHeader("정보 및 지원")

            SettingsItem(
                icon = Icons.Default.BugReport,
                title = "피드백 및 버그 보고",
                subtitle = "개선 의견 보내기"
            )

            SettingsItem(
                icon = Icons.Default.Info,
                title = "앱 정보",
                subtitle = "버전 2.0.0",
                isClickable = false
            )

            Spacer(modifier = Modifier.height(24.dp))

            // === 관리자 (admin 역할만) ===
            if (isLoggedIn && viewModel.getUserRole() == "admin") {
                SettingsSectionHeader("관리자")
                SettingsItem(
                    icon = Icons.Default.Build,
                    title = "관리자 설정",
                    subtitle = "단가, 한도, 구독 관리",
                    onClick = onAdminClick
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // === 로그아웃 (로그인 시에만) ===
            if (isLoggedIn) {
                SettingsItem(
                    icon = Icons.Default.Logout,
                    title = "로그아웃",
                    subtitle = "계정에서 로그아웃",
                    isDestructive = true,
                    onClick = {
                        viewModel.logout()
                        android.widget.Toast.makeText(context, "로그아웃 되었습니다", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = SamsungBlue,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit = {},
    isClickable: Boolean = true,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isClickable, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isDestructive) SamsungRed else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDestructive) SamsungRed else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isClickable && !isDestructive) {
            Icon(
                imageVector = Icons.Default.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(16.dp)
            )
        }
    }
}

@Composable
fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = isChecked, onCheckedChange = onCheckedChange)
    }
}

private fun getFontSizeLabel(scale: Float): String {
    return when {
        scale < 0.9f -> "작게"
        scale < 1.1f -> "보통"
        scale < 1.3f -> "크게"
        scale < 1.6f -> "아주 크게"
        else -> "최대"
    }
}
