package com.bizconnect.v2.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bizconnect.v2.data.local.db.entity.MessageTemplateEntity
import com.bizconnect.v2.ui.viewmodel.MessageTemplateViewModel

@Composable
fun TemplatePickerDialog(
    category: String? = null,
    onDismiss: () -> Unit,
    onTemplateSelected: (MessageTemplateEntity) -> Unit,
    onCreateNew: (() -> Unit)? = null,
    viewModel: MessageTemplateViewModel = hiltViewModel()
) {
    val allTemplates by viewModel.templates.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var showCreateForm by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }

    // Filter local templates by category and search
    val filteredTemplates = allTemplates
        .let { list ->
            if (category != null) list.filter { it.category == category } else list
        }
        .let { list ->
            if (searchQuery.isBlank()) list
            else list.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.content.contains(searchQuery, ignoreCase = true)
            }
        }

    // Admin templates filtered by search
    val adminTemplates = viewModel.adminTemplates.value
    val filteredAdminTemplates = adminTemplates.let { list ->
        if (searchQuery.isBlank()) list
        else list.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true)
        }
    }

    // Category label mapping for local templates
    val categoryLabels = mapOf(
        "general" to "일반",
        "callback" to "콜백",
        "bulk" to "대량발송",
        "scheduled" to "예약"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("템플릿 선택") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
            ) {
                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("내 템플릿", fontSize = 13.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("\uD83D\uDCE6 제공 템플릿", fontSize = 13.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                SearchBar(
                    placeholder = "템플릿 검색",
                    onSearchChange = { searchQuery = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                when (selectedTab) {
                    // ============================================================
                    // Tab 0: 내 템플릿
                    // ============================================================
                    0 -> {
                        // 새 템플릿 만들기
                        if (showCreateForm) {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = { Text("템플릿 이름") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = newContent,
                                onValueChange = { newContent = it },
                                label = { Text("내용") },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "이미지 포함 템플릿은 비즈니스 > 문자 관리에서 만들 수 있습니다",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    showCreateForm = false; newName = ""; newContent = ""
                                }) {
                                    Text("취소")
                                }
                                TextButton(onClick = {
                                    if (newName.isNotBlank() && newContent.isNotBlank()) {
                                        viewModel.quickCreate(newName.trim(), newContent.trim())
                                        showCreateForm = false
                                        newName = ""; newContent = ""
                                    }
                                }) {
                                    Text("저장")
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        } else {
                            TextButton(
                                onClick = { showCreateForm = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("+ 새 템플릿 만들기", color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        if (filteredTemplates.isEmpty()) {
                            Text(
                                text = "내 템플릿이 없습니다",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            // Group by category
                            val grouped = filteredTemplates.groupBy { it.category }

                            LazyColumn {
                                grouped.forEach { (cat, items) ->
                                    val label = categoryLabels[cat] ?: cat
                                    item(key = "header_$cat") {
                                        Text(
                                            text = label,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(
                                                top = 12.dp,
                                                bottom = 6.dp,
                                                start = 4.dp
                                            )
                                        )
                                    }
                                    items(items, key = { it.id }) { template ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onTemplateSelected(template) }
                                                .padding(vertical = 10.dp, horizontal = 4.dp)
                                        ) {
                                            Text(
                                                text = template.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = template.content,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }

                    // ============================================================
                    // Tab 1: 제공 템플릿
                    // ============================================================
                    1 -> {
                        if (filteredAdminTemplates.isEmpty()) {
                            Text(
                                text = "제공 템플릿이 없습니다",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            // Group by categoryName
                            val adminGrouped =
                                filteredAdminTemplates.groupBy { it.categoryName }

                            LazyColumn {
                                adminGrouped.forEach { (catName, items) ->
                                    val icon = items.first().categoryIcon
                                    item(key = "admin_header_$catName") {
                                        Text(
                                            text = "$icon $catName",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(
                                                top = 12.dp,
                                                bottom = 6.dp,
                                                start = 4.dp
                                            )
                                        )
                                    }
                                    items(items, key = { "admin_${it.id}" }) { tpl ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onTemplateSelected(
                                                        MessageTemplateEntity(
                                                            name = tpl.title,
                                                            content = tpl.content,
                                                            category = "admin",
                                                            createdAt = System.currentTimeMillis(),
                                                            updatedAt = System.currentTimeMillis()
                                                        )
                                                    )
                                                }
                                                .padding(vertical = 10.dp, horizontal = 4.dp)
                                        ) {
                                            Text(
                                                text = tpl.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = tpl.content.take(60),
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}
