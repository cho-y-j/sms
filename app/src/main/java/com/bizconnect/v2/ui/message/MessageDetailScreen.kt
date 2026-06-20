package com.bizconnect.v2.ui.message

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bizconnect.v2.ui.components.AiSummarySheet
import com.bizconnect.v2.ui.components.DateHeader
import com.bizconnect.v2.ui.components.MessageBubble
import com.bizconnect.v2.ui.theme.SamsungBlue
import com.bizconnect.v2.ui.viewmodel.MessageDetailViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun MessageDetailScreen(
    threadId: Long,
    onBackClick: () -> Unit = {},
    onCallClick: () -> Unit = {},
    viewModel: MessageDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // 문자 읽어주기(TTS) — 화면 생명주기에 묶인 컨트롤러 + 설정 플래그
    val tts = com.bizconnect.v2.util.rememberTtsController()
    val ttsEnabled = remember { viewModel.isTtsEnabled() }

    // AI state
    val aiSummary by viewModel.aiSummary.collectAsStateWithLifecycle()
    val aiEmotion by viewModel.aiEmotion.collectAsStateWithLifecycle()
    val aiAppointment by viewModel.aiAppointment.collectAsStateWithLifecycle()
    val aiSuggestions by viewModel.aiSuggestions.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val isAiSuggestLoading by viewModel.isAiSuggestLoading.collectAsStateWithLifecycle()
    val toneConvertedText by viewModel.toneConvertedText.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val aiSearchResult by viewModel.aiSearchResult.collectAsStateWithLifecycle()
    val isAiSearchLoading by viewModel.isAiSearchLoading.collectAsStateWithLifecycle()
    var showAiSummarySheet by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchMatchIndex by remember { mutableIntStateOf(0) }
    var showAiSearchDialog by remember { mutableStateOf(false) }

    var showAttachmentPicker by remember { mutableStateOf(false) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var selectedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    // Location permission
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                val locationManager = context.getSystemService(android.location.LocationManager::class.java)
                @Suppress("MissingPermission")
                val location = locationManager?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                    ?: locationManager?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                if (location != null) {
                    viewModel.sendMessage("📍 내 위치\nhttps://maps.google.com/?q=${location.latitude},${location.longitude}&z=17")
                } else {
                    android.widget.Toast.makeText(context, "위치를 가져올 수 없습니다. GPS를 켜주세요.", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {}
        }
    }

    // Gallery picker (multiple images)
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImageUris = uris
            selectedFileUri = null
            selectedFileName = null
        }
    }

    // Camera picker
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            selectedImageUris = listOf(cameraImageUri ?: return@rememberLauncherForActivityResult)
            selectedFileUri = null
            selectedFileName = null
        }
    }

    // File picker (all types → Firebase upload + SMS link)
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Check if it's an image
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType?.startsWith("image/") == true) {
                selectedImageUris = listOf(uri)
                selectedFileUri = null
                selectedFileName = null
            } else {
                // Non-image file → server upload
                android.util.Log.d("MessageDetailScreen", "File selected: $uri, mimeType=$mimeType")
                selectedFileUri = uri
                selectedImageUris = emptyList()
                // Get file name
                selectedFileName = try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else null
                    }
                } catch (_: Exception) { null }
            }
        }
    }

    // Scroll to bottom
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text(
                text = uiState.contactName.ifEmpty { uiState.phoneNumber.ifEmpty { "..." } },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                showAiSummarySheet = true
                viewModel.loadAiSummary()
            }) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = "AI 분석",
                    tint = SamsungBlue
                )
            }
            IconButton(onClick = {
                showSearchBar = !showSearchBar
                if (!showSearchBar) {
                    viewModel.clearSearch()
                    searchMatchIndex = 0
                }
            }) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "검색",
                    tint = if (showSearchBar) SamsungBlue else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = {
                val phone = uiState.phoneNumber
                if (phone.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:$phone")
                    }
                    context.startActivity(intent)
                }
            }) {
                Icon(Icons.Default.Call, contentDescription = "전화")
            }
        }

        // Search bar (Samsung style rounded)
        if (showSearchBar) {
            val matchingMessages by remember(searchQuery, uiState.messages) {
                derivedStateOf {
                    if (searchQuery.isBlank()) emptyList()
                    else uiState.messages.filter {
                        it.text.contains(searchQuery, ignoreCase = true)
                    }
                }
            }
            val matchCount = matchingMessages.size

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = {
                        viewModel.setSearchQuery(it)
                        searchMatchIndex = 0
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text("대화 내 검색", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        innerTextField()
                    }
                )
                if (searchQuery.isNotBlank() && matchCount > 0) {
                    Text("${matchCount}건", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // AI search - always visible
                IconButton(onClick = {
                    val query = searchQuery.ifBlank { "전체 대화 요약해줘" }
                    viewModel.aiSearchInConversation(query)
                    showAiSearchDialog = true
                }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "AI 검색", tint = SamsungBlue, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { showSearchBar = false; viewModel.clearSearch(); searchMatchIndex = 0 }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "닫기", modifier = Modifier.size(18.dp))
                }
            }
        }

        // Filtered messages for display
        val displayMessages = remember(searchQuery, uiState.messages) {
            if (searchQuery.isBlank()) uiState.messages
            else uiState.messages.filter { it.text.contains(searchQuery, ignoreCase = true) }
        }

        // Messages list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(displayMessages, key = { it.id }) { message ->
                Column {
                    if (message.date != null) {
                        DateHeader(
                            date = message.date,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                    MessageBubble(
                        text = message.text,
                        isSent = message.isSent,
                        timestamp = message.timestamp,
                        imageUrl = message.attachmentPath,
                        isRead = message.isRead,
                        status = message.status,
                        onSpeak = if (ttsEnabled) ({ tts.speak(message.text) }) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                }
            }
        }

        // 전송 중 표시
        val isSending by viewModel.isSending.collectAsStateWithLifecycle()
        if (isSending) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("파일 업로드 중...", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }

        // Message input
        MessageInput(
            selectedImageUris = selectedImageUris,
            selectedFileName = selectedFileName,
            onSendMessage = { text ->
                when {
                    selectedImageUris.isNotEmpty() -> {
                        viewModel.sendMmsWithImages(text, selectedImageUris)
                        selectedImageUris = emptyList()
                    }
                    selectedFileUri != null -> {
                        android.util.Log.d("MessageDetailScreen", "Sending file: $selectedFileUri, name=$selectedFileName")
                        selectedFileUri?.let { uri -> viewModel.sendFileMessage(text, uri) }
                        selectedFileUri = null
                        selectedFileName = null
                    }
                    else -> {
                        viewModel.sendMessage(text)
                    }
                }
            },
            onAttachmentClick = { showAttachmentPicker = true },
            onRemoveImage = { index ->
                if (index != null) {
                    selectedImageUris = selectedImageUris.toMutableList().apply { removeAt(index) }
                } else {
                    selectedImageUris = emptyList()
                    selectedFileUri = null
                    selectedFileName = null
                }
            },
            aiSuggestions = aiSuggestions,
            isAiLoading = isAiSuggestLoading,
            onAiSuggestClick = { viewModel.loadAiSuggestions() },
            onAiSuggestionSelected = { suggestion ->
                // Will be handled by inserting into input - for now just send
            },
            onDismissAiSuggestions = { viewModel.clearAiSuggestions() },
            onToneConvert = { text, tone -> viewModel.convertTone(text, tone) },
            toneConvertedText = toneConvertedText,
            onToneConvertedTextConsumed = { viewModel.clearToneConvertedText() }
        )
    }

    // Attachment picker bottom sheet
    AttachmentPicker(
        isVisible = showAttachmentPicker,
        onDismiss = { showAttachmentPicker = false },
        onGallery = {
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onCamera = {
            val uri = viewModel.createTempImageUri()
            cameraImageUri = uri
            if (uri != null) cameraLauncher.launch(uri)
        },
        onFile = { fileLauncher.launch(arrayOf("*/*")) },
        onContact = {
            // 내 연락처를 vCard 형식으로 전송 (상대방이 바로 저장 가능)
            try {
                val tm = context.getSystemService(android.telephony.TelephonyManager::class.java)
                @Suppress("MissingPermission", "DEPRECATION")
                val myNumber = tm?.line1Number ?: ""
                // "나" 프로필에서 이름 가져오기
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
                viewModel.sendMessage(vCard)
            } catch (_: Exception) {
                viewModel.sendMessage("연락처 정보를 가져올 수 없습니다")
            }
        },
        onLocation = {
            // 위치 권한 확인 후 공유
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        },
        onTemplate = { showTemplatePicker = true }
    )

    // Template picker for quick insert
    if (showTemplatePicker) {
        com.bizconnect.v2.ui.components.TemplatePickerDialog(
            onDismiss = { showTemplatePicker = false },
            onTemplateSelected = { template ->
                viewModel.sendMessage(template.content)
                showTemplatePicker = false
            },
            onCreateNew = { /* 인라인 생성 폼이 다이얼로그 안에서 처리됨 */ }
        )
    }

    // AI search result dialog
    if (showAiSearchDialog) {
        AlertDialog(
            onDismissRequest = {
                showAiSearchDialog = false
                viewModel.clearAiSearchResult()
            },
            title = { Text("AI 검색 결과") },
            text = {
                if (isAiSearchLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("검색 중...")
                    }
                } else {
                    Text(aiSearchResult ?: "결과 없음")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAiSearchDialog = false
                    viewModel.clearAiSearchResult()
                }) {
                    Text("확인")
                }
            }
        )
    }

    // AI summary bottom sheet
    AiSummarySheet(
        isVisible = showAiSummarySheet,
        summary = aiSummary,
        emotion = aiEmotion,
        appointment = aiAppointment,
        isLoading = isAiLoading,
        onDismiss = { showAiSummarySheet = false },
        onAddToCalendar = { appointment ->
            try {
                val dateParts = appointment.date.split("-").map { it.toInt() }
                val timeParts = appointment.time.split(":").map { it.toInt() }
                val localDate = LocalDate.of(dateParts[0], dateParts[1], dateParts[2])
                val localTime = LocalTime.of(timeParts[0], timeParts[1])
                val beginMillis = localDate.atTime(localTime)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                val contactName = uiState.contactName
                val calendarIntent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, "${contactName} - ${appointment.description}")
                    putExtra(CalendarContract.Events.DESCRIPTION, "${contactName}님과의 약속\n${appointment.description}")
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, beginMillis + 3600000)
                    putExtra(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                    if (!appointment.location.isNullOrBlank()) {
                        putExtra(CalendarContract.Events.EVENT_LOCATION, appointment.location)
                    }
                }
                context.startActivity(calendarIntent)
            } catch (_: Exception) {
                // Fallback: open calendar without parsed time
                val calendarIntent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, appointment.description)
                }
                context.startActivity(calendarIntent)
            }
        }
    )
}
