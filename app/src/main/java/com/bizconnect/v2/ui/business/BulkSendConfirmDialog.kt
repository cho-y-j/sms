package com.bizconnect.v2.ui.business

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale

enum class MessageType(val label: String) {
    SMS("단문(SMS)"),
    LMS("장문(LMS)"),
    MMS("MMS")
}

@Composable
fun BulkSendConfirmDialog(
    recipientCount: Int,
    messageType: MessageType,
    estimatedCost: Double,
    dailySentCount: Int,
    dailyLimit: Int,
    creditBalance: Double,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onServerSend: (() -> Unit)? = null
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    val exceedsLimit = dailySentCount + recipientCount > dailyLimit
    val isLargeVolume = recipientCount > 20

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "발송 확인",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ConfirmRow("수신자", "${numberFormat.format(recipientCount)}명")
                Spacer(modifier = Modifier.height(8.dp))
                ConfirmRow("메시지 유형", messageType.label)
                Spacer(modifier = Modifier.height(8.dp))

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Spacer(modifier = Modifier.height(8.dp))
                ConfirmRow(
                    "오늘 전체 발송",
                    "${numberFormat.format(dailySentCount)} / ${numberFormat.format(dailyLimit)}건"
                )
                Spacer(modifier = Modifier.height(8.dp))
                ConfirmRow(
                    "잔여 충전금",
                    "${numberFormat.format(creditBalance.toLong())}원"
                )

                // 통신사 차단 경고
                if (exceedsLimit) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "⚠ 일일 발송 한도를 초과합니다.\n통신사에서 문자 발송이 차단될 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // 대량 발송 경고 + 유료 서버 발송 유도
                if (isLargeVolume || exceedsLimit) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color(0xFFFFF3E0),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "광고성/대량 문자 안내",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• 폰 발송은 20건/분 순차 발송됩니다\n" +
                                    "• 광고성 문자는 폰 발송이 법적으로 금지됩니다\n" +
                                    "• 광고성 문자는 반드시 유료 웹 발송을 이용하세요\n" +
                                    "• 위반 시 과태료 최대 3,000만원 부과\n" +
                                    "• 이에 대한 법적 책임은 발송자에게 있습니다\n" +
                                    "• (주)다인온은 불법 발송에 대한 책임을 지지 않습니다",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5D4037)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if ((isLargeVolume || exceedsLimit) && onServerSend != null) {
                    TextButton(onClick = onServerSend) {
                        Text("유료 웹 발송", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(
                    onClick = onConfirm,
                    enabled = !exceedsLimit
                ) {
                    Text(if (exceedsLimit) "한도 초과" else "폰 발송")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
private fun ConfirmRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
