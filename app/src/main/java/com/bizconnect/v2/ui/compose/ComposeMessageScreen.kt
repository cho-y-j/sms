package com.bizconnect.v2.ui.compose

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bizconnect.v2.ui.components.AvatarView
import com.bizconnect.v2.ui.message.AttachmentPicker
import com.bizconnect.v2.ui.theme.SamsungBlue
import com.bizconnect.v2.ui.viewmodel.ComposeMessageViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeMessageScreen(
    onBackClick: () -> Unit = {},
    onNavigateToThread: (Long) -> Unit = {},
    initialPhone: String? = null,
    viewModel: ComposeMessageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showSearchDialog by remember { mutableStateOf(false) }
    var showPhoneDialog by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showAttachmentPicker by remember { mutableStateOf(false) }
    var showTemplatePicker by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> selectedImageUri = uri }

    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(initialPhone) {
        if (!initialPhone.isNullOrEmpty()) {
            viewModel.addRecipientByPhone(initialPhone)
        }
    }

    // 발송 결과 처리
    LaunchedEffect(uiState.sendResult) {
        val result = uiState.sendResult ?: return@LaunchedEffect
        viewModel.clearSendResult()

        if (result.totalCount == 1 && result.threadId > 0) {
            // 1건 → 대화창으로 자동 이동
            onNavigateToThread(result.threadId)
        } else if (result.totalCount > 1) {
            // 다건 → 토스트
            android.widget.Toast.makeText(
                context,
                "문자 발송 완료 ${result.successCount}/${result.totalCount}건",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("새 메시지") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = { /* 전송 버튼은 하단 입력바로 이동 */ }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
          Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // === 받는 사람 추가 버튼 2개 ===
            Text("받는 사람", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 검색하여 추가
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showSearchDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = SamsungBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("이름 검색", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // 번호 직접 입력
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showPhoneDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = SamsungBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("번호 입력", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // === 추가된 수신자 목록 ===
            if (uiState.selectedRecipients.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.selectedRecipients) { recipient ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = SamsungBlue.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    recipient.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SamsungBlue,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(
                                    onClick = { viewModel.removeRecipient(recipient) },
                                    modifier = Modifier.size(18.dp).padding(start = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Cancel,
                                        contentDescription = "제거",
                                        tint = SamsungBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

          } // end top section

            Spacer(modifier = Modifier.weight(1f))

            // === 이미지 미리보기 ===
            if (selectedImageUri != null) {
                Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "첨부 이미지",
                        modifier = Modifier
                            .height(100.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = { selectedImageUri = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(22.dp)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "제거", tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(12.dp))
                    }
                }
            }

            // === 하단 메시지 입력 + 첨부 + 전송 ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(onClick = { showAttachmentPicker = true }) {
                    Icon(Icons.Default.AttachFile, contentDescription = "첨부", tint = SamsungBlue)
                }

                Spacer(modifier = Modifier.width(4.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 120.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = uiState.messageText,
                        onValueChange = { viewModel.updateMessage(it) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        decorationBox = { innerTextField ->
                            if (uiState.messageText.isEmpty()) {
                                Text("메시지를 입력하세요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            innerTextField()
                        }
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                val canSend = uiState.selectedRecipients.isNotEmpty() && (uiState.messageText.isNotEmpty() || selectedImageUri != null)
                if (canSend) {
                    IconButton(onClick = {
                        if (selectedImageUri != null) {
                            viewModel.sendMmsWithImage(uiState.messageText, selectedImageUri ?: return@IconButton)
                            selectedImageUri = null
                        } else {
                            viewModel.sendMessage()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "전송", tint = SamsungBlue)
                    }
                }
            }
        }
    }

    // === 이름 검색 다이얼로그 ===
    if (showSearchDialog) {
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<ComposeMessageViewModel.ContactUiModel>>(emptyList()) }

        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("연락처 검색") },
            text = {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            if (it.length >= 2) viewModel.searchContactsOnce(it) { results = it }
                            else results = emptyList()
                        },
                        label = { Text("이름 입력 (2자 이상)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(results) { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addRecipient(contact)
                                        showSearchDialog = false
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarView(name = contact.name, photoUrl = contact.photoUri, size = 36.dp, modifier = Modifier.padding(end = 8.dp))
                                Column {
                                    Text(contact.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(contact.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) { Text("닫기") }
            }
        )
    }

    // === AttachmentPicker bottom sheet ===
    AttachmentPicker(
        isVisible = showAttachmentPicker,
        onDismiss = { showAttachmentPicker = false },
        onGallery = {
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onCamera = {
            // Camera not yet wired for ComposeMessageScreen — fall back to gallery
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onFile = {
            // File picker not yet wired — fall back to gallery
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onContact = {
            // Share own contact as text
            try {
                val tm = context.getSystemService(android.telephony.TelephonyManager::class.java)
                @Suppress("MissingPermission", "DEPRECATION")
                val myNumber = tm?.line1Number ?: ""
                val profileCursor = context.contentResolver.query(
                    android.provider.ContactsContract.Profile.CONTENT_URI,
                    arrayOf(android.provider.ContactsContract.Profile.DISPLAY_NAME),
                    null, null, null
                )
                val myName = profileCursor?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: "내 연락처"

                val vCard = """
BEGIN:VCARD
VERSION:3.0
FN:$myName
TEL:$myNumber
END:VCARD
                """.trimIndent()
                viewModel.updateMessage(uiState.messageText + vCard)
            } catch (_: Exception) { }
        },
        onLocation = {
            // Location sharing stub — append placeholder
            viewModel.updateMessage(uiState.messageText + "[위치 공유]")
        },
        onTemplate = { showTemplatePicker = true }
    )

    // === Template picker dialog ===
    if (showTemplatePicker) {
        com.bizconnect.v2.ui.components.TemplatePickerDialog(
            onDismiss = { showTemplatePicker = false },
            onTemplateSelected = { template ->
                viewModel.updateMessage(template.content)
                showTemplatePicker = false
            },
            onCreateNew = { /* 인라인 생성 폼이 다이얼로그 안에서 처리됨 */ }
        )
    }

    // === 번호 직접 입력 다이얼로그 ===
    if (showPhoneDialog) {
        var phone by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPhoneDialog = false },
            title = { Text("번호 직접 입력") },
            text = {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("전화번호") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (phone.isNotBlank()) {
                            viewModel.addRecipientByPhone(phone.trim())
                            showPhoneDialog = false
                        }
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (phone.isNotBlank()) {
                        viewModel.addRecipientByPhone(phone.trim())
                        showPhoneDialog = false
                    }
                }) { Text("추가") }
            },
            dismissButton = {
                TextButton(onClick = { showPhoneDialog = false }) { Text("취소") }
            }
        )
    }
}
