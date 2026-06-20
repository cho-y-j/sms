package com.bizconnect.v2.ui.business

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bizconnect.v2.ui.viewmodel.BusinessHomeViewModel
import java.text.NumberFormat
import java.util.Locale

private val SamsungBlue = Color(0xFF0381FE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricingScreen(
    onBackClick: () -> Unit,
    onPaymentClick: ((amount: Int, goodsName: String, paymentType: String, payMethod: String) -> Unit)? = null,
    viewModel: BusinessHomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    var selectedPlan by remember { mutableStateOf(viewModel.subscriptionTier) }
    var selectedChargeAmount by remember { mutableIntStateOf(0) }
    var selectedPaymentMethod by remember { mutableStateOf("card") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "구독 및 충전",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로"
                        )
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Section 1: Subscription Plans
            SectionHeader(title = "구독 플랜")
            Spacer(modifier = Modifier.height(12.dp))

            val currentTier = viewModel.subscriptionTier

            // Free plan
            PlanCard(
                planName = "무료",
                description = "하루 50건 · 콜백·서류 템플릿 체험",
                price = null,
                isSelected = selectedPlan == "free",
                isCurrent = currentTier == "free",
                onClick = { selectedPlan = "free" }
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Paid plan
            PlanCard(
                planName = "유료 (Business)",
                description = "통화 후 명함 자동회신 · 서류 템플릿 무제한 · 하루 약 150건",
                price = "₩4,900/월",
                isSelected = selectedPlan == "paid",
                isCurrent = currentTier == "paid",
                onClick = { selectedPlan = "paid" }
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Premium plan
            PlanCard(
                planName = "프리미엄 (Premium)",
                description = "비즈니스 혜택 전부 · 우선 지원 · 외부 연동(개발자용)",
                price = "₩9,900/월",
                isSelected = selectedPlan == "premium",
                isCurrent = currentTier == "premium",
                onClick = { selectedPlan = "premium" }
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 결제 수단 선택
            Text(
                text = "결제 수단",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "card" to "💳 카드",
                    "kakaopay" to "🟡 카카오",
                    "naverpay" to "🟢 네이버"
                ).forEach { (method, label) ->
                    OutlinedButton(
                        onClick = { selectedPaymentMethod = method },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedPaymentMethod == method) SamsungBlue.copy(alpha = 0.1f) else Color.Transparent,
                            contentColor = if (selectedPaymentMethod == method) SamsungBlue else MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (selectedPlan == "free") {
                        Toast.makeText(context, "무료 플랜은 결제가 필요 없습니다", Toast.LENGTH_SHORT).show()
                    } else {
                        val amount = if (selectedPlan == "premium") 9900 else 4900
                        val name = if (selectedPlan == "premium") "BizConnect Premium 구독" else "BizConnect Business 구독"
                        if (onPaymentClick != null) {
                            onPaymentClick(amount, name, "subscription", selectedPaymentMethod)
                        } else {
                            Toast.makeText(context, "결제 화면으로 이동합니다", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = selectedPlan != "free",
                colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("구독하기", fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(8.dp))
            // 결제 신뢰 정보 (자동결제·해지·환불) — 정기결제 고지는 전자상거래법상 필수
            Text(
                text = "매월 자동 결제되며 언제든지 해지할 수 있어요. 판매자 (주)다인온 · 결제 전 청구되지 않습니다.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: Credit Charge
            SectionHeader(title = "문자 충전")
            Spacer(modifier = Modifier.height(12.dp))

            val chargeAmounts = listOf(10_000, 50_000, 100_000)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chargeAmounts.forEach { amount ->
                    val label = "₩${numberFormat.format(amount)}"
                    val isSelected = selectedChargeAmount == amount
                    OutlinedButton(
                        onClick = { selectedChargeAmount = amount },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) SamsungBlue.copy(alpha = 0.1f)
                            else Color.Transparent,
                            contentColor = if (isSelected) SamsungBlue
                            else MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Unit prices
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "단가 안내",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PriceRow("단문(SMS)", "9.8원")
                    PriceRow("장문(LMS)", "29원")
                    PriceRow("MMS", "63원")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (selectedChargeAmount > 0 && onPaymentClick != null) {
                        val name = "크레딧 ${numberFormat.format(selectedChargeAmount)}원 충전"
                        onPaymentClick(selectedChargeAmount, name, "credit_charge", selectedPaymentMethod)
                    }
                },
                enabled = selectedChargeAmount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("충전하기", fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // Section 3: Current Status
            SectionHeader(title = "현재 상태")
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Column {
                    val tierDisplay = when (viewModel.subscriptionTier) {
                        "premium" -> "프리미엄"
                        "paid" -> "유료 (Business)"
                        else -> "무료"
                    }
                    val balanceText = numberFormat.format(viewModel.creditBalance.toLong())
                    StatusRow("현재 구독", tierDisplay)
                    Spacer(modifier = Modifier.height(8.dp))
                    StatusRow("충전 잔액", "${balanceText}원")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun PlanCard(
    planName: String,
    description: String,
    price: String?,
    isSelected: Boolean,
    isCurrent: Boolean = false,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) SamsungBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val bgColor = if (isSelected) SamsungBlue.copy(alpha = 0.05f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .background(bgColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = SamsungBlue)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = planName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isCurrent) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "현재",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .background(SamsungBlue, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = description,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (price != null) {
            Text(
                text = price,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = SamsungBlue
            )
        }
    }
}

@Composable
private fun PriceRow(label: String, price: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = price,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
