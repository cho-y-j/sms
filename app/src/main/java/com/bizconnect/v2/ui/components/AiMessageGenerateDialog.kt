package com.bizconnect.v2.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AiMessageGenerateDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onGenerated: (String) -> Unit,
    onGenerate: suspend (String) -> String
) {
    if (!isVisible) return

    var prompt by remember { mutableStateOf("") }
    var generatedText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = {
            if (!isLoading) onDismiss()
        },
        title = { Text("AI 메시지 생성") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("예: 생일 축하 메시지, 거래처 신년 인사, 미팅 확인") },
                    label = { Text("메시지 설명") },
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading,
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }

                if (error != null) {
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (generatedText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "생성된 메시지:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = generatedText ?: "",
                        onValueChange = { generatedText = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3,
                        maxLines = 6
                    )
                }
            }
        },
        confirmButton = {
            if (generatedText != null) {
                Button(
                    onClick = {
                        onGenerated(generatedText ?: return@Button)
                        onDismiss()
                    }
                ) {
                    Text("적용")
                }
            } else {
                Button(
                    onClick = {
                        if (prompt.isNotBlank()) {
                            isLoading = true
                            error = null
                            scope.launch {
                                try {
                                    val result = onGenerate(prompt)
                                    generatedText = result
                                } catch (e: Exception) {
                                    error = "생성 실패: ${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    enabled = prompt.isNotBlank() && !isLoading
                ) {
                    Text("생성")
                }
            }
        },
        dismissButton = {
            if (generatedText != null && !isLoading) {
                OutlinedButton(
                    onClick = {
                        generatedText = null
                        error = null
                    }
                ) {
                    Text("다시 생성")
                }
            }
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("취소")
            }
        }
    )
}
