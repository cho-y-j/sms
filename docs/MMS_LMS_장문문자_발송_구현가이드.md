# Android 장문 문자(LMS) MMS 발송 구현 가이드

## 최종 작성일: 2026-03-18
## 프로젝트: BizConnect V2
## 테스트 환경: Samsung Galaxy, LG U+ (45006), Android 14+

---

## 1. 문제 정의

### 증상
- 70자 초과 한글 메시지를 보내면 **수신자에게 2건의 SMS로 분리되어 도착**
- 같은 통신사끼리는 1건으로 도착하지만, **다른 통신사로 보내면 2건으로 분리**
- 삼성 기본 메시지 앱은 장문을 1건으로 정상 발송

### 원인
Android `SmsManager.sendMultipartTextMessage()`는 장문을 여러 개의 SMS로 분할 전송한다.
각 SMS에 UDH(User Data Header) 연결 정보가 포함되어 수신 측에서 재조합해야 하는데,
**한국 통신사 간 연동(inter-carrier)에서 UDH가 제거되거나 무시되어 분리 도착**한다.

삼성 메시지 앱은 장문을 SMS가 아닌 **MMS(LMS: Long Message Service)**로 발송하여
단일 메시지로 도착하게 한다.

### 해결 방법
장문 메시지를 **MMS PDU로 구성하여 통신사 MMSC에 직접 HTTP POST**한다.
`SmsManager.sendMultimediaMessage()` API가 아닌, MMSC에 직접 HTTP 통신하는 방식이다.

---

## 2. 시도했던 방법들과 실패 원인

### 2-1. sendMultipartTextMessage (기본 방식)
```kotlin
smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
```
- **결과**: 같은 통신사 OK, 다른 통신사 2건 분리
- **원인**: SMS 프로토콜 한계. 한국 통신사 간 UDH 연결 정보 누락

### 2-2. SmsManager.sendMultimediaMessage() API
```kotlin
smsManager.sendMultimediaMessage(context, pduUri, null, null, sentIntent)
```
- **결과**: MMS 발송 실패, 삼성 시스템이 자동으로 SMS 재시도 → **이중 발송 (2건)**
- **원인 1**: FileProvider URI를 시스템 MMS 서비스가 읽지 못함
- **원인 2**: MMS 실패 시 삼성 시스템이 자동으로 multipart SMS로 재전송
- **원인 3**: 비동기 API라서 실패 감지 후 즉시 폴백 불가능
- **교훈**: 이 API는 절대 사용하지 말 것. 이중 발송 위험이 높음

### 2-3. 직접 HTTP POST (APN 설정 읽기)
```kotlin
context.contentResolver.query(
    Uri.parse("content://telephony/carriers/current"), ...)
```
- **결과**: `No permission to access APN settings` 에러
- **원인**: Android 10+에서 APN 설정은 시스템 앱만 읽을 수 있음 (READ_APN_SETTINGS는 signature 권한)

### 2-4. 직접 HTTP POST (하드코딩 MMSC) — 최종 성공
- **결과**: 성공!!! 단, 처음에는 `Cleartext HTTP traffic not permitted` 에러
- **원인**: AndroidManifest의 `android:usesCleartextTraffic="false"` 설정
- **해결**: `network_security_config.xml`로 MMSC 도메인만 HTTP 허용

---

## 3. 최종 성공 구현 (전체 코드)

### 3-1. 전체 아키텍처

```
장문 메시지 발송 흐름:

사용자가 메시지 입력 (70자 초과)
    ↓
SmsManager.divideMessage()로 분할 여부 확인
    ↓ (parts.size > 1이면 장문)
TelephonyManager.networkOperator로 통신사 식별 (MCC/MNC)
    ↓
통신사별 MMSC URL 매핑 (하드코딩)
    ↓
MMS PDU 생성 (OMA-TS-MMS_ENC 스펙, M-Send.req)
    ↓
ConnectivityManager로 MMS 셀룰러 네트워크 요청 (5초 타임아웃)
    ↓ (획득 실패 시 기본 네트워크 사용)
MMSC에 HTTP POST (Content-Type: application/vnd.wap.mms-message)
    ↓
M-Send.conf 응답 확인 (0x8C 0x81)
    ↓ 성공 → 1건 MMS로 도착
    ↓ 실패 → multipart SMS 폴백 (2건)
```

