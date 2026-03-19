package com.bizconnect.v2.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bizconnect.v2.ui.theme.SamsungBlue
import com.bizconnect.v2.ui.theme.SamsungRed
import com.bizconnect.v2.ui.viewmodel.AdminSettingsViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSettingsScreen(
    onBackClick: () -> Unit = {},
    viewModel: AdminSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            Toast.makeText(context, "설정이 저장되었습니다", Toast.LENGTH_SHORT).show()
            viewModel.clearSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("관리자 설정") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save() }) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = "저장",
                            tint = SamsungBlue
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // === Section 1: 문자 단가 설정 ===
            SettingsSectionHeader("문자 단가 설정")

            CostTextField(
                label = "단문(SMS)",
                value = uiState.smsCost,
                onValueChange = { viewModel.updateSmsCost(it) },
                suffix = "원"
            )

            CostTextField(
                label = "장문(LMS)",
                value = uiState.lmsCost,
                onValueChange = { viewModel.updateLmsCost(it) },
                suffix = "원"
            )

            CostTextField(
                label = "MMS",
                value = uiState.mmsCost,
                onValueChange = { viewModel.updateMmsCost(it) },
                suffix = "원"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // === Section 2: 일일 발송 한도 ===
            SettingsSectionHeader("일일 발송 한도")

            CostTextField(
                label = "무료 회원",
                value = uiState.freeDailyLimit,
                onValueChange = { viewModel.updateFreeDailyLimit(it) },
                suffix = "건/일",
                isInteger = true
            )

            CostTextField(
                label = "유료 회원",
                value = uiState.paidDailyLimit,
                onValueChange = { viewModel.updatePaidDailyLimit(it) },
                suffix = "건/일",
                isInteger = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            // === Section 3: 구독 정보 ===
            SettingsSectionHeader("구독 정보")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "현재 구독",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (uiState.subscriptionTier == "paid") "유료" else "무료",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (uiState.subscriptionTier == "paid") SamsungBlue
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "월 구독료",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "\u20A94,900",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Button(
                onClick = {
                    Toast.makeText(context, "구독 변경 기능은 준비 중입니다", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue)
            ) {
                Text("구독 변경")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // === Section 4: 충전 잔액 ===
            SettingsSectionHeader("충전 잔액")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "현재 잔액",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "\u20A9${NumberFormat.getNumberInstance(Locale.KOREA).format(uiState.creditBalance)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SamsungBlue
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.resetBalance() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SamsungRed)
                ) {
                    Text("잔액 초기화")
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = { viewModel.addTestBalance() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue)
                ) {
                    Text("테스트 충전 (\u20A910,000)")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save button
            Button(
                onClick = { viewModel.save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue)
            ) {
                Text("설정 저장")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CostTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suffix: String,
    isInteger: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (isInteger) {
                if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                    onValueChange(newValue)
                }
            } else {
                if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                    onValueChange(newValue)
                }
            }
        },
        label = { Text(label) },
        suffix = { Text(suffix) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isInteger) KeyboardType.Number else KeyboardType.Decimal
        ),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}
