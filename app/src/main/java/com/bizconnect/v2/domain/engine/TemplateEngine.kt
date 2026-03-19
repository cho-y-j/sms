package com.bizconnect.v2.domain.engine

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/**
 * Message template variable substitution engine.
 * Supports Korean business message templates with dynamic variable replacement.
 *
 * Supported variables:
 * - {고객명} - Customer name
 * - {전화번호} - Phone number
 * - {날짜} - Date (YYYY-MM-DD)
 * - {시간} - Time (HH:mm)
 * - {요일} - Day of week
 * - {회사명} - Company name
 * - {담당자} - Manager/Staff name
 * - {시스템날짜} - System date
 * - {커스텀:key} - Custom field by key
 */
class TemplateEngine @Inject constructor() {

    companion object {
        private const val TAG = "TemplateEngine"

        // Supported system variables
        private val SYSTEM_VARIABLES = setOf(
            "고객명",
            "이름",
            "전화번호",
            "날짜",
            "시간",
            "요일",
            "회사명",
            "담당자",
            "시스템날짜",
            "시스템시간",
            "기념일",
            "주소",
            "메모",
            "커스텀1",
            "커스텀2",
            "커스텀3",
            "커스텀4",
            "커스텀5"
        )
    }

    /**
     * Context for template variable substitution
     */
    data class TemplateContext(
        val customerName: String? = null,
        val phoneNumber: String? = null,
        val companyName: String? = null,
        val managerName: String? = null,
        val customFields: Map<String, String> = emptyMap(),
        val referenceDate: Long = System.currentTimeMillis()
    )

    /**
     * Process template string and replace all variables.
     * Supports both {variable} and %variable% formats.
     */
    fun process(template: String, context: TemplateContext): String {
        var result = template

        // Extract variables from template (both formats)
        val braceVars = extractBraceVariables(template)
        val percentVars = extractPercentVariables(template)

        // Replace {variable} format
        for (variable in braceVars) {
            val replacement = getVariableValue(variable, context)
            result = result.replace("{$variable}", replacement)
        }

        // Replace %variable% format
        for (variable in percentVars) {
            val replacement = getVariableValue(variable, context)
            result = result.replace("%$variable%", replacement)
        }

        return result
    }

    /**
     * Extract all template variables from a string.
     * Detects both {variable} and %variable% formats.
     * Returns list of variable names found in the template.
     */
    fun extractVariables(template: String): List<String> {
        val variables = mutableListOf<String>()
        variables.addAll(extractBraceVariables(template))
        variables.addAll(extractPercentVariables(template))
        return variables.distinct()
    }

    /**
     * Extract variables in {variable} format
     */
    private fun extractBraceVariables(template: String): List<String> {
        val variables = mutableListOf<String>()
        val pattern = Regex("""\{([^}]+)\}""")
        val matches = pattern.findAll(template)
        for (match in matches) {
            val variable = match.groupValues[1]
            if (variable.isNotBlank()) {
                variables.add(variable)
            }
        }
        return variables
    }

    /**
     * Extract variables in %variable% format
     */
    private fun extractPercentVariables(template: String): List<String> {
        val variables = mutableListOf<String>()
        val pattern = Regex("""%([^%]+)%""")
        val matches = pattern.findAll(template)
        for (match in matches) {
            val variable = match.groupValues[1]
            if (variable.isNotBlank()) {
                variables.add(variable)
            }
        }
        return variables
    }

    /**
     * Validate template - check for unknown/unsupported variables
     */
    fun validate(template: String): TemplateValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val variables = extractVariables(template)

        for (variable in variables) {
            when {
                variable in SYSTEM_VARIABLES -> {
                    // Valid system variable
                }
                variable.startsWith("커스텀:") -> {
                    // Custom field - warn if empty key
                    val key = variable.substring(3)
                    if (key.isBlank()) {
                        errors.add("커스텀 필드 키가 비어있습니다: $variable")
                    }
                }
                else -> {
                    errors.add("알 수 없는 변수: $variable")
                }
            }
        }

        // Check for empty template
        if (template.isBlank()) {
            errors.add("템플릿이 비어있습니다")
        }

        // Check template length
        if (template.length > 2000) {
            warnings.add("템플릿이 매우 깁니다 (${template.length}자)")
        }