### 3-2. SmsSender.kt (핵심 코드)

```kotlin
package com.bizconnect.v2.util

import android.content.ContentValues
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SmsSender(private val context: Context) {

    private val TAG = "SmsSender"

    /**
     * 메시지 발송 메인 함수.
     * 단문: SMS, 장문: MMS(MMSC 직접 HTTP) → 실패 시 multipart SMS 폴백
     */
    fun sendMessage(phoneNumber: String, message: String) {
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        val parts = smsManager.divideMessage(message)

        if (parts.size > 1) {
            // === 장문: MMS로 발송 시도 ===
            Log.d(TAG, "장문 메시지 (${message.length}자, ${parts.size}파트) → MMS 시도")

            val sentAsMms = trySendViaDirectHttp(phoneNumber, message)

            if (!sentAsMms) {
                // MMS 실패 → multipart SMS 폴백
                Log.w(TAG, "MMS 실패, multipart SMS로 폴백")
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }
        } else {
            // === 단문: 일반 SMS ===
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
        }
    }

    // ============================================================
    // MMS 직접 HTTP POST (핵심)
    // ============================================================

    /**
     * MMSC에 직접 HTTP POST로 MMS 발송.
     * sendMultimediaMessage() API를 사용하지 않음 (이중 발송 위험).
     *
     * @return true: MMS 발송 성공, false: 실패
     */
    private fun trySendViaDirectHttp(phoneNumber: String, message: String): Boolean {
        return try {
            // 1. 통신사 식별 및 MMSC URL 결정
            val mmsc = getKoreanCarrierMmsc()
            if (mmsc == null) {
                Log.e(TAG, "알 수 없는 통신사, MMSC URL 결정 불가")
                return false
            }
            Log.d(TAG, "통신사: ${mmsc.carrierName}, MMSC: ${mmsc.url}")

            // 2. MMS PDU 생성
            val pduBytes = buildTextOnlyMmsPdu(phoneNumber, message)

            // 3. MMSC에 HTTP POST
            sendPduViaHttp(pduBytes, mmsc)
        } catch (e: Exception) {
            Log.e(TAG, "MMS 직접 발송 실패: ${e.message}", e)
            false
        }
    }

    // ============================================================
    // 통신사 식별 (MCC/MNC 기반)
    // ============================================================

    /**
     * TelephonyManager.networkOperator (MCC+MNC)로 한국 통신사 식별.
     * APN 설정은 시스템 권한이 필요하므로 읽을 수 없음.
     * 대신 통신사별 MMSC URL을 하드코딩.
     *
     * 한국 MCC: 450
     * MNC: 05=SKT, 08=KT, 02=KT, 06=LG U+, 04=KT MVNO, 11=SKT MVNO
     */
    private fun getKoreanCarrierMmsc(): KoreanMmsc? {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return null
        val operator = tm.networkOperator ?: tm.simOperator ?: ""
        Log.d(TAG, "Network operator (MCC+MNC): $operator")

        return when {
            // SKT (MNC: 05)
            operator.startsWith("45005") -> KoreanMmsc(
                carrierName = "SKT",
                url = "http://omms.nate.com:9082/oma_mms",
                proxy = "smart.nate.com",
                port = 9093
            )
            // KT (MNC: 08, 02)
            operator.startsWith("45008") || operator.startsWith("45002") -> KoreanMmsc(
                carrierName = "KT",
                url = "http://mmsc.ktfwing.com:9082",
                proxy = "",
                port = 80
            )
            // LG U+ (MNC: 06)
            operator.startsWith("45006") -> KoreanMmsc(
                carrierName = "LG U+",
                url = "http://omammsc.uplus.co.kr:9084",
                proxy = "",
                port = 80
            )
            // KT 알뜰폰 (MNC: 04)
            operator.startsWith("45004") -> KoreanMmsc(
                carrierName = "KT MVNO",
                url = "http://mmsc.ktfwing.com:9082",
                proxy = "",
                port = 80
            )
            // SKT 알뜰폰 (MNC: 11)
            operator.startsWith("45011") -> KoreanMmsc(
                carrierName = "SKT MVNO",
                url = "http://omms.nate.com:9082/oma_mms",
                proxy = "smart.nate.com",
                port = 9093
            )
            else -> {
                Log.w(TAG, "미지원 통신사: $operator")
                null
            }
        }
    }

    // ============================================================
    // HTTP POST to MMSC
    // ============================================================

    /**
     * MMS PDU를 MMSC에 HTTP POST.
     *
     * 핵심 포인트:
     * 1. MMS 전용 셀룰러 네트워크 획득 시도 (WiFi에서는 MMS 불가할 수 있음)
     * 2. 네트워크 획득 실패 시 기본 네트워크로 시도 (일부 통신사는 기본 네트워크로도 가능)
     * 3. 프록시가 있는 통신사(SKT)는 프록시 경유
     * 4. Content-Type: application/vnd.wap.mms-message
     * 5. 응답에서 M-Send.conf (0x8C 0x81) 확인
     */
    private fun sendPduViaHttp(pduBytes: ByteArray, mmsc: KoreanMmsc): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false

        // MMS 전용 셀룰러 네트워크 획득 (5초 타임아웃)
        val network = acquireMmsNetwork(cm)

        return try {
            val url = URL(mmsc.url)

            // HTTP 연결 생성 (프록시 유무에 따라)
            val connection = if (mmsc.proxy.isNotBlank()) {
                val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(mmsc.proxy, mmsc.port))
                if (network != null) network.openConnection(url, proxy) as HttpURLConnection
                else url.openConnection(proxy) as HttpURLConnection
            } else {
                if (network != null) network.openConnection(url) as HttpURLConnection
                else url.openConnection() as HttpURLConnection
            }

            // HTTP 요청 설정
            connection.apply {
                doOutput = true
                doInput = true
                requestMethod = "POST"
                connectTimeout = 10_000  // 10초
                readTimeout = 10_000     // 10초
                setRequestProperty("Content-Type", "application/vnd.wap.mms-message")
                setRequestProperty("Accept", "*/*, application/vnd.wap.mms-message, application/vnd.wap.sic")
                setRequestProperty("Content-Length", pduBytes.size.toString())
            }

            // PDU 전송
            DataOutputStream(connection.outputStream).use { out ->
                out.write(pduBytes)
                out.flush()
            }

            // 응답 읽기
            val responseCode = connection.responseCode
            val responseBytes = try {
                connection.inputStream.readBytes()
            } catch (_: Exception) {
                connection.errorStream?.readBytes() ?: byteArrayOf()
            }
            connection.disconnect()

            Log.d(TAG, "MMSC 응답: $responseCode, ${responseBytes.size} bytes")

            // 성공 판정
            if (responseCode == HttpURLConnection.HTTP_OK && responseBytes.isNotEmpty()) {
                // M-Send.conf 헤더: 0x8C (Message-Type) 0x81 (m-send-conf)
                if (responseBytes.size >= 2
                    && responseBytes[0] == 0x8C.toByte()
                    && responseBytes[1] == 0x81.toByte()) {
                    Log.d(TAG, "MMS 발송 성공 (M-Send.conf 수신)")
                    return true
                }
                Log.d(TAG, "200 OK 응답, 성공으로 처리")
                return true
            }

            Log.w(TAG, "MMSC 에러 응답: $responseCode")
            false
        } catch (e: Exception) {
            Log.e(TAG, "MMSC HTTP 통신 에러: ${e.message}", e)
            false
        } finally {
            releaseMmsNetwork(cm)
        }
    }

    // ============================================================
    // MMS 네트워크 관리
    // ============================================================

    private var mmsNetworkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * MMS 전용 셀룰러 네트워크 획득.
     *
     * - NET_CAPABILITY_MMS + TRANSPORT_CELLULAR 요청
     * - WiFi 연결 중에도 셀룰러 MMS 네트워크를 별도로 요청
     * - 5초 내 획득 실패 시 null 반환 → 기본 네트워크로 시도
     * - 필요 권한: CHANGE_NETWORK_STATE
     *
     * @return MMS Network 또는 null (기본 네트워크 사용)
     */
    private fun acquireMmsNetwork(cm: ConnectivityManager): Network? {
        val latch = CountDownLatch(1)
        var mmsNetwork: Network? = null

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mmsNetwork = network
                latch.countDown()
            }
            override fun onUnavailable() {
                latch.countDown()
            }
        }
        mmsNetworkCallback = callback

        try {
            cm.requestNetwork(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_MMS)
                    .build(),
                callback,
                5000  // 5초 타임아웃
            )
            latch.await(6, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "MMS 네트워크 요청 실패: ${e.message}")
        }

        Log.d(TAG, "MMS 네트워크: ${if (mmsNetwork != null) "획득" else "미획득, 기본 네트워크 사용"}")
        return mmsNetwork
    }

    /**
     * MMS 네트워크 콜백 해제. 반드시 finally에서 호출.
     */
    private fun releaseMmsNetwork(cm: ConnectivityManager) {
        mmsNetworkCallback?.let {
            try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {}
            mmsNetworkCallback = null
        }
    }

    // ============================================================
    // MMS PDU 빌더 (OMA-TS-MMS_ENC 스펙)
    // ============================================================

    /**
     * 텍스트 전용 MMS PDU (M-Send.req) 생성.
     *
     * OMA-TS-MMS_ENC (MMS Encapsulation Protocol) 스펙 준수.
     * WAP-230-WSP (Wireless Session Protocol) 바이너리 인코딩.
     *
     * PDU 구조:
     * ┌─────────────────────────────────────┐
     * │ MMS Headers                         │
     * │  - Message-Type: m-send-req (0x80)  │
     * │  - Transaction-Id: 고유값           │
     * │  - MMS-Version: 1.0 (0x90)          │
     * │  - From: insert-address-token       │
     * │  - To: 수신번호                     │
     * │  - Content-Type: multipart/mixed    │ ← 반드시 마지막 헤더
     * ├─────────────────────────────────────┤
     * │ Body (Multipart)                    │
     * │  - nEntries: 1                      │
     * │  - Part 1:                          │
     * │    - HeadersLen: 4                  │
     * │    - DataLen: 텍스트 바이트 수      │
     * │    - Content-Type: text/plain;      │
     * │      charset=utf-8                  │
     * │    - Data: UTF-8 텍스트             │
     * └─────────────────────────────────────┘
     *
     * @param recipient 수신 전화번호 (예: "01012345678")
     * @param text 메시지 본문 (UTF-8)
     * @return MMS PDU 바이트 배열
     */
    private fun buildTextOnlyMmsPdu(recipient: String, text: String): ByteArray {
        val out = ByteArrayOutputStream()

        // === MMS 헤더 ===

        // X-Mms-Message-Type: m-send-req
        // 헤더 필드 0x8C, 값 0x80 (m-send-req)
        out.write(0x8C)
        out.write(0x80)

        // X-Mms-Transaction-Id: 고유 문자열 (Text-string: 문자열 + 0x00)
        // 헤더 필드 0x98
        out.write(0x98)
        writeTextString(out, System.nanoTime().toString())

        // X-Mms-MMS-Version: 1.0
        // 헤더 필드 0x8D, 값 0x90 (Short-integer: 0x10 | 0x80)
        // 1.0 = 0x10 → 0x10 | 0x80 = 0x90
        out.write(0x8D)
        out.write(0x90)

        // From: insert-address-token (시스템이 발신번호를 자동 삽입)
        // 헤더 필드 0x89, Value-length 0x01, insert-address-token 0x81
        out.write(0x89)
        out.write(0x01)
        out.write(0x81)

        // To: 수신 전화번호 (Encoded-string-value = Text-string)
        // 헤더 필드 0x97
        // 주의: /TYPE=PLMN 접미사 붙이지 않음 (삼성 호환성)
        out.write(0x97)
        writeTextString(out, recipient)

        // Content-Type: application/vnd.wap.multipart.mixed
        // 반드시 마지막 헤더 (MMS 스펙 요구사항)
        // 헤더 필드 0x84
        // Well-known media: multipart.mixed = 0x23
        // Short-integer 인코딩: 0x23 | 0x80 = 0xA3
        out.write(0x84)
        out.write(0xA3.toInt())

        // === 바디 (Multipart) ===

        // nEntries: 파트 수 = 1 (uintvar 인코딩)
        writeUintvar(out, 1)

        // === Part 1: text/plain; charset=utf-8 ===
        val textBytes = text.toByteArray(Charsets.UTF_8)

        // Content-Type (Content-general-form 인코딩):
        //   Value-length (Short-length): 3
        //   Media-type (Short-integer): text/plain = 0x03 | 0x80 = 0x83
        //   Parameter:
        //     Charset token (Short-integer): 0x01 | 0x80 = 0x81
        //     Charset value (Short-integer): UTF-8 = 106 | 0x80 = 0xEA
        val partContentType = byteArrayOf(
            0x03,             // Value-length = 3 (이후 3바이트)
            0x83.toByte(),    // text/plain (Well-known 0x03 | 0x80)
            0x81.toByte(),    // Charset 파라미터 (Well-known 0x01 | 0x80)
            0xEA.toByte()     // UTF-8 (Well-known 106 | 0x80)
        ) // 총 4바이트

        // HeadersLen: Content-Type 인코딩 크기 = 4 (uintvar)
        writeUintvar(out, partContentType.size)
        // DataLen: 텍스트 바이트 크기 (uintvar)
        writeUintvar(out, textBytes.size)
        // Content-Type 헤더 데이터
        out.write(partContentType)
        // 텍스트 데이터
        out.write(textBytes)

        return out.toByteArray()
    }

    /**
     * WSP Text-string 인코딩: US-ASCII 문자열 + NULL(0x00) 종단
     */
    private fun writeTextString(out: ByteArrayOutputStream, text: String) {
        out.write(text.toByteArray(Charsets.US_ASCII))
        out.write(0x00)
    }

    /**
     * WSP Uintvar 인코딩: 가변 길이 부호 없는 정수.
     * 각 바이트의 최상위 비트가 1이면 다음 바이트가 이어짐.
     * 마지막 바이트의 최상위 비트는 0.
     */
    private fun writeUintvar(out: ByteArrayOutputStream, value: Int) {
        if (value < 0x80) {
            out.write(value)
            return
        }
        val bytes = mutableListOf<Int>()
        var v = value
        bytes.add(v and 0x7F)  // 마지막 바이트 (MSB=0)
        v = v shr 7
        while (v > 0) {
            bytes.add((v and 0x7F) or 0x80)  // 이전 바이트들 (MSB=1)
            v = v shr 7
        }
        bytes.reversed().forEach { out.write(it) }
    }

    // ============================================================
    // 데이터 클래스
    // ============================================================

    private data class KoreanMmsc(
        val carrierName: String,  // 통신사 이름 (로그용)
        val url: String,          // MMSC URL (HTTP)
        val proxy: String,        // MMS 프록시 (빈 문자열이면 프록시 없음)
        val port: Int             // MMS 프록시 포트
    )
}
```

