package com.bizconnect.v2.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SmsEngineTest {

    private lateinit var smsEngine: SmsEngine

    @Before
    fun setup() {
        smsEngine = SmsEngine()
    }

    @Test
    fun detectSmsTypeForShortMessage() {
        val message = "안녕하세요"
        val type = smsEngine.detectMessageType(message)

        assertEquals(MessageType.SMS, type)
    }

    @Test
    fun detectLmsTypeForLongMessage() {
        val message = "A".repeat(161)
        val type = smsEngine.detectMessageType(message)

        assertEquals(MessageType.LMS, type)
    }

    @Test
    fun detectMmsTypeWithImage() {
        val type = smsEngine.detectMessageType(
            message = "사진을 첨부합니다",
            hasAttachment = true
        )

        assertEquals(MessageType.MMS, type)
    }

    @Test
    fun checkDailyLimitNotReached() {
        smsEngine.setDailyLimit(100)

        repeat(50) {
            smsEngine.recordMessageSent()
        }

        assertTrue(smsEngine.canSendMessage())
    }

    @Test
    fun checkDailyLimitReached() {
        smsEngine.setDailyLimit(10)

        repeat(10) {
            smsEngine.recordMessageSent()
        }

        assertFalse(smsEngine.canSendMessage())
    }

    @Test
    fun normalizeKoreanPhoneNumber() {
        val phone1 = "010-1234-5678"
        val normalized1 = smsEngine.normalizePhoneNumber(phone1)
        assertEquals("01012345678", normalized1)

        val phone2 = "010 1234 5678"
        val normalized2 = smsEngine.normalizePhoneNumber(phone2)
        assertEquals("01012345678", normalized2)

        val phone3 = "01012345678"
        val normalized3 = smsEngine.normalizePhoneNumber(phone3)
        assertEquals("01012345678", normalized3)
    }

    @Test
    fun validateKoreanPhoneNumber() {
        assertTrue(smsEngine.isValidPhoneNumber("01012345678"))
        assertTrue(smsEngine.isValidPhoneNumber("01112345678"))
        assertTrue(smsEngine.isValidPhoneNumber("01612345678"))
        assertTrue(smsEngine.isValidPhoneNumber("02-123-4567"))
    }

    @Test
    fun rejectInvalidPhoneNumber() {
        assertFalse(smsEngine.isValidPhoneNumber("123"))
        assertFalse(smsEngine.isValidPhoneNumber("abcdefghij"))
        assertFalse(smsEngine.isValidPhoneNumber(""))
    }
}

enum class MessageType {
    SMS,   // up to 160 characters
    LMS,   // 161-4000 characters
    MMS    // with attachments
}

class SmsEngine {
    private var dailyLimit = Int.MAX_VALUE
    private var messagesSentToday = 0
    private val smsMaxLength = 160
    private val lmsMaxLength = 4000

    fun setDailyLimit(limit: Int) {
        dailyLimit = limit
        messagesSentToday = 0
    }

    fun recordMessageSent() {
        messagesSentToday++
    }

    fun canSendMessage(): Boolean {
        return messagesSentToday < dailyLimit
    }

    fun detectMessageType(message: String, hasAttachment: Boolean = false): MessageType {
        return when {
            hasAttachment -> MessageType.MMS
            message.length <= smsMaxLength -> MessageType.SMS
            message.length <= lmsMaxLength -> MessageType.LMS
            else -> MessageType.LMS
        }
    }

    fun normalizePhoneNumber(phone: String): String {
        return phone.replace("-", "").replace(" ", "")
    }

    fun isValidPhoneNumber(phone: String): Boolean {
        val normalized = normalizePhoneNumber(phone)

        return when {
            normalized.isEmpty() -> false
            // Mobile numbers starting with 01x
            normalized.matches(Regex("^01[0-9]\\d{7,8}$")) -> true
            // Landline numbers
            normalized.matches(Regex("^0[2-9]\\d{7,10}$")) -> true
            else -> false
        }
    }

    fun getRemainingDailyQuota(): Int {
        return (dailyLimit - messagesSentToday).coerceAtLeast(0)
    }
}
