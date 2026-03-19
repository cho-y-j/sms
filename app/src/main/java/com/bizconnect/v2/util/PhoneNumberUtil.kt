package com.bizconnect.v2.util

import java.util.regex.Pattern

class PhoneNumberUtil {
    fun normalizeNumber(phoneNumber: String): String {
        var number = phoneNumber.trim()

        // Remove all non-digit characters except leading +
        number = if (number.startsWith("+")) {
            "+" + number.filter { it.isDigit() }
        } else {
            number.filter { it.isDigit() }
        }

        // Korean phone numbers: convert local to international format
        if (!number.startsWith("+") && (number.startsWith("01") || number.startsWith("02") || number.startsWith("0"))) {
            number = if (number.startsWith("0")) {
                "+82" + number.substring(1)
            } else {
                "+82$number"
            }
        }

        return number
    }

    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val normalized = normalizeNumber(phoneNumber)

        // Valid format: +CCNNNNNNNNN (2-3 digit country code + 7-15 digit number)
        val pattern = Pattern.compile("^\\+\\d{10,15}$")
        return pattern.matcher(normalized).matches()
    }

    fun formatPhoneNumberForDisplay(phoneNumber: String, locale: String = "ko"): String {
        val normalized = normalizeNumber(phoneNumber)

        return when {
            locale == "ko" && normalized.startsWith("+82") -> {
                // Korean format: +82 10-1234-5678
                val number = normalized.substring(3) // Remove +82
                when {
                    number.length == 10 -> "${number.substring(0, 2)}-${number.substring(2, 6)}-${number.substring(6)}"
                    number.length == 11 -> "${number.substring(0, 3)}-${number.substring(3, 7)}-${number.substring(7)}"
                    else -> normalized
                }
            }
            else -> normalized
        }
    }

    fun extractCountryCode(phoneNumber: String): String? {
        val normalized = normalizeNumber(phoneNumber)
        if (!normalized.startsWith("+")) return null

        // Extract country code (2-3 digits after +)
        val match = Regex("\\+([0-9]{2,3})").find(normalized)
        return match?.groupValues?.get(1)
    }
}