### 3-3. network_security_config.xml (필수 설정)

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- 기본: HTTPS만 허용 -->
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.bizconnect.com</domain>
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </domain-config>

    <!-- 개발용: localhost HTTP 허용 -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">localhost</domain>
        <domain includeSubdomains="true">127.0.0.1</domain>
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </domain-config>

    <!-- 한국 통신사 MMSC 서버: HTTP 허용 (MMSC는 HTTP만 지원) -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">omammsc.uplus.co.kr</domain>  <!-- LG U+ -->
        <domain includeSubdomains="true">omms.nate.com</domain>        <!-- SKT -->
        <domain includeSubdomains="true">smart.nate.com</domain>       <!-- SKT 프록시 -->
        <domain includeSubdomains="true">mmsc.ktfwing.com</domain>     <!-- KT -->
    </domain-config>
</network-security-config>
```

### 3-4. AndroidManifest.xml 설정 (핵심)

```xml
<application
    ...
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

**절대 주의**: `android:usesCleartextTraffic="false"`를 사용하면
`networkSecurityConfig`보다 우선 적용되어 MMSC HTTP 통신이 차단됨.
반드시 `usesCleartextTraffic` 속성을 제거하고 `networkSecurityConfig`만 사용할 것.

### 3-5. 필요 권한 (AndroidManifest.xml)

