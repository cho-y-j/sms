package com.bizconnect.v2.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TemplateEngineTest {

    private lateinit var templateEngine: TemplateEngine

    @Before
    fun setup() {
        templateEngine = TemplateEngine()
    }

    @Test
    fun replaceCustomerNameVariable() {
        val template = "안녕하세요 {{customer_name}}님"
        val result = templateEngine.replaceVariables(
            template,
            mapOf("customer_name" to "김철수")
        )
        assertEquals("안녕하세요 김철수님", result)
    }

    @Test
    fun replaceDateAndTimeVariables() {
        val template = "오늘은 {{current_date}}이고 현재 시간은 {{current_time}}입니다."
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        val now = LocalDateTime.now()

        val result = templateEngine.replaceVariables(
            template,
            mapOf(
                "current_date" to now.format(dateFormatter),
                "current_time" to now.format(timeFormatter)
            )
        )

        assertTrue(result.contains(now.format(dateFormatter)))
        assertTrue(result.contains(now.format(timeFormatter)))
    }

    @Test
    fun replaceMultipleVariables() {
        val template = "{{greeting}} {{customer_name}}님, {{company_name}}에서 인사드립니다."
        val result = templateEngine.replaceVariables(
            template,
            mapOf(
                "greeting" to "안녕하세요",
                "customer_name" to "이영희",
                "company_name" to "비즈커넥트"
            )
        )
        assertEquals("안녕하세요 이영희님, 비즈커넥트에서 인사드립니다.", result)
    }

    @Test
    fun handleMissingVariableGracefully() {
        val template = "{{customer_name}}님께 {{product_name}}을 추천드립니다."
        val result = templateEngine.replaceVariables(
            template,
            mapOf("customer_name" to "박민준")
            // product_name is missing
        )

        assertEquals("박민준님께 {{product_name}}을 추천드립니다.", result)
    }

    @Test
    fun extractVariablesFromTemplate() {
        val template = "{{customer_name}}님, {{company_name}}의 {{service_name}} 서비스를 이용해보세요."
        val variables = templateEngine.extractVariables(template)

        assertTrue(variables.contains("customer_name"))
        assertTrue(variables.contains("company_name"))
        assertTrue(variables.contains("service_name"))
        assertEquals(3, variables.size)
    }

    @Test
    fun validateTemplateWithUnknownVariable() {
        val template = "{{customer_name}}님, {{unknown_var}}을 확인하세요."
        val knownVariables = setOf("customer_name", "company_name", "service_name")

        val unknownVars = templateEngine.validateTemplate(template, knownVariables)

        assertTrue(unknownVars.contains("unknown_var"))
        assertEquals(1, unknownVars.size)
    }

    @Test
    fun handleEmptyTemplate() {
        val template = ""
        val result = templateEngine.replaceVariables(
            template,
            mapOf("customer_name" to "홍길동")
        )

        assertEquals("", result)
    }

    @Test
    fun handleTemplateWithNoVariables() {
        val template = "고객님 감사합니다."
        val result = templateEngine.replaceVariables(
            template,
            mapOf("customer_name" to "홍길동")
        )

        assertEquals("고객님 감사합니다.", result)
    }

    @Test
    fun customFieldReplacement() {
        val template = "{{customer_name}}님의 주문번호는 {{order_id}}입니다. 예상 배송일은 {{delivery_date}}입니다."
        val result = templateEngine.replaceVariables(
            template,
            mapOf(
                "customer_name" to "김영미",
                "order_id" to "ORD-2026-001234",
                "delivery_date" to "2026-03-24"
            )
        )

        assertEquals("김영미님의 주문번호는 ORD-2026-001234입니다. 예상 배송일은 2026-03-24입니다.", result)
    }

    @Test
    fun koreanDayOfWeek() {
        val template = "{{day_of_week}}에 예약이 확정되었습니다."
        val dayOfWeek = templateEngine.formatDateToKoreanDay(
            LocalDate.of(2026, 3, 20) // Friday
        )

        val result = templateEngine.replaceVariables(
            template,
            mapOf("day_of_week" to dayOfWeek)
        )

        assertTrue(result.contains("금요일") || result.contains("다음주"))
    }
}

class TemplateEngine {
    fun replaceVariables(template: String, variables: Map<String, String>): String {
        var result = template
        val pattern = Regex("""{{(\w+)}}""")

        pattern.findAll(template).forEach { match ->
            val varName = match.groupValues[1]
            val value = variables[varName]
            if (value != null) {
                result = result.replace(match.value, value)
            }
        }

        return result
    }

    fun extractVariables(template: String): List<String> {
        val pattern = Regex("""{{(\w+)}}""")
        return pattern.findAll(template).map { it.groupValues[1] }.toList()
    }

    fun validateTemplate(template: String, knownVariables: Set<String>): Set<String> {
        val extractedVars = extractVariables(template).toSet()
        return extractedVars - knownVariables
    }

    fun formatDateToKoreanDay(date: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern("EEEE", java.util.Locale("ko", "KR"))
        return date.format(formatter)
    }
}
