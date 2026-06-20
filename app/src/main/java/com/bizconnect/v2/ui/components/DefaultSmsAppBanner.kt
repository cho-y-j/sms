package com.bizconnect.v2.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bizconnect.v2.ui.theme.SamsungOrange
import com.bizconnect.v2.util.DefaultSmsApp

@Composable
fun DefaultSmsAppBanner(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Re-evaluated on each (re)composition and after the role request returns.
    var isDefault by remember { mutableStateOf(DefaultSmsApp.isDefault(context)) }
    var dismissed by remember { mutableStateOf(false) }

    val roleRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDefault = DefaultSmsApp.isDefault(context)
    }

    // Never nag a user who is already the default app, or who dismissed it.
    if (isDefault || dismissed) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SamsungOrange.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = SamsungOrange,
                modifier = Modifier.padding(end = 8.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "기본 메시지 앱으로 설정",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "BizConnect를 기본 메시지 앱으로 설정하세요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    DefaultSmsApp.createRequestIntent(context)?.let { roleRequestLauncher.launch(it) }
                },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text("설정")
            }

            IconButton(
                onClick = { dismissed = true },
                modifier = Modifier.padding(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
