package com.bizconnect.v2.ui.payment

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * 결제 화면 - NICE Pay WebView
 *
 * 결제 프로세스:
 * 1. ViewModel에서 서버 /api/payment/prepare 호출 → checkoutUrl 수신
 * 2. WebView에서 checkoutUrl 로드 → NICE SDK 결제창 표시
 * 3. 결제 완료 → 서버 /api/payment/approve → bizconnect:// 딥링크 리다이렉트
 * 4. 딥링크 수신 → 결제 결과 표시
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PaymentScreen(
    amount: Int,
    goodsName: String,
    paymentType: String, // "subscription" or "credit_charge"
    payMethod: String = "card", // "card", "kakaopay", "naverpay"
    onPaymentComplete: (success: Boolean, orderId: String?, tid: String?) -> Unit,
    onBack: () -> Unit,
    viewModel: PaymentViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(amount, payMethod) {
        viewModel.preparePayment(amount, goodsName, paymentType, payMethod)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("결제") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = uiState.error ?: "오류가 발생했습니다",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = { viewModel.preparePayment(amount, goodsName, paymentType, payMethod) }) {
                            Text("다시 시도")
                        }
                    }
                }
                uiState.paymentSuccess -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "결제가 완료되었습니다!",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "${amount}원",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(onClick = {
                            onPaymentComplete(true, uiState.orderId, uiState.tid)
                        }) {
                            Text("확인")
                        }
                    }
                }
                uiState.checkoutUrl != null -> {
                    // WebView로 결제 페이지 로드
                    var webViewRef by remember { mutableStateOf<WebView?>(null) }

                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        val scheme = Uri.parse(url).scheme?.lowercase() ?: return true

                                        // Block dangerous schemes
                                        if (scheme in listOf("tel", "sms", "javascript", "data", "blob")) {
                                            return true
                                        }

                                        // bizconnect:// 딥링크 처리
                                        if (scheme == "bizconnect") {
                                            val uri = Uri.parse(url)
                                            when (uri.host) {
                                                "payment" -> {
                                                    when (uri.pathSegments.firstOrNull()) {
                                                        "success" -> {
                                                            val orderId = uri.getQueryParameter("orderId")
                                                            val tid = uri.getQueryParameter("tid")
                                                            // Clear WebView data after payment
                                                            view?.clearCache(true)
                                                            view?.clearHistory()
                                                            viewModel.onPaymentSuccess(orderId, tid)
                                                        }
                                                        "fail", "error" -> {
                                                            val code = uri.getQueryParameter("code")
                                                            val msg = uri.getQueryParameter("msg")
                                                            view?.clearCache(true)
                                                            view?.clearHistory()
                                                            viewModel.onPaymentFailed(code, msg)
                                                        }
                                                    }
                                                }
                                            }
                                            return true
                                        }

                                        // Allow only http, https, intent schemes
                                        if (scheme in listOf("https", "http")) {
                                            return false
                                        }

                                        // intent:// scheme (카카오페이, 네이버페이 등 외부 앱)
                                        if (scheme == "intent") {
                                            try {
                                                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                                                ctx.startActivity(intent)
                                            } catch (_: Exception) { }
                                            return true
                                        }

                                        // Block all other schemes
                                        return true
                                    }
                                }

                                loadUrl(uiState.checkoutUrl ?: return@apply)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
