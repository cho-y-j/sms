package com.bizconnect.v2.ui.business

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

    // Template picker dialog state
    var showTemplatePickerFor by remember { mutableStateOf<String?>(null) }

    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.setBusinessCardImage(it.toString()) }
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
                    onCheckedChange = { viewModel.toggleAutoCallback(it) }
                )

                if (state.autoCallbackEnabled) {
                    Spacer(modifier = Modifier.height(24.dp))

                    // === 통화 종료 후 ===
                    CallbackSectionHeader("통화 종료 후")

                    SettingToggleItem(
                        title = "자동 응답 활성화",
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
                        title = "자동 응답 활성화",
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
                        title = "자동 응답 활성화",
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
                        title = "자동 응답 활성화",
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
                                    painter = rememberAsyncImagePainter(model = Uri.parse(imageUrl)),
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
        style = MaterialTheme.typography.labelSmall,
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
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            decorationBox = { innerTextField ->
                if (message.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                innerTextField()
            }
        )
    }
}
