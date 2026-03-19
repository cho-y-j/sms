package com.bizconnect.v2.ui.business

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    onDismiss: () -> Unit
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

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
                    "예상 비용",
                    "${numberFormat.format(estimatedCost.toLong())}원"
                )
                Spacer(modifier = Modifier.height(8.dp))
                ConfirmRow(
                    "일일 한도",
                    "${numberFormat.format(dailySentCount)} / ${numberFormat.format(dailyLimit)}건"
                )
                Spacer(modifier = Modifier.height(8.dp))
                ConfirmRow(
                    "잔여 충전금",
                    "${numberFormat.format(creditBalance.toLong())}원"
                )

                if (dailySentCount + recipientCount > dailyLimit) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "일일 발송 한도를 초과합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (estimatedCost > creditBalance) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "충전금이 부족합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = dailySentCount + recipientCount <= dailyLimit
            ) {
                Text("발송")
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
