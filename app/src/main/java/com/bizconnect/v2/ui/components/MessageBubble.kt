package com.bizconnect.v2.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bizconnect.v2.ui.theme.ReceivedBubbleColor
import com.bizconnect.v2.ui.theme.ReceivedBubbleDark
import com.bizconnect.v2.ui.theme.ReceivedMessageBubbleShape
import android.provider.Telephony
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import com.bizconnect.v2.ui.theme.SentBubbleColor
import com.bizconnect.v2.ui.theme.SentBubbleDark
import com.bizconnect.v2.ui.theme.SentMessageBubbleShape

private val URL_REGEX = Regex("""(https?://[^\s]+)""")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    text: String,
    isSent: Boolean,
    timestamp: String = "",
    imageUrl: String? = null,
    isRead: Boolean = false,
    status: Int = Telephony.Sms.STATUS_COMPLETE,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val backgroundColor = when {
        isSent -> if (isDark) SentBubbleDark else SentBubbleColor
        else -> if (isDark) ReceivedBubbleDark else ReceivedBubbleColor
    }

    val textColor = if (isSent || (!isSent && !isDark)) {
        if (isSent) Color.White else MaterialTheme.colorScheme.onSurface
    } else {
        Color.White
    }

    val linkColor = if (isSent) Color(0xFFBBDEFB) else Color(0xFF1976D2)

    val shape = if (isSent) SentMessageBubbleShape else ReceivedMessageBubbleShape
    val horizontalAlignment = if (isSent) Alignment.CenterEnd else Alignment.CenterStart

    // Build annotated string with clickable URLs
    val annotatedText = remember(text, textColor, linkColor) {
        buildAnnotatedString {
            append(text)
            // Style entire text
            addStyle(
                SpanStyle(color = textColor),
                0, text.length
            )
            // Find and annotate URLs
            URL_REGEX.findAll(text).forEach { match ->
                addStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    ),
                    match.range.first, match.range.last + 1
                )
                addStringAnnotation(
                    tag = "URL",
                    annotation = match.value,
                    start = match.range.first,
                    end = match.range.last + 1
                )
            }
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = horizontalAlignment
    ) {
        Column(
            modifier = Modifier
                .wrapContentWidth()
                .background(backgroundColor, shape)
                .combinedClickable(
                    onClick = { },
                    onLongClick = {
                        if (text.isNotEmpty()) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
                            Toast.makeText(context, "메시지가 복사되었습니다", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                .padding(12.dp)
        ) {
            // Image
            if (!imageUrl.isNullOrEmpty()) {
                val imageModel = if (imageUrl.startsWith("content://") || imageUrl.startsWith("file://")) {
                    android.net.Uri.parse(imageUrl)
                } else {
                    imageUrl
                }
                AsyncImage(
                    model = imageModel,
                    contentDescription = "첨부 이미지",
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        ),
                    contentScale = ContentScale.Crop
                )
            }

            // Message text with clickable links + selectable
            if (text.isNotEmpty()) {
                SelectionContainer {
                    ClickableText(
                        text = annotatedText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = if (!imageUrl.isNullOrEmpty()) 8.dp else 0.dp),
                        onClick = { offset ->
                            annotatedText.getStringAnnotations("URL", offset, offset)
                                .firstOrNull()?.let { annotation ->
                                    try {
                                        uriHandler.openUri(annotation.item)
                                    } catch (_: Exception) { }
                                }
                        }
                    )
                }
            }

            // Timestamp + send status
            if (timestamp.isNotEmpty() || (isSent && status == Telephony.Sms.STATUS_PENDING)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (isSent && status == Telephony.Sms.STATUS_PENDING) {
                        // 전송 중 스피너
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp).padding(end = 4.dp),
                            strokeWidth = 1.5.dp,
                            color = if (textColor == Color.White) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "전송중",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (textColor == Color.White) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (isSent && status == Telephony.Sms.STATUS_FAILED) {
                        // 전송 실패
                        Text(
                            text = "$timestamp ⚠ 전송실패",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFF5252)
                        )
                    } else {
                        // 정상 — 시간만 표시
                        Text(
                            text = timestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (textColor == Color.White) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