```xml
<!-- SMS 발송 -->
<uses-permission android:name="android.permission.SEND_SMS" />

<!-- MMS 네트워크 요청 -->
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- 인터넷 (MMSC HTTP 통신) -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- 통신사 식별 -->
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
```

---

## 4. 한국 통신사 MMSC 정보

| 통신사 | MCC/MNC | MMSC URL | 프록시 | 포트 |
|--------|---------|----------|--------|------|
| SKT | 450/05 | http://omms.nate.com:9082/oma_mms | smart.nate.com | 9093 |
| KT | 450/08, 450/02 | http://mmsc.ktfwing.com:9082 | (없음) | - |
| LG U+ | 450/06 | http://omammsc.uplus.co.kr:9084 | (없음) | - |
| KT 알뜰폰 | 450/04 | http://mmsc.ktfwing.com:9082 | (없음) | - |
| SKT 알뜰폰 | 450/11 | http://omms.nate.com:9082/oma_mms | smart.nate.com | 9093 |

---

## 5. 디버깅 가이드

### Logcat 필터
```bash
adb logcat -s SmsSender
```

### 성공 로그 예시
```
D SmsSender: Long message (85 chars, 2 parts) → trying MMS
D SmsSender: [DirectHTTP] Network operator: 45006
D SmsSender: [DirectHTTP] Carrier: LG U+, MMSC: http://omammsc.uplus.co.kr:9084
D SmsSender: MMS network: unavailable, using default
D SmsSender: [DirectHTTP] Response: 200, 90 bytes
D SmsSender: [DirectHTTP] MMS sent successfully (M-Send.conf)
D SmsSender: Message sent to 01043792829 (mms=true)
```

