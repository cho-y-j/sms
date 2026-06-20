package com.bizconnect.v2.ui.business

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bizconnect.v2.ui.theme.SamsungBlue
import com.bizconnect.v2.ui.viewmodel.AuthViewModel
import com.bizconnect.v2.ui.viewmodel.BusinessHomeViewModel

data class BusinessFeature(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: androidx.compose.ui.graphics.Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessHomeScreen(
    onBulkSendClick: () -> Unit = {},
    onScheduledMessagesClick: () -> Unit = {},
    onCallbackSettingsClick: () -> Unit = {},
    onCustomerManagementClick: () -> Unit = {},
    onSpamManagementClick: () -> Unit = {},
    onMessageTemplateClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onAnalyticsClick: () -> Unit = {},
    onAiSettingsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onPricingClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    viewModel: BusinessHomeViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    // Refresh dashboard data when screen resumes (e.g., after server sync)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadDashboardData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 핵심 2기능(콜백·자주쓰는 문자)은 아래에서 전폭 대형 카드로 따로 강조하고,
    // 나머지는 보조 그리드로 둔다.
    val features = listOf(
        // 대량 문자 발송: Google Play SMS 정책(대량/마케팅 발송 금지) 거절 트리거이므로
        // 공개 빌드의 홈 메뉴에서 숨김. 라우트/화면 코드는 보존(추후 별도 배포용).
        BusinessFeature(
            id = "scheduled",
            title = "예약 발송",
            subtitle = "시간을 정해 자동 전송",
            icon = Icons.Default.Schedule,
            color = androidx.compose.ui.graphics.Color(0xFF6C5CE7)
        ),
        BusinessFeature(
            id = "customers",
            title = "고객 관리",
            subtitle = "그룹 및 연락처 관리",
            icon = Icons.Default.GroupAdd,
            color = androidx.compose.ui.graphics.Color(0xFFFDAB2D)
        ),
        BusinessFeature(
            id = "spam",
            title = "스팸 관리",
            subtitle = "차단 및 필터 설정",
            icon = Icons.Default.Security,
            color = androidx.compose.ui.graphics.Color(0xFFE84E3F)
        ),
        BusinessFeature(
            id = "history",
            title = "발송 기록",
            subtitle = "과거 발송 현황 조회",
            icon = Icons.Default.History,
            color = androidx.compose.ui.graphics.Color(0xFF00A0D1)
        ),
        // 통계는 관리자 웹 패널에서 확인
        BusinessFeature(
            id = "ai_settings",
            title = "AI 설정",
            subtitle = "AI 기능 및 API 키 관리",
            icon = Icons.Default.AutoAwesome,
            color = androidx.compose.ui.graphics.Color(0xFF9C27B0)
        ),
        BusinessFeature(
            id = "settings",
            title = "설정",
            subtitle = "앱 설정 및 계정",
            icon = Icons.Default.Settings,
            color = MaterialTheme.colorScheme.secondary
        )
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "비즈니스",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    if (authViewModel.isLoggedIn) {
                        IconButton(onClick = { authViewModel.logout() }) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "로그아웃",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        IconButton(onClick = onLoginClick) {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = "로그인",
                                tint = SamsungBlue
                            )
                        }
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                DashboardSection(
                    sentToday = viewModel.sentToday,
                    dailyLimit = viewModel.dailyLimit,
                    creditBalance = viewModel.creditBalance,
                    subscriptionTier = viewModel.subscriptionTier,
                    onSubscribeClick = onPricingClick
                )
            }

            // 핵심① 통화 후 자동 문자 (콜백)
            item(span = { GridItemSpan(2) }) {
                HeroFeatureCard(
                    title = "통화 후 자동 문자",
                    subtitle = "못 받은 전화에 명함·인사를 자동으로 보내드려요",
                    icon = Icons.Default.PhoneCallback,
                    color = androidx.compose.ui.graphics.Color(0xFF00B894),
                    onClick = onCallbackSettingsClick
                )
            }
            // 핵심② 자주 쓰는 문자 (템플릿)
            item(span = { GridItemSpan(2) }) {
                HeroFeatureCard(
                    title = "자주 쓰는 문자",
                    subtitle = "서류·인사말을 저장해두고 한 번에 빠르게 보내기",
                    icon = Icons.Default.Description,
                    color = androidx.compose.ui.graphics.Color(0xFF2196F3),
                    onClick = onMessageTemplateClick
                )
            }

            items(features.size) { index ->
                val feature = features[index]
                BusinessFeatureCard(
                    feature = feature,
                    onClick = {
                        when (feature.id) {
                            "message_templates" -> onMessageTemplateClick()
                            "bulk_send" -> onBulkSendClick()
                            "scheduled" -> onScheduledMessagesClick()
                            "callback" -> onCallbackSettingsClick()
                            "customers" -> onCustomerManagementClick()
                            "spam" -> onSpamManagementClick()
                            "history" -> onHistoryClick()
                            "analytics" -> onAnalyticsClick()
                            "ai_settings" -> onAiSettingsClick()
                            "settings" -> onSettingsClick()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun BusinessFeatureCard(
    feature: BusinessFeature,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        feature.color.copy(alpha = 0.15f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = feature.icon,
                    contentDescription = feature.title,
                    tint = feature.color,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )

            Text(
                text = feature.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 핵심 기능(콜백·자주쓰는 문자)용 전폭 대형 카드. 보조 기능 카드보다 크고 글자도 큼.
 */
@Composable
fun HeroFeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(color.copy(alpha = 0.18f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
