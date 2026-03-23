package com.bizconnect.v2.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bizconnect.v2.ui.theme.SamsungBlue
import com.bizconnect.v2.ui.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    onBackClick: () -> Unit,
    onSignupSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 전화번호 자동 인식
    val autoPhone = remember {
        try {
            val tm = context.getSystemService(android.content.Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            val num = tm.line1Number ?: ""
            if (num.startsWith("+82")) "0${num.substring(3)}" else num
        } catch (_: Exception) { "" }
    }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf(autoPhone) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var agreeTerms by remember { mutableStateOf(false) }
    var agreeAd by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }
    // SMS 인증
    var verificationCode by remember { mutableStateOf("") }
    var isPhoneVerified by remember { mutableStateOf(false) }
    var isVerificationSent by remember { mutableStateOf(false) }
    var verificationCountdown by remember { mutableStateOf(0) }
    var verificationError by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    // 카운트다운 타이머
    androidx.compose.runtime.LaunchedEffect(verificationCountdown) {
        if (verificationCountdown > 0) {
            kotlinx.coroutines.delay(1000)
            verificationCountdown--
        }
    }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("회원가입") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로"
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
                .imePadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "간편 가입",
                style = MaterialTheme.typography.headlineSmall,
                color = SamsungBlue
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "비즈니스 기능을 이용하려면 가입하세요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; viewModel.clearError() },
                label = { Text("이름") },
                placeholder = { Text("홍길동") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = {
                    phone = it; viewModel.clearError()
                    if (isPhoneVerified) { isPhoneVerified = false; isVerificationSent = false }
                },
                label = { Text("전화번호") },
                placeholder = { Text("01012345678") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                singleLine = true,
                enabled = !isPhoneVerified,
                trailingIcon = {
                    if (isPhoneVerified) {
                        Icon(Icons.Default.Visibility, contentDescription = "인증완료",
                            tint = androidx.compose.ui.graphics.Color(0xFF22C55E))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 인증번호 발송/확인
            if (!isPhoneVerified) {
                Spacer(modifier = Modifier.height(6.dp))
                if (!isVerificationSent) {
                    Button(
                        onClick = {
                            isVerifying = true; verificationError = null
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val client = okhttp3.OkHttpClient()
                                    val body = org.json.JSONObject()
                                        .put("phone", phone).put("purpose", "signup").toString()
                                    val req = okhttp3.Request.Builder()
                                        .url("https://sm.on1.kr/api/auth/send-verification")
                                        .post(okhttp3.RequestBody.create("application/json".toMediaType(), body))
                                        .build()
                                    val resp = client.newCall(req).execute()
                                    val respBody = resp.body?.string() ?: ""
                                    withContext(Dispatchers.Main) {
                                        if (resp.isSuccessful) {
                                            isVerificationSent = true
                                            verificationCountdown = 180
                                        } else {
                                            val err = try { org.json.JSONObject(respBody).optString("error", "발송 실패") } catch (_: Exception) { "발송 실패" }
                                            verificationError = err
                                        }
                                        isVerifying = false
                                    }
                                    resp.close()
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        verificationError = "서버 연결 실패"
                                        isVerifying = false
                                    }
                                }
                            }
                        },
                        enabled = phone.length >= 10 && !isVerifying,
                        colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        if (isVerifying) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                        else Text("인증번호 발송", fontSize = 13.sp)
                    }
                } else {
                    // 인증번호 입력
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = verificationCode,
                            onValueChange = { if (it.length <= 6) verificationCode = it },
                            label = { Text("인증번호 6자리") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                isVerifying = true; verificationError = null
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val client = okhttp3.OkHttpClient()
                                        val body = org.json.JSONObject()
                                            .put("phone", phone).put("code", verificationCode)
                                            .put("purpose", "signup").toString()
                                        val req = okhttp3.Request.Builder()
                                            .url("https://sm.on1.kr/api/auth/verify-code")
                                            .post(okhttp3.RequestBody.create("application/json".toMediaType(), body))
                                            .build()
                                        val resp = client.newCall(req).execute()
                                        val respBody = resp.body?.string() ?: ""
                                        withContext(Dispatchers.Main) {
                                            if (resp.isSuccessful) {
                                                isPhoneVerified = true
                                            } else {
                                                val err = try { org.json.JSONObject(respBody).optString("error", "인증 실패") } catch (_: Exception) { "인증 실패" }
                                                verificationError = err
                                            }
                                            isVerifying = false
                                        }
                                        resp.close()
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            verificationError = "서버 연결 실패"
                                            isVerifying = false
                                        }
                                    }
                                }
                            },
                            enabled = verificationCode.length == 6 && !isVerifying,
                            colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue),
                            modifier = Modifier.height(42.dp)
                        ) {
                            if (isVerifying) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                            else Text("확인", fontSize = 13.sp)
                        }
                    }
                    // 타이머 + 재발송
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        if (verificationCountdown > 0) {
                            Text("남은 시간: ${verificationCountdown / 60}:${String.format("%02d", verificationCountdown % 60)}",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("인증번호가 만료되었습니다", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = {
                            isVerificationSent = false; verificationCode = ""
                        }, enabled = verificationCountdown == 0) {
                            Text("재발송", fontSize = 12.sp)
                        }
                    }
                }
                verificationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
            } else {
                Text("전화번호 인증 완료", fontSize = 12.sp,
                    color = androidx.compose.ui.graphics.Color(0xFF22C55E),
                    modifier = Modifier.padding(vertical = 4.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; viewModel.clearError() },
                label = { Text("이메일") },
                placeholder = { Text("example@email.com") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; viewModel.clearError() },
                label = { Text("비밀번호") },
                placeholder = { Text("4자 이상") },
                supportingText = { Text("4자 이상 입력") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility
                                else Icons.Default.VisibilityOff,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = passwordConfirm,
                onValueChange = { passwordConfirm = it; viewModel.clearError() },
                label = { Text("비밀번호 확인") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    viewModel.signup(name, phone, email, password, passwordConfirm, onSignupSuccess)
                }),
                isError = passwordConfirm.isNotEmpty() && password != passwordConfirm,
                supportingText = {
                    if (passwordConfirm.isNotEmpty() && password != passwordConfirm) {
                        Text("비밀번호가 일치하지 않습니다", color = MaterialTheme.colorScheme.error)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 이용약관 동의
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // 전체 동의
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val newState = !(agreeTerms && agreeAd)
                        agreeTerms = newState
                        agreeAd = newState
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Checkbox(
                    checked = agreeTerms && agreeAd,
                    onCheckedChange = {
                        agreeTerms = it; agreeAd = it
                    }
                )
                Text("전체 동의", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 14.sp)
            }

            // 서비스 이용약관 동의
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Checkbox(
                    checked = agreeTerms,
                    onCheckedChange = { agreeTerms = it }
                )
                Text("[필수] 서비스 이용약관 및 개인정보 처리방침 동의", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { showTerms = true }) {
                    Text("보기", fontSize = 11.sp, color = SamsungBlue)
                }
            }

            // 광고성 문자 주의사항 동의
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Checkbox(
                    checked = agreeAd,
                    onCheckedChange = { agreeAd = it }
                )
                Text("[필수] 광고성 문자 발송 규정 확인 및 동의", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 주의사항 안내
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "⚠️ 광고성 문자는 정보통신망법에 따라 반드시 유료 API를 통해 발송해야 합니다. 폰 문자로 광고성 문자를 발송하면 통신사 차단 및 법적 제재(과태료 최대 3,000만원)를 받을 수 있으며, 이에 대한 모든 책임은 이용자에게 있습니다.",
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Error message
            viewModel.error?.let { errorMsg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMsg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.signup(name, phone, email, password, passwordConfirm, onSignupSuccess)
                },
                enabled = !viewModel.isLoading && name.isNotBlank() && phone.isNotBlank()
                    && email.isNotBlank() && password.length >= 4 && password == passwordConfirm
                    && agreeTerms && agreeAd && isPhoneVerified,
                colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("가입하기", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 이용약관 다이얼로그
    if (showTerms) {
        AlertDialog(
            onDismissRequest = { showTerms = false },
            title = { Text("BizConnect 이용약관", fontSize = 16.sp) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = """
(주)다인온 BizConnect 서비스 이용약관

제1조 (목적)
본 약관은 (주)다인온이 제공하는 BizConnect 서비스의 이용조건 및 절차를 규정함을 목적으로 합니다.

제2조 (서비스 내용)
① 문자메시지(SMS/LMS/MMS) 발송 서비스
② AI 기반 메시지 작성 지원
③ 고객 관리 및 연락처 관리
④ 대량 문자 발송 (유료)

제3조 (이용자의 의무)
① 이용자는 관련 법률을 준수하여 서비스를 이용해야 합니다.
② 광고성 문자는 반드시 유료 API를 통해 발송해야 하며, 폰 문자를 이용한 광고성 문자 발송은 엄격히 금지됩니다.
③ 광고성 문자 발송 시 정보통신망법에 따라 다음을 준수해야 합니다:
  - (광고) 표기 의무
  - 080 수신거부번호 포함
  - 야간(21시~08시) 발송 금지
  - 수신자의 사전 동의 확보
④ 이를 위반하여 발생하는 모든 법적 책임(과태료, 민·형사상 책임)은 전적으로 이용자에게 있습니다.

제4조 (서비스 제한)
① 스팸, 불법 문자 발송이 확인될 경우 즉시 계정을 정지합니다.
② 통신사 정책에 따라 동일 내용 대량 발송 시 차단될 수 있습니다.
③ 폰 발송은 1분당 20건으로 제한됩니다.

제5조 (면책)
① (주)다인온은 이용자의 불법적인 문자 발송으로 인한 법적 분쟁에 대해 책임을 지지 않습니다.
② 통신사 정책 변경에 의한 서비스 제한에 대해 책임을 지지 않습니다.

제6조 (개인정보 처리)
① 수집항목: 이름, 전화번호, 이메일
② 이용목적: 서비스 제공, 본인 확인
③ 보유기간: 탈퇴 시까지
④ 개인정보는 암호화하여 안전하게 관리합니다.

(주)다인온
                        """.trimIndent(),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTerms = false; agreeTerms = true }) {
                    Text("동의", color = SamsungBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTerms = false }) {
                    Text("닫기")
                }
            }
        )
    }
}
