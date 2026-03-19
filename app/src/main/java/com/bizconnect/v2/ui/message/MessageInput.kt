package com.bizconnect.v2.ui.message

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bizconnect.v2.ui.components.AiSuggestionChips
import com.bizconnect.v2.ui.theme.SamsungBlue

@Composable
fun MessageInput(
    onSendMessage: (String) -> Unit,
    onAttachmentClick: () -> Unit,
    selectedImageUris: List<Uri> = emptyList(),
    selectedFileName: String? = null,
    onRemoveImage: (Int?) -> Unit = {},
    aiSuggestions: List<String> = emptyList(),
    isAiLoading: Boolean = false,
    onAiSuggestClick: () -> Unit = {},
    onAiSuggestionSelected: (String) -> Unit = {},
    onDismissAiSuggestions: () -> Unit = {},
    onToneConvert: (String, String) -> Unit = { _, _ -> },
    toneConvertedText: String? = null,
    onToneConvertedTextConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var messageText by remember { mutableStateOf("") }
    var showAiMenu by remember { mutableStateOf(false) }
    var showToneSubMenu by remember { mutableStateOf(false) }
    val hasContent = messageText.isNotEmpty() || selectedImageUris.isNotEmpty() || selectedFileName != null

    // Apply tone-converted text when available
    if (toneConvertedText != null) {
        messageText = toneConvertedText
        onToneConvertedTextConsumed()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp)
    ) {
        // AI suggestion chips
        AiSuggestionChips(
            suggestions = aiSuggestions,
            isLoading = isAiLoading,
            onSuggestionClick = { suggestion ->
                messageText = suggestion
                onAiSuggestionSelected(suggestion)
            },
            onDismiss = onDismissAiSuggestions
        )

        // File preview
        if (selectedFileName != null && selectedImageUris.isEmpty()) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                @Suppress("DEPRECATION")
                Icon(
                    Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = SamsungBlue,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = selectedFileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(
                    onClick = { onRemoveImage(null) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "파일 제거",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Image previews (horizontal scroll for multiple)
        if (selectedImageUris.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                selectedImageUris.forEachIndexed { index, uri ->
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "첨부 이미지 ${index + 1}",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { onRemoveImage(index) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(22.dp)
                                .background(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "이미지 제거",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = onAttachmentClick,
                modifier = Modifier.padding(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "파일 첨부",
                    tint = SamsungBlue
                )
            }

            Box {
                IconButton(
                    onClick = { showAiMenu = true },
                    modifier = Modifier.padding(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "AI 기능",
                        tint = SamsungBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showAiMenu && !showToneSubMenu,
                    onDismissRequest = { showAiMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("AI 답장 추천") },
                        onClick = {
                            showAiMenu = false
                            onAiSuggestClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("톤 변환") },
                        onClick = {
                            showToneSubMenu = true
                        }
                    )
                }

                DropdownMenu(
                    expanded = showToneSubMenu,
                    onDismissRequest = {
                        showToneSubMenu = false
                        showAiMenu = false
                    }
                ) {
                    DropdownMenuItem(
                        text = { Text("공식적으로") },
                        onClick = {
                            showToneSubMenu = false
                            showAiMenu = false
                            onToneConvert(messageText, "공식적")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("친근하게") },
                        onClick = {
                            showToneSubMenu = false
                            showAiMenu = false
                            onToneConvert(messageText, "친근한")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("간결하게") },
                        onClick = {
                            showToneSubMenu = false
                            showAiMenu = false
                            onToneConvert(messageText, "간결한")
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 100.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterStart),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    decorationBox = { innerTextField ->
                        if (messageText.isEmpty()) {
                            Text(
                                text = if (selectedImageUris.isNotEmpty()) "캡션 추가 (선택)" else "메시지를 입력하세요",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            if (hasContent) {
                IconButton(
                    onClick = {
                        onSendMessage(messageText)
                        messageText = ""
                    },
                    modifier = Modifier.padding(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "전송",
                        tint = SamsungBlue
                    )
                }
            }
        }
    }
}