        return TemplateValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            variableCount = variables.size
        )
    }

    /**
     * Get all supported variables with descriptions
     */
    fun getSupportedVariables(): List<TemplateVariable> {
        return listOf(
            TemplateVariable(
                name = "고객명",
                description = "고객의 이름",
                example = "김철수",
                required = false
            ),
            TemplateVariable(
                name = "전화번호",
                description = "고객의 전화번호",
                example = "010-1234-5678",
                required = false
            ),
            TemplateVariable(
                name = "날짜",
                description = "현재 날짜 (YYYY-MM-DD)",
                example = "2026-03-17",
                required = false
            ),
            TemplateVariable(
                name = "시간",
                description = "현재 시간 (HH:mm)",
                example = "14:30",
                required = false
            ),
            TemplateVariable(
                name = "요일",
                description = "요일 (월~일)",
                example = "화요일",
                required = false
            ),
            TemplateVariable(
                name = "회사명",
                description = "회사/조직명",
                example = "비즈커넥트",
                required = false
            ),
            TemplateVariable(
                name = "담당자",
                description = "담당자/관리자명",
                example = "이영희",
                required = false
            ),
            TemplateVariable(
                name = "시스템날짜",
                description = "시스템 날짜 (YYYY-MM-DD)",
                example = "2026-03-17",
                required = false
            ),
            TemplateVariable(
                name = "시스템시간",
                description = "시스템 시간 (HH:mm:ss)",
                example = "14:30:45",
                required = false
            ),
            TemplateVariable(
                name = "이름",
                description = "고객의 이름 (고객명과 동일)",
                example = "김철수",
                required = false
            ),
            TemplateVariable(
                name = "기념일",
                description = "고객 기념일",
                example = "2026-05-05",
                required = false
            ),
            TemplateVariable(
                name = "주소",
                description = "고객 주소",
                example = "서울시 강남구",
                required = false
            ),
            TemplateVariable(
                name = "메모",
                description = "고객 메모",
                example = "VIP 고객",
                required = false
            ),
            TemplateVariable(
                name = "커스텀1",
                description = "사용자 정의 필드 1",
                example = "값1",
                required = false
            ),
            TemplateVariable(
                name = "커스텀2",
                description = "사용자 정의 필드 2",
                example = "값2",
                required = false
            ),
            TemplateVariable(
                name = "커스텀3",
                description = "사용자 정의 필드 3",
                example = "값3",
                required = false
            ),
            TemplateVariable(
                name = "커스텀4",
                description = "사용자 정의 필드 4",
                example = "값4",
                required = false
            ),
            TemplateVariable(
                name = "커스텀5",
                description = "사용자 정의 필드 5",
                example = "값5",
                required = false
            ),
            TemplateVariable(
                name = "커스텀:key",
                description = "커스텀 필드 (예: 커스텀:주문번호)",
                example = "커스텀:주문번호",
                required = false
            )
        )
    }

    /**
     * Get the actual value for a variable
     */
    private fun getVariableValue(variable: String, context: TemplateContext): String {
        return when (variable) {
            "고객명", "이름" -> context.customerName ?: ""
            "전화번호" -> context.phoneNumber ?: ""
            "회사명" -> context.companyName ?: ""
            "담당자" -> context.managerName ?: ""
            "날짜" -> formatDate(context.referenceDate, "yyyy-MM-dd")
            "시간" -> formatDate(context.referenceDate, "HH:mm")
            "요일" -> getDayOfWeekKorean(context.referenceDate)
            "시스템날짜" -> formatDate(System.currentTimeMillis(), "yyyy-MM-dd")
            "시스템시간" -> formatDate(System.currentTimeMillis(), "HH:mm:ss")
            "기념일" -> context.customFields["기념일"] ?: ""
            "주소" -> context.customFields["주소"] ?: ""
            "메모" -> context.customFields["메모"] ?: ""
            "커스텀1" -> context.customFields["커스텀1"] ?: ""
            "커스텀2" -> context.customFields["커스텀2"] ?: ""
            "커스텀3" -> context.customFields["커스텀3"] ?: ""
            "커스텀4" -> context.customFields["커스텀4"] ?: ""
            "커스텀5" -> context.customFields["커스텀5"] ?: ""
            else -> {
                // Check for custom fields
                if (variable.startsWith("커스텀:")) {
                    val key = variable.substring(3)
                    context.customFields[key] ?: ""
                } else {
                    ""
                }
            }
        }
    }

    /**
     * Format timestamp to string with given pattern
     */
    private fun formatDate(timestamp: Long, pattern: String): String {
        return try {
            val sdf = SimpleDateFormat(pattern, Locale.KOREA)
            sdf.format(timestamp)
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Get Korean day of week from timestamp
     */
    private fun getDayOfWeekKorean(timestamp: Long): String {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }

        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        return when (dayOfWeek) {
            Calendar.SUNDAY -> "일요일"
            Calendar.MONDAY -> "월요일"
            Calendar.TUESDAY -> "화요일"
            Calendar.WEDNESDAY -> "수요일"
            Calendar.THURSDAY -> "목요일"
            Calendar.FRIDAY -> "금요일"
            Calendar.SATURDAY -> "토요일"
            else -> ""
        }
    }

    /**
     * Get formatted preview of a template with sample data
     */
    fun getPreview(
        template: String,
        context: TemplateContext = TemplateContext()
    ): String {
        return process(template, context)
    }

    /**
     * Batch process multiple templates
     */
    fun processBatch(
        templates: List<String>,
        contexts: List<TemplateContext>
    ): List<String> {
        require(templates.size == contexts.size) {
            "Templates and contexts must have same size"
        }

        return templates.mapIndexed { index, template ->
            process(template, contexts[index])
        }
    }

    /**
     * Check if variable is supported
     */
    fun isSupportedVariable(variable: String): Boolean {
        return variable in SYSTEM_VARIABLES || variable.startsWith("커스텀:")
    }

    /**
     * Get variable statistics for a template
     */
    fun getVariableStats(template: String): VariableStats {
        val variables = extractVariables(template)
        val systemVars = variables.filter { it in SYSTEM_VARIABLES }
        val customVars = variables.filter { it.startsWith("커스텀:") }
        val invalidVars = variables.filterNot {
            it in SYSTEM_VARIABLES || it.startsWith("커스텀:")
        }

        return VariableStats(
            totalVariables = variables.size,
            systemVariables = systemVars,
            customVariables = customVars,
            invalidVariables = invalidVars
        )
    }
}

/**
 * Information about a supported template variable
 */
data class TemplateVariable(
    val name: String,
    val description: String,
    val example: String,
    val required: Boolean = false
)

/**
 * Result of template validation
 */
data class TemplateValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val variableCount: Int
)

/**
 * Statistics about variables in a template
 */
data class VariableStats(
    val totalVariables: Int,
    val systemVariables: List<String>,
    val customVariables: List<String>,
    val invalidVariables: List<String>
) {
    val hasInvalidVariables: Boolean
        get() = invalidVariables.isNotEmpty()
}
