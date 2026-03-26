package com.bizconnect.v2.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.preferences.AppPreferences
import com.bizconnect.v2.data.remote.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject

data class PaymentUiState(
    val loading: Boolean = false,
    val checkoutUrl: String? = null,
    val paymentSuccess: Boolean = false,
    val orderId: String? = null,
    val tid: String? = null,
    val error: String? = null
)

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val tokenManager: TokenManager
) : ViewModel() {

    companion object {
        private const val BASE_URL = "https://sm.on1.kr"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val _uiState = MutableStateFlow(PaymentUiState())
    val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

    fun preparePayment(amount: Int, goodsName: String, paymentType: String, payMethod: String = "card") {
        viewModelScope.launch {
            _uiState.value = PaymentUiState(loading = true)

            try {
                val result = withContext(Dispatchers.IO) {
                    val body = JSONObject().apply {
                        put("amount", amount)
                        put("goodsName", goodsName)
                        put("method", payMethod)
                        put("type", paymentType)
                    }

                    val requestBuilder = Request.Builder()
                        .url("$BASE_URL/api/payment/prepare")
                        .addHeader("Content-Type", "application/json")
                        .post(body.toString().toRequestBody(JSON_TYPE))

                    val response = if (appPreferences.getAccessToken() != null) {
                        tokenManager.executeAuthenticated(requestBuilder)
                    } else {
                        tokenManager.getClient().newCall(requestBuilder.build()).execute()
                    }
                    val responseBody = response.body?.string() ?: "{}"
                    response.close()

                    if (response.isSuccessful) {
                        val json = JSONObject(responseBody)
                        val checkoutUrl = json.optString("checkoutUrl", "")
                        val orderId = json.optString("orderId", "")
                        Pair(checkoutUrl, orderId)
                    } else {
                        val errorMsg = try {
                            JSONObject(responseBody).optString("error", "결제 준비 실패")
                        } catch (_: Exception) { "결제 준비 실패 (${response.code})" }
                        throw Exception(errorMsg)
                    }
                }

                _uiState.value = PaymentUiState(
                    checkoutUrl = result.first,
                    orderId = result.second
                )
            } catch (e: Exception) {
                _uiState.value = PaymentUiState(error = e.message ?: "결제 준비 중 오류 발생")
            }
        }
    }

    fun onPaymentSuccess(orderId: String?, tid: String?) {
        _uiState.value = PaymentUiState(
            paymentSuccess = true,
            orderId = orderId,
            tid = tid
        )
    }

    fun onPaymentFailed(code: String?, msg: String?) {
        _uiState.value = PaymentUiState(
            error = msg ?: "결제 실패 ($code)"
        )
    }
}
