package com.bizconnect.v2.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberUtilTest {

    @Test
    fun normalizePhoneWithHyphens() {
        val normalized = PhoneNumberUtil.normalize("010-1234-5678")
        assertEquals("01012345678", normalized)
    }

    @Test
    fun normalizePhoneWithSpaces() {
        val normalized = PhoneNumberUtil.normalize("010 1234 5678")
        assertEquals("01012345678", normalized)
    }

    @Test
    fun validate010Number() {
        assertTrue(PhoneNumberUtil.isValid("01012345678"))
    }

    @Test
    fun validate011Number() {
        assertTrue(PhoneNumberUtil.isValid("01112345678"))
    }

    @Test
    fun validate016Number() {
        assertTrue(PhoneNumberUtil.isValid("01612345678"))
    }

    @Test
    fun rejectTooShortNumber() {
        assertFalse(PhoneNumberUtil.isValid("0101234567"))
    }

    @Test
    fun rejectNonKoreanNumber() {
        assertFalse(PhoneNumberUtil.isValid("5551234567"))
    }

    @Test
    fun formatForDisplay() {
        val formatted = PhoneNumberUtil.formatForDisplay("01012345678")
        assertEquals("010-1234-5678", formatted)
    }

    @Test
    fun handleCountryCode() {
        val normalized = PhoneNumberUtil.normalize("+82-10-1234-5678")
        assertEquals("01012345678", normalized)
    }

    @Test
    fun validateLandlineNumber() {
        assertTrue(PhoneNumberUtil.isValid("0212345678"))
        assertTrue(PhoneNumberUtil.isValid("0312345678"))
        assertTrue(PhoneNumberUtil.isValid("0512345678"))
    }

    @Test
    fun formatLandlineForDisplay() {
        val formatted = PhoneNumberUtil.formatForDisplay("0212345678")
        assertEquals("02-1234-5678", formatted)
    }
}

object PhoneNumberUtil {

    fun normalize(phone: String): String {
        var normalized = phone
            .replace("-", "")
            .replace(" ", "")
            .replace("+", "")

        // Handle country code +82 or 0082 format
        when {
            normalized.startsWith("82") && normalized.length > 10 -> {
                // +82-10-xxx-xxxx becomes 010xxxx
                normalized = "0" + normalized.substring(2)
            }
        }

        return normalized
    }

    fun isValid(phone: String): Boolean {
        val normalized = normalize(phone)

        return when {
            normalized.isEmpty() -> false
            // Mobile numbers: 01x followed by 7-8 digits
            normalized.matches(Regex("^01[0-9]\\d{7,8}$")) -> true
            // Landline: 0x followed by 7-10 digits
            normalized.matches(Regex("^0[2-9]\\d{7,10}$")) -> true
            else -> false
        }
    }

    fun formatForDisplay(phone: String): String {
        val normalized = normalize(phone)

        return when {
            // Mobile format: 010-1234-5678
            normalized.matches(Regex("^01[0-9]\\d{7,8}$")) -> {
                normalized.substring(0, 3) + "-" +
                        normalized.substring(3, 7) + "-" +
                        normalized.substring(7)
            }
            // Landline format: 02-1234-5678
            normalized.matches(Regex("^0[2-9]\\d{7,10}$")) -> {
                val areaCode = normalized.substring(0, 2)
                val prefix = normalized.substring(2, normalized.length - 4)
                val lineNumber = normalized.substring(normalized.length - 4)
                "$areaCode-$prefix-$lineNumber"
            }
            else -> phone
        }
    }

    fun getOperator(phone: String): String? {
        val normalized = normalize(phone)

        return when {
            normalized.startsWith("010") -> "SKT"
            normalized.startsWith("011") -> "KT"
            normalized.startsWith("016") -> "LG U+"
            normalized.startsWith("017") -> "SKT"
            normalized.startsWith("018") -> "SKT"
            normalized.startsWith("019") -> "KT"
            else -> null
        }
    }
}
