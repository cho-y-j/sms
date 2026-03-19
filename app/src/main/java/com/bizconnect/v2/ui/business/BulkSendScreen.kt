package com.bizconnect.v2.ui.business

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.bizconnect.v2.ui.components.AiMessageGenerateDialog
import com.bizconnect.v2.ui.components.TemplatePickerDialog
import com.bizconnect.v2.ui.viewmodel.BulkSendViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BulkSendScreen(
    onBackClick: () -> Unit = {},
    viewModel: BulkSendViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showRecipientSelection by remember { mutableStateOf(false) }
    var showTemplatePickerDialog by remember { mutableStateOf(false) }
    var showAiGenerateDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        viewModel.setImageUri(uri)
    }

    // Show error via snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Show send complete via snackbar
    LaunchedEffect(uiState.sendComplete) {
        if (uiState.sendComplete) {
            snackbarHostState.showSnackbar(
                "발송 완료: 성공 ${uiState.successCount}건, 실패 ${uiState.failureCount}건"
            )
            viewModel.resetSendState()
        }
    }

    // Template picker dialog
    if (showTemplatePickerDialog) {
        TemplatePickerDialog(
            onDismiss = { showTemplatePickerDialog = false },
            onTemplateSelected = { template ->
                viewModel.loadTemplateFromEntity(template)
                showTemplatePickerDialog = false
            }
        )
    }

    // AI message generate dialog
    AiMessageGenerateDialog(
        isVisible = showAiGenerateDialog,
        onDismiss = { showAiGenerateDialog = false },
        onGenerated = { generatedText ->
            viewModel.setMessageText(generatedText)
        },
        onGenerate = { prompt -> viewModel.generateAiMessage(prompt) }
    )

    // Confirm dialog
    if (showConfirmDialog) {
        BulkSendConfirmDialog(
            recipientCount = uiState.recipients.size,
            messageType = viewModel.getMessageType(),
            estimatedCost = viewModel.estimateCost(),
            dailySentCount = uiState.dailySentCount,
            dailyLimit = viewModel.getDailyLimit(),
            creditBalance = viewModel.getCreditBalance(),
            onConfirm = {
                showConfirmDialog = false
                viewModel.sendBulkMessages()
            },
            onDismiss = { showConfirmDialog = false }
        )
    }

    // Recipient selection full-screen overlay
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
                title = {
                    Text(
                        text = "대량 문자",
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
                // ============================================================
                // Section 1: Recipients
                // ============================================================
                Text(
                    text = "수신자 선택",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedButton(
                    onClick = { showRecipientSelection = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = if (uiState.recipients.isEmpty()) {
                            "수신자 선택"
                        } else {
                            "수신자 선택 (${uiState.recipients.size}명)"
                        }
                    )
                }

                // Show selected recipient chips (scrollable)
                if (uiState.recipients.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(uiState.recipients) { recipient ->
                            InputChip(
                                selected = true,
                                onClick = {
                                    viewModel.setRecipients(
                                        uiState.recipients.filter { it.phone != recipient.phone }
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (recipient.name != recipient.phone) {
                                            recipient.name
                                        } else {
                                            recipient.phone
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "삭제",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ============================================================
                // Section 2: Message
                // ============================================================
                Text(
                    text = "메시지 작성",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showTemplatePickerDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("템플릿 불러오기")
                    }

                    OutlinedButton(
                        onClick = { showAiGenerateDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp).size(18.dp)
                        )
                        Text("AI 메시지 생성")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    BasicTextField(
                        value = uiState.messageText,
                        onValueChange = { viewModel.setMessageText(it) },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        decorationBox = { innerTextField ->
                            if (uiState.messageText.isEmpty()) {
                                Text(
                                    text = "발송할 메시지를 입력하세요",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                // Character count + message type
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val messageType = viewModel.getMessageType()
                    val byteSize = uiState.messageText.toByteArray(Charsets.UTF_8).size
                    Text(
                        text = "${uiState.messageText.length}자 / ${byteSize}바이트",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = messageType.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = when (messageType) {
                            MessageType.SMS -> MaterialTheme.colorScheme.primary
                            MessageType.LMS -> MaterialTheme.colorScheme.tertiary
                            MessageType.MMS -> MaterialTheme.colorScheme.error
                        }
                    )
                }

                // Variable insertion chips
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "변수 삽입",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val variables = listOf("이름", "전화번호", "날짜", "시간", "회사명", "담당자")
                    variables.forEach { variable ->
                        SuggestionChip(
                            onClick = { viewModel.insertVariable(variable) },
                            label = {
                                Text(
                                    text = "%${variable}%",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ============================================================
                // Section 3: Image attachment
                // ============================================================
                Text(
                    text = "이미지 첨부 (선택)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (uiState.selectedImageUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = uiState.selectedImageUri,
                            contentDescription = "첨부 이미지",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { viewModel.setImageUri(null) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(32.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "이미지 삭제",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("갤러리에서 선택")
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // ============================================================
                // Progress indicator
                // ============================================================
                if (uiState.isSending) {
                    Column {
                        LinearProgressIndicator(
                            progress = { uiState.sendProgress },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "${(uiState.sendProgress * 100).toInt()}% 발송 중... " +
                                "(성공: ${uiState.successCount}, 실패: ${uiState.failureCount})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // ============================================================
                // Send button
                // ============================================================
                Button(
                    onClick = { showConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = uiState.recipients.isNotEmpty() &&
                        uiState.messageText.isNotEmpty() &&
                        !uiState.isSending,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("발송", color = Color.White)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
