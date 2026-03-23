package com.bizconnect.v2.ui.business

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.bizconnect.v2.ui.components.AiMessageGenerateDialog
import com.bizconnect.v2.ui.components.TemplatePickerDialog
import com.bizconnect.v2.ui.theme.SamsungBlue
import com.bizconnect.v2.ui.viewmodel.ScheduleCreateViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleCreateScreen(
    scheduleId: String? = null,
    onBackClick: () -> Unit = {},
    viewModel: ScheduleCreateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var showAiGenerateDialog by remember { mutableStateOf(false) }
    var showRecipientSelection by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.KOREA) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.KOREA) }
    val dateTimeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.updateImageUri(uri.toString()) }

    LaunchedEffect(uiState.isSaved) { if (uiState.isSaved) onBackClick() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    if (showTemplatePicker) {
        TemplatePickerDialog(
            onDismiss = { showTemplatePicker = false },
            onTemplateSelected = { template ->
                viewModel.updateMessage(template.content)
                if (template.isMms && template.imageUri != null) {
                    viewModel.updateImageUri(template.imageUri)
                }
                showTemplatePicker = false
            }
        )
    }

    // AI message generate dialog
    AiMessageGenerateDialog(
        isVisible = showAiGenerateDialog,
        onDismiss = { showAiGenerateDialog = false },
        onGenerated = { generatedText ->
            viewModel.updateMessage(generatedText)
        },
        onGenerate = { prompt -> viewModel.generateAiMessage(prompt) }
    )

    if (showRecipientSelection) {
        RecipientSelectionScreen(
            onBackClick = { showRecipientSelection = false },
            onConfirm = { recipients ->
                viewModel.setRecipients(recipients)
                showRecipientSelection = false
            }
        )
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (uiState.isEditing) "예약 수정" else "예약 발송") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // === 수신자 ===
            SectionTitle("수신자")
            OutlinedButton(
                onClick = { showRecipientSelection = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(if (uiState.recipients.isEmpty()) "수신자 선택" else "수신자 선택 (${uiState.recipients.size}명)")
            }
            if (uiState.recipients.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(uiState.recipients) { recipient ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.setRecipients(uiState.recipients.filter { it.phone != recipient.phone }) },
                            label = { Text(recipient.name.ifBlank { recipient.phone }, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // === 메시지 ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("메시지")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showAiGenerateDialog = true }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.padding(end = 4.dp).size(18.dp))
                        Text("AI 생성")
                    }
                    TextButton(onClick = { showTemplatePicker = true }) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("템플릿")
                    }
                }
            }

            // 변수 칩
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("%이름%", "%전화번호%", "%날짜%", "%시간%", "%회사명%", "%담당자%").forEach { variable ->
                    AssistChip(
                        onClick = { viewModel.updateMessage(uiState.message + variable) },
                        label = { Text(variable, style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = SamsungBlue.copy(alpha = 0.1f), labelColor = SamsungBlue
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.message,
                onValueChange = { viewModel.updateMessage(it) },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                placeholder = { Text("메시지 내용을 입력하세요") },
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // === 이미지 첨부 ===
            if (!uiState.imageUri.isNullOrBlank()) {
                Box(modifier = Modifier.padding(bottom = 8.dp)) {
                    val imgUri = uiState.imageUri ?: ""
                    AsyncImage(
                        model = if (imgUri.startsWith("/")) Uri.fromFile(java.io.File(imgUri)) else Uri.parse(imgUri),
                        contentDescription = "첨부 이미지",
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = { viewModel.updateImageUri(null) },
                        modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "제거", tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(14.dp))
                    }
                }
                Text("MMS로 발송됩니다 (63원/건)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                AssistChip(
                    onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    label = { Text("이미지 추가") },
                    leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = SamsungBlue.copy(alpha = 0.1f), labelColor = SamsungBlue, leadingIconContentColor = SamsungBlue
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // === 예약 시간 ===
            SectionTitle("예약 시간")
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val calendar = remember(uiState.scheduledAt) {
                    Calendar.getInstance().apply { timeInMillis = uiState.scheduledAt }
                }
                OutlinedTextField(
                    value = dateFormat.format(Date(uiState.scheduledAt)),
                    onValueChange = {}, modifier = Modifier.weight(1f).clickable {
                        DatePickerDialog(context, { _, y, m, d ->
                            val cal = Calendar.getInstance().apply { timeInMillis = uiState.scheduledAt; set(Calendar.YEAR, y); set(Calendar.MONTH, m); set(Calendar.DAY_OF_MONTH, d) }
                            viewModel.updateScheduledAt(cal.timeInMillis)
                        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                    }, readOnly = true, enabled = false,
                    leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                OutlinedTextField(
                    value = timeFormat.format(Date(uiState.scheduledAt)),
                    onValueChange = {}, modifier = Modifier.weight(1f).clickable {
                        TimePickerDialog(context, { _, h, m ->
                            val cal = Calendar.getInstance().apply { timeInMillis = uiState.scheduledAt; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0) }
                            viewModel.updateScheduledAt(cal.timeInMillis)
                        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
                    }, readOnly = true, enabled = false,
                    leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // === 반복 ===
            SectionTitle("반복")
            Spacer(modifier = Modifier.height(8.dp))
            val repeatOptions = listOf("none" to "1회", "daily" to "매일", "weekly" to "매주", "monthly" to "매월", "yearly" to "매년")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeatOptions.forEach { (value, label) ->
                    FilterChip(selected = uiState.repeatType == value, onClick = { viewModel.updateRepeatType(value) }, label = { Text(label) })
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // === 예약 버튼 ===
            Button(
                onClick = { viewModel.save() },
                enabled = uiState.recipients.isNotEmpty() && uiState.message.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (uiState.isEditing) "예약 수정" else "예약 발송",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}
