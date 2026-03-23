package com.bizconnect.server.services

import com.bizconnect.server.database.VerificationCodesTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.SecureRandom
import java.util.UUID

class PhoneVerificationService(private val smsService: WideshotSmsService) {
    private val random = SecureRandom()

    suspend fun sendCode(phone: String, purpose: String): Result {
        val normalizedPhone = phone.replace("-", "")

        // Daily limit check (5 per day)
        val today = System.currentTimeMillis() - 86400_000
        val dailyCount = transaction {
            VerificationCodesTable.selectAll().where {
                (VerificationCodesTable.phone eq normalizedPhone) and
                (VerificationCodesTable.createdAt greater today)
            }.count()
        }
        if (dailyCount >= 5) return Result(false, "일일 인증 횟수(5회)를 초과했습니다")

        // Generate 6-digit code
        val code = String.format("%06d", random.nextInt(1000000))
        val now = System.currentTimeMillis()

        // Save to DB
        transaction {
            VerificationCodesTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[VerificationCodesTable.phone] = normalizedPhone
                it[VerificationCodesTable.code] = code
                it[VerificationCodesTable.purpose] = purpose
                it[expiresAt] = now + 180_000 // 3 minutes
                it[createdAt] = now
            }
        }

        // Send SMS via proxy (3.37.75.111:9877)
        try {
            val smsProxyUrl = System.getenv("SMS_PROXY_URL") ?: "http://3.37.75.111/sms-proxy"
            val payload = """{"phone":"$normalizedPhone","message":"[BizConnect] 인증번호: $code (3분 이내 입력해주세요)"}"""
            val conn = java.net.URL(smsProxyUrl).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.outputStream.write(payload.toString().toByteArray())
            val respCode = conn.responseCode
            val respBody = if (respCode == 200) conn.inputStream.bufferedReader().readText()
                else conn.errorStream?.bufferedReader()?.readText() ?: ""
            conn.disconnect()

            if (respCode != 200 || !respBody.contains("200")) {
                return Result(false, "인증번호 발송에 실패했습니다: $respBody")
            }
        } catch (e: Exception) {
            return Result(false, "인증번호 발송에 실패했습니다: ${e.message}")
        }

        return Result(true, "인증번호가 발송되었습니다")
    }

    fun verifyCode(phone: String, code: String, purpose: String): Result {
        val normalizedPhone = phone.replace("-", "")
        val now = System.currentTimeMillis()

        val record = transaction {
            VerificationCodesTable.selectAll().where {
                (VerificationCodesTable.phone eq normalizedPhone) and
                (VerificationCodesTable.purpose eq purpose) and
                (VerificationCodesTable.verified eq false) and
                (VerificationCodesTable.expiresAt greater now)
            }.orderBy(VerificationCodesTable.createdAt, SortOrder.DESC)
                .firstOrNull()
        } ?: return Result(false, "인증번호가 만료되었습니다. 다시 요청해주세요.")

        val recordId = record[VerificationCodesTable.id]
        val attempts = record[VerificationCodesTable.attempts]

        if (attempts >= 5) return Result(false, "인증 시도 횟수를 초과했습니다")

        if (record[VerificationCodesTable.code] != code) {
            transaction {
                VerificationCodesTable.update({ VerificationCodesTable.id eq recordId }) {
                    it[VerificationCodesTable.attempts] = attempts + 1
                }
            }
            return Result(false, "인증번호가 일치하지 않습니다 (${5 - attempts - 1}회 남음)")
        }

        // Mark as verified
        transaction {
            VerificationCodesTable.update({ VerificationCodesTable.id eq recordId }) {
                it[verified] = true
            }
        }

        return Result(true, "인증이 완료되었습니다")
    }

    fun isPhoneVerified(phone: String, purpose: String): Boolean {
        val normalizedPhone = phone.replace("-", "")
        val tenMinutesAgo = System.currentTimeMillis() - 600_000
        return transaction {
            VerificationCodesTable.selectAll().where {
                (VerificationCodesTable.phone eq normalizedPhone) and
                (VerificationCodesTable.purpose eq purpose) and
                (VerificationCodesTable.verified eq true) and
                (VerificationCodesTable.createdAt greater tenMinutesAgo)
            }.count() > 0
        }
    }

    data class Result(val success: Boolean, val message: String)
}