### 실패 원인별 로그

| 로그 | 원인 | 해결 |
|------|------|------|
| `Cleartext HTTP traffic not permitted` | networkSecurityConfig 미적용 | AndroidManifest에서 `usesCleartextTraffic` 제거, `networkSecurityConfig` 추가 |
| `No permission to access APN settings` | APN 읽기 권한 없음 | APN 읽지 말고 MMSC URL 하드코딩 |
| `Unknown operator: XXXXX` | 미지원 통신사 | MMSC URL 추가 |
| `MMS network: unavailable` | WiFi 환경에서 MMS 네트워크 없음 | 정상 — 기본 네트워크로 시도 |
| `completed=false, code=1` | sendMultimediaMessage 타임아웃 | 이 API 사용하지 말 것 |

---

## 6. 주의사항 및 교훈

### 절대 하지 말 것
1. **`SmsManager.sendMultimediaMessage()` 사용 금지**
   - 삼성 기기에서 MMS 실패 시 시스템이 자동으로 multipart SMS 재시도
   - 결과: 우리 폴백 SMS + 시스템 재시도 SMS = **이중 발송**

2. **`android:usesCleartextTraffic="false"`와 `networkSecurityConfig` 동시 사용 금지**
   - `usesCleartextTraffic`이 우선 적용되어 networkSecurityConfig 무시됨

