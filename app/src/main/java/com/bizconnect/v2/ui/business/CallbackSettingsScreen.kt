package com.bizconnect.v2.ui.business

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.bizconnect.v2.util.BatteryOptimization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.bizconnect.v2.ui.components.TemplatePickerDialog
import com.bizconnect.v2.ui.theme.SamsungBlue
import com.bizconnect.v2.ui.viewmodel.CallbackSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallbackSettingsScreen(
    onBackClick: () -> Unit = {},
    viewModel: CallbackSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // 콜백 신뢰성: 배터리 최적화 예외를 받아야 백그라운드에서 통화 후 콜백이 안 죽음.
    val batteryOptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    // Template picker dialog state
    var showTemplatePickerFor by remember { mutableStateOf<String?>(null) }

    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setBusinessCardImage(it.toString()) }
    }

    // 자동 콜백용 통화기록 권한: 사전 고지(prominent disclosure) → 권한 요청 → 기능 활성화
    var showCallLogDisclosure by remember { mutableStateOf(false) }
    val hasCallPermission = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }
    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            viewModel.toggleAutoCallback(true)
            BatteryOptimization.createRequestIntent(context)?.let { batteryOptLauncher.launch(it) }
        } else {
            viewModel.toggleAutoCallback(false)
            android.widget.Toast.makeText(
                context,
                "통화기록 권한이 있어야 통화 후 자동 콜백을 보낼 수 있어요",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            snackbarHostState.showSnackbar("설정이 저장되었습니다")
        }
    }

    // Template picker dialogs
    showTemplatePickerFor?.let { target ->
        TemplatePickerDialog(
            onDismiss = { showTemplatePickerFor = null },
            onTemplateSelected = { template ->
                when (target) {
                    "onEnd" -> {
                        viewModel.updateOnEndMessage(template.content)
                        viewModel.setOnEndTemplateId(template.id)
                    }
                    "onMissed" -> {
                        viewModel.updateOnMissedMessage(template.content)
                        viewModel.setOnMissedTemplateId(template.id)
                    }
                    "onBusy" -> {
                        viewModel.updateOnBusyMessage(template.content)
                        viewModel.setOnBusyTemplateId(template.id)
                    }
                    "onOutgoing" -> {
                        viewModel.updateOutgoingMessage(template.content)
                        viewModel.setOutgoingTemplateId(template.id)
                    }
                }
                showTemplatePickerFor = null
            }
        )
    }

    // 통화기록 사용 사전 고지 (Google Play 정책: 제한 데이터 접근 전 명확한 고지 + 동의)
    if (showCallLogDisclosure) {
        AlertDialog(
            onDismissRequest = { showCallLogDisclosure = false },
            icon = { Icon(Icons.Default.Check, contentDescription = null, tint = SamsungBlue) },
            title = { Text("통화기록 사용 안내") },
            text = {
                Text(
                    "‘자동 콜백’을 켜면 BizConnect가 방금 종료된 통화·부재중 전화의 전화번호를 " +
                        "통화기록에서 읽어, 그 번호로 미리 설정한 회신 문자를 자동으로 보냅니다.\n\n" +
                        "• 통화기록은 ‘회신할 번호 확인’ 용도로만, 기기 안에서만 사용합니다\n" +
                        "• 서버 전송·광고·분석에 사용하지 않습니다\n" +
                        "• 자동 콜백을 끄면 통화기록을 읽지 않습니다"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCallLogDisclosure = false
                    callPermissionLauncher.launch(
                        arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE)
                    )
                }) { Text("동의하고 계속", color = SamsungBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showCallLogDisclosure = false }) { Text("취소") }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "콜백 설정",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.saveSettings() }) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "저장",
                            tint = SamsungBlue
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Master toggle
                SettingToggleItem(
                    title = "자동 콜백",
                    subtitle = "자동 응답 메시지 사용",
                    isChecked = state.autoCallbackEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            // 통화기록 권한이 없으면 먼저 사전 고지 → 권한 요청 후 활성화
                            if (hasCallPermission()) {
                                viewModel.toggleAutoCallback(true)
                                BatteryOptimization.createRequestIntent(context)
                                    ?.let { batteryOptLauncher.launch(it) }
                            } else {
                                showCallLogDisclosure = true
                            }
                        } else {
                            viewModel.toggleAutoCallback(false)
                        }
                    }
                )

                if (state.autoCallbackEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // 처음 설정 가이드 (3단계)
                    InfoNote(
                        text = "처음이세요? ① 보낼 메시지 입력 → ② 아래 '명함 이미지' 등록 → ③ 저장하면 끝!",
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // 백그라운드 동작 안내 (OEM 절전 정책 대응)
                    InfoNote(
                        text = "콜백이 안정적으로 동작하려면 배터리 최적화에서 'BizConnect'를 제외하고, " +
                            "샤오미·오포·비보 등은 설정 > 앱 > 자동 시작을 허용해 주세요.",
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // === 발송 방식 (자동/수동) ===
                    SettingToggleItem(
                        title = "수동 발송 (확인 후 보내기)",
                        subtitle = if (state.manualMode)
                            "통화 종료 후 알림에서 [보내기]를 눌러야 발송됩니다"
                        else
                            "통화 종료 후 자동으로 즉시 발송됩니다",
                        isChecked = state.manualMode,
                        onCheckedChange = { viewModel.setManualMode(it) }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // === 통화 종료 후 ===
                    CallbackSectionHeader("통화 종료 후")

                    SettingToggleItem(
                        title = "통화 끝나면 보내기",
                        subtitle = "통화 종료 후 메시지 발송",
                        isChecked = state.onEndEnabled,
                        onCheckedChange = { viewModel.toggleOnEnd(it) }
                    )

                    if (state.onEndEnabled) {
                        MessageEditBox(
                            message = state.onEndMessage,
                            onMessageChange = { viewModel.updateOnEndMessage(it) },
                            placeholder = "메시지 입력",
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        TemplateLoadButton(onClick = { showTemplatePickerFor = "onEnd" })
                        MessageCostNote(state.onEndMessage)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // === 부재중 전화 ===
                    CallbackSectionHeader("부재중 전화")

                    SettingToggleItem(
                        title = "못 받은 전화에 보내기",
                        subtitle = "부재중 전화에 메시지 발송",
                        isChecked = state.onMissedEnabled,
                        onCheckedChange = { viewModel.toggleOnMissed(it) }
                    )

                    if (state.onMissedEnabled) {
                        MessageEditBox(
                            message = state.onMissedMessage,
                            onMessageChange = { viewModel.updateOnMissedMessage(it) },
                            placeholder = "메시지 입력",
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        TemplateLoadButton(onClick = { showTemplatePickerFor = "onMissed" })
                        MessageCostNote(state.onMissedMessage)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // === 통화중 거절 ===
                    CallbackSectionHeader("통화중 거절")

                    SettingToggleItem(
                        title = "거절한 전화에 보내기",
                        subtitle = "통화 중 거절 시 메시지 발송",
                        isChecked = state.onBusyEnabled,
                        onCheckedChange = { viewModel.toggleOnBusy(it) }
                    )

                    if (state.onBusyEnabled) {
                        MessageEditBox(
                            message = state.onBusyMessage,
                            onMessageChange = { viewModel.updateOnBusyMessage(it) },
                            placeholder = "메시지 입력",
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        TemplateLoadButton(onClick = { showTemplatePickerFor = "onBusy" })
                        MessageCostNote(state.onBusyMessage)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // === 발신 통화 종료 후 ===
                    CallbackSectionHeader("발신 통화 종료 후")

                    SettingToggleItem(
                        title = "내가 건 통화 후 보내기",
                        subtitle = "내가 전화 걸고 종료 시 메시지 발송",
                        isChecked = state.onOutgoingEnabled,
                        onCheckedChange = { viewModel.toggleOutgoing(it) }
                    )

                    if (state.onOutgoingEnabled) {
                        MessageEditBox(
                            message = state.onOutgoingMessage,
                            onMessageChange = { viewModel.updateOutgoingMessage(it) },
                            placeholder = "메시지 입력",
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        TemplateLoadButton(onClick = { showTemplatePickerFor = "onOutgoing" })
                        MessageCostNote(state.onOutgoingMessage)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // === 명함 이미지 ===
                    CallbackSectionHeader("명함 이미지")

                    SettingToggleItem(
                        title = "명함 이미지 첨부",
                        subtitle = "콜백 메시지에 명함 이미지 포함",
                        isChecked = state.businessCardEnabled,
                        onCheckedChange = { viewModel.toggleBusinessCard(it) }
                    )

                    if (state.businessCardEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Image preview
                        state.businessCardImageUrl?.let { imageUrl ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(
                                        model = if (imageUrl.startsWith("/")) java.io.File(imageUrl)
                                                else Uri.parse(imageUrl)
                                    ),
                                    contentDescription = "명함 이미지",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("이미지 선택")
                        }

                        InfoNote(
                            text = "이미지 포함 시 MMS(63원)",
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // === 제외 카테고리 ===
                    CallbackSectionHeader("제외 카테고리")

                    if (state.categories.isEmpty()) {
                        Text(
                            text = "등록된 카테고리가 없습니다",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(4.dp)
                        ) {
                            Column {
                                state.categories.forEach { category ->
                                    val isExcluded = category.id in state.excludedCategoryIds
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isExcluded,
                                            onCheckedChange = {
                                                viewModel.toggleCategoryExclusion(category.id)
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = SamsungBlue
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = category.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    InfoNote(
                        text = "선택된 카테고리 연락처는 콜백이 발송되지 않습니다",
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // === 자동발송 금지 번호 ===
                    CallbackSectionHeader("자동발송 금지 번호")

                    if (state.blockedNumbers.isEmpty()) {
                        Text(
                            text = "금지된 번호가 없습니다. 통화 종료 알림에서 [자동발송 금지]를 누르면 여기에 추가됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(4.dp)
                        ) {
                            Column {
                                state.blockedNumbers.forEach { number ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = number,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(onClick = { viewModel.removeBlockedNumber(number) }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "삭제",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    InfoNote(
                        text = "이 목록의 번호에는 자동·수동 모두 콜백이 발송되지 않습니다",
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // === 발송 간격 ===
                    CallbackSectionHeader("발송 간격")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Column {
                            Text(
                                text = "최소 ${state.throttleInterval}분 간격으로 발송",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = state.throttleInterval.toFloat(),
                                onValueChange = { viewModel.updateThrottleInterval(it.toInt()) },
                                valueRange = 1f..60f,
                                steps = 58,
                                colors = SliderDefaults.colors(
                                    thumbColor = SamsungBlue,
                                    activeTrackColor = SamsungBlue
                                )
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "1분",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "60분",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // === 비용 안내 ===
                    CallbackSectionHeader("비용 안내")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                SamsungBlue.copy(alpha = 0.1f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "무료 50건/일 포함, 유료 150건/일",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SamsungBlue
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "초과 시 충전 잔액에서 차감",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Template variables info
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                SamsungBlue.copy(alpha = 0.1f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "사용 가능한 변수",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SamsungBlue,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "{고객명} - 발신자 이름\n{날짜} - 현재 날짜\n{시간} - 현재 시간",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Save button
                Button(
                    onClick = { viewModel.saveSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue)
                ) {
                    Text(
                        text = "저장",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun CallbackSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun TemplateLoadButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text("템플릿 불러오기")
    }
}

@Composable
private fun MessageCostNote(message: String) {
    val byteSize = message.toByteArray(Charsets.UTF_8).size
    val costText = if (byteSize <= 90) {
        "90바이트 이하: 단문(9.8원)"
    } else {
        "90바이트 초과: 장문(29원)"
    }
    InfoNote(
        text = "$costText (현재 ${byteSize}바이트)",
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun InfoNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        // 노안 대응: 안내문구를 labelSmall(11sp) → bodyMedium(14sp)로 상향
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp)
    )
}

@Composable
fun SettingToggleItem(
    title: String,
    subtitle: String = "",
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SamsungBlue
            )
        )
    }
}

@Composable
fun MessageEditBox(
    message: String,
    onMessageChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        BasicTextField(
            value = message,
            onValueChange = onMessageChange,
            modifier = Modifier.fillMaxWidth(),
            // 노안 대응: 콜백 회신 본문은 직접 읽고 고치는 핵심 텍스트 → bodyLarge(16sp)
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            decorationBox = { innerTextField ->
                if (message.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                innerTextField()
            }
        )
    }
}
