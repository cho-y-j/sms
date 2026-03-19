package com.bizconnect.v2.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateTimeUtil {
    private val koreanLocale = Locale("ko", "KR")

    fun formatTimestamp(timestamp: Long, pattern: String = "yyyy.MM.dd HH:mm"): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat(pattern, koreanLocale)
        return format.format(date)
    }

    fun formatTimestampShort(timestamp: Long): String {
        val date = Date(timestamp)
        val now = System.currentTimeMillis()
        val diffMs = now - timestamp

        return when {
            diffMs < 60 * 1000 -> "방금 전"
            diffMs < 60 * 60 * 1000 -> {
                val minutes = diffMs / (60 * 1000)
                "${minutes}분 전"
            }
            diffMs < 24 * 60 * 60 * 1000 -> {
                val hours = diffMs / (60 * 60 * 1000)
                "${hours}시간 전"
            }
            diffMs < 30 * 24 * 60 * 60 * 1000 -> {
                val days = diffMs / (24 * 60 * 60 * 1000)
                "${days}일 전"
            }
            else -> formatTimestamp(timestamp, "yyyy.MM.dd")
        }
    }

    fun getTimeOnly(timestamp: Long): String {
        return formatTimestamp(timestamp, "HH:mm")
    }

    fun getDateOnly(timestamp: Long): String {
        return formatTimestamp(timestamp, "yyyy년 MM월 dd일")
    }

    fun getFullDateTime(timestamp: Long): String {
        return formatTimestamp(timestamp, "yyyy.MM.dd(EEE) HH:mm")
    }

    fun isToday(timestamp: Long): Boolean {
        val date = Date(timestamp)
        val now = Date()
        val format = SimpleDateFormat("yyyyMMdd", koreanLocale)
        return format.format(date) == format.format(now)
    }

    fun isYesterday(timestamp: Long): Boolean {
        val date = Date(timestamp)
        val yesterday = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
        val format = SimpleDateFormat("yyyyMMdd", koreanLocale)
        return format.format(date) == format.format(yesterday)
    }
}