3. **APN 설정 직접 읽기 시도 금지**
   - Android 10+에서 `READ_APN_SETTINGS`는 시스템 권한
   - `content://telephony/carriers` 접근 불가

### 기본앱 여부
- **MMS 발송**: 기본앱 아니어도 가능 (HTTP 통신일 뿐)
- **SMS 발송**: 기본앱 아니어도 가능 (SEND_SMS 권한만 필요)
- **시스템 provider 기록**: 기본앱만 가능
- **SMS 수신**: 기본앱만 가능 (SMS_DELIVER)

### MMS 네트워크
- WiFi 환경에서 MMS 전용 셀룰러 네트워크를 못 잡을 수 있음
- 이 경우 기본 네트워크(WiFi)로 시도하면 **LG U+는 성공** (확인됨)
- 통신사에 따라 셀룰러 네트워크만 허용할 수 있음 → 폴백으로 multipart SMS

### 타임아웃 설정 (검증된 값)
- MMS 네트워크 획득: **5초** (requestNetwork timeout)
- HTTP 연결: **10초** (connectTimeout)
- HTTP 읽기: **10초** (readTimeout)
- 총 최악: **~25초** (네트워크 5초 + HTTP 10초 + 처리 시간)

---

## 7. 테스트 결과 (2026-03-18)

| 테스트 | 발신 | 수신 | 결과 |
|--------|------|------|------|
| 단문 (70자 이하) | LG U+ | LG U+ | SMS 1건 즉시 |
| 장문 (70자 초과) | LG U+ | LG U+ | MMS 1건 성공 |
| 장문 (70자 초과) | LG U+ | 다른 통신사 | MMS 1건 성공 |

성공 확인 로그:
```
[DirectHTTP] Response: 200, 90 bytes
[DirectHTTP] MMS sent successfully (M-Send.conf)
Message sent to 01043792829 (mms=true)
```
