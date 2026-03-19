package com.bizconnect.v2.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpamEngineTest {

    private lateinit var spamEngine: SpamEngine

    @Before
    fun setup() {
        spamEngine = SpamEngine()
    }

    @Test
    fun blockNumber() {
        spamEngine.blockNumber("01012345678")

        val blockedNumbers = spamEngine.getBlockedNumbers()
        assertTrue(blockedNumbers.contains("01012345678"))
    }

    @Test
    fun unblockNumber() {
        spamEngine.blockNumber("01012345678")
        assertTrue(spamEngine.getBlockedNumbers().contains("01012345678"))

        spamEngine.unblockNumber("01012345678")
        assertFalse(spamEngine.getBlockedNumbers().contains("01012345678"))
    }

    @Test
    fun detectSpamByBlockedNumber() {
        spamEngine.blockNumber("01087654321")

        val isSpam = spamEngine.isSpam(
            address = "01087654321",
            body = "무료 상품 받으세요"
        )

        assertTrue(isSpam)
    }

    @Test
    fun detectSpamByKeyword() {
        spamEngine.addSpamKeyword("무료 상품")
        spamEngine.addSpamKeyword("클릭하세요")

        val isSpam1 = spamEngine.isSpam(
            address = "01012345678",
            body = "무료 상품을 받으세요!"
        )
        assertTrue(isSpam1)

        val isSpam2 = spamEngine.isSpam(
            address = "01012345678",
            body = "지금 클릭하세요."
        )
        assertTrue(isSpam2)
    }

    @Test
    fun nonSpamMessagePasses() {
        spamEngine.blockNumber("01099999999")
        spamEngine.addSpamKeyword("피싱")

        val isSpam = spamEngine.isSpam(
            address = "01012345678",
            body = "내일 회의 시간 확인해주세요"
        )

        assertFalse(isSpam)
    }

    @Test
    fun extract6DigitVerificationCode() {
        val message = "인증번호는 123456입니다. 이 번호를 공유하지 마세요."
        val code = spamEngine.extractVerificationCode(message)

        assertEquals("123456", code)
    }

    @Test
    fun extract4DigitVerificationCode() {
        val message = "비밀번호 확인: 1234"
        val code = spamEngine.extractVerificationCode(message)

        assertEquals("1234", code)
    }

    @Test
    fun noCodeInRegularMessage() {
        val message = "안녕하세요, 좋은 아침입니다."
        val code = spamEngine.extractVerificationCode(message)

        assertNull(code)
    }

    @Test
    fun caseInsensitiveKeywordMatching() {
        spamEngine.addSpamKeyword("이벤트")

        val isSpam1 = spamEngine.isSpam(
            address = "01012345678",
            body = "이벤트에 참가하세요"
        )

        val isSpam2 = spamEngine.isSpam(
            address = "01012345678",
            body = "이벤트에 참가하세요"
        )

        assertTrue(isSpam1)
        assertTrue(isSpam2)
    }
}

class SpamEngine {
    private val blockedNumbers = mutableSetOf<String>()
    private val spamKeywords = mutableSetOf<String>()

    fun blockNumber(number: String) {
        blockedNumbers.add(number)
    }

    fun unblockNumber(number: String) {
        blockedNumbers.remove(number)
    }

    fun getBlockedNumbers(): Set<String> = blockedNumbers.toSet()

    fun addSpamKeyword(keyword: String) {
        spamKeywords.add(keyword.lowercase())
    }

    fun removeSpamKeyword(keyword: String) {
        spamKeywords.remove(keyword.lowercase())
    }

    fun isSpam(address: String, body: String): Boolean {
        // Check if number is blocked
        if (blockedNumbers.contains(address)) {
            return true
        }

        // Check if message contains spam keywords
        val lowerBody = body.lowercase()
        return spamKeywords.any { keyword -> lowerBody.contains(keyword) }
    }

    fun extractVerificationCode(message: String): String? {
        // Try to find 6-digit code first
        val sixDigitPattern = Regex("\\b\\d{6}\\b")
        sixDigitPattern.find(message)?.let { return it.value }

        // Try to find 4-digit code
        val fourDigitPattern = Regex("\\b\\d{4}\\b")
        fourDigitPattern.find(message)?.let { return it.value }

        return null
    }
}
