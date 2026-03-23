package com.bizconnect.server.services

/**
 * 광고성 문자 감지 (정보통신망법 준수)
 *
 * 광고성 문자 요건:
 * 1. (광고) 표기 의무
 * 2. 080 수신거부번호 포함
 * 3. 업체명 포함
 * 4. 야간(21~08시) 발송 금지
 */
class AdMessageDetector {

    data class DetectionResult(
        val isAd: Boolean,
        val reasons: List<String>,
        val formattedMessage: String? = null  // 광고 표기 자동 추가된 메시지
    )

    private val adKeywords = listOf(
        "할인", "이벤트", "쿠폰", "세일", "프로모션", "특가", "무료체험",
        "가입", "혜택", "적립", "포인트", "감사제", "기간한정", "마감임박",
        "오픈", "신규", "런칭", "사은품", "경품", "추첨", "당첨",
        "% OFF", "%할인", "원~", "최대", "파격", "초특가",
        "지금 바로", "서두르세요", "놓치지", "한정수량"
    )

    private val urlPattern = Regex("https?://\\S+|www\\.\\S+|\\S+\\.com|\\S+\\.kr|\\S+\\.co\\.kr")

    /**
     * 메시지가 광고성인지 감지
     */
    fun detect(message: String): DetectionResult {
        val reasons = mutableListOf<String>()

        // 키워드 매칭
        val foundKeywords = adKeywords.filter { message.contains(it, ignoreCase = true) }
        if (foundKeywords.size >= 2) {
            reasons.add("광고 키워드 감지: ${foundKeywords.take(3).joinToString(", ")}")
        }

        // URL 포함 + 키워드 1개 이상
        val hasUrl = urlPattern.containsMatchIn(message)
        if (hasUrl && foundKeywords.isNotEmpty()) {
            reasons.add("URL + 광고 키워드 포함")
        }

        // 이미 (광고) 표기가 있으면 광고로 확정
        if (message.contains("(광고)") || message.contains("[광고]")) {
            reasons.add("광고 표기 포함")
        }

        val isAd = reasons.isNotEmpty()

        return DetectionResult(
            isAd = isAd,
            reasons = reasons,
            formattedMessage = if (isAd) formatAdMessage(message) else null
        )
    }

    /**
     * 광고 문자에 법적 요건 자동 추가
     */
    fun formatAdMessage(message: String, businessName: String = "BizConnect"): String {
        val sb = StringBuilder()

        // (광고) 표기가 없으면 추가
        if (!message.startsWith("(광고)") && !message.startsWith("[광고]")) {
            sb.append("(광고) $businessName\n")
        }

        sb.append(message)

        // 수신거부 안내가 없으면 추가
        if (!message.contains("수신거부") && !message.contains("080")) {
            sb.append("\n무료수신거부 080-000-0000")
        }

        return sb.toString()
    }

    /**
     * 야간 시간대 체크 (21:00~08:00)
     */
    fun isNightTime(): Boolean {
        val hour = java.time.LocalTime.now(java.time.ZoneId.of("Asia/Seoul")).hour
        return hour >= 21 || hour < 8
    }
}
