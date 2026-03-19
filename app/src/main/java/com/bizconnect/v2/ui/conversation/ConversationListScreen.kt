package com.bizconnect.v2.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bizconnect.v2.ui.components.DefaultSmsAppBanner
import com.bizconnect.v2.ui.components.EmptyState
import com.bizconnect.v2.ui.components.SearchBar
import com.bizconnect.v2.ui.theme.SamsungBlue
import com.bizconnect.v2.ui.viewmodel.ConversationListViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onConversationClick: (Long) -> Unit = {},
    onNewMessageClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    permissionsGranted: Boolean = false,
    viewModel: ConversationListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var selectedConversationPhone by remember { mutableStateOf("") }
    var selectedContactCategories by remember { mutableStateOf<List<Long>>(emptyList()) }

    // Trigger sync when permissions are granted
    androidx.compose.runtime.LaunchedEffect(permissionsGranted) {
        if (permissionsGranted) {
            viewModel.triggerSync()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewMessageClick,
                containerColor = SamsungBlue,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New message",
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        val conversations = uiState.conversations

        run {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Fixed top: Title + Settings + Search
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "메시지",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "설정",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                SearchBar(
                    placeholder = "대화 검색",
                    onSearchChange = { viewModel.search(it) }
                )

                CategoryFilterChips(
                    categories = uiState.categories,
                    selectedCategoryId = uiState.selectedCategoryId,
                    onCategorySelected = { viewModel.selectCategory(it) },
                    onAddCategory = { showAddCategoryDialog = true }
                )

                // Empty state
                if (conversations.isEmpty()) {
                    EmptyState(
                        icon = if (uiState.searchQuery.isNotEmpty()) "🔍" else "💬",
                        title = if (uiState.searchQuery.isNotEmpty()) "검색 결과 없음" else "메시지가 없습니다",
                        subtitle = if (uiState.searchQuery.isNotEmpty()) "\"${uiState.searchQuery}\"와 일치하는 대화가 없습니다" else "새 메시지를 시작해보세요"
                    )
                }

                // Scrollable: Conversation list only
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(
                        conversations,
                        key = { it.threadId }
                    ) { conversation ->
                        ConversationListItem(
                            item = ConversationItem(
                                id = conversation.threadId,
                                contactName = conversation.contactName,
                                contactPhoto = conversation.contactPhoto,
                                lastMessage = conversation.lastMessage,
                                timestamp = conversation.timestamp,
                                unreadCount = conversation.unreadCount,
                                isPinned = conversation.isPinned,
                                aiSummary = conversation.aiSummary,
                                aiEmotion = conversation.aiEmotion
                            ),
                            onClick = { onConversationClick(conversation.threadId) },
                            onLongClick = {
                                viewModel.getRecipientAddress(conversation.threadId) { phone ->
                                    selectedConversationPhone = phone
                                    viewModel.getCategoriesForContact(phone) { cats ->
                                        selectedContactCategories = cats
                                        showCategorySheet = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Category change bottom sheet
    if (showCategorySheet && uiState.categories.isNotEmpty()) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showCategorySheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    "카테고리 설정",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                uiState.categories.forEach { category ->
                    val isInCategory = selectedContactCategories.contains(category.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.toggleContactCategory(
                                    selectedConversationPhone, category.id, isInCategory
                                )
                                selectedContactCategories = if (isInCategory) {
                                    selectedContactCategories - category.id
                                } else {
                                    selectedContactCategories + category.id
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = isInCategory,
                            onCheckedChange = {
                                viewModel.toggleContactCategory(
                                    selectedConversationPhone, category.id, isInCategory
                                )
                                selectedContactCategories = if (isInCategory) {
                                    selectedContactCategories - category.id
                                } else {
                                    selectedContactCategories + category.id
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(category.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Add category dialog
    if (showAddCategoryDialog) {
        var categoryName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("카테고리 추가") },
            text = {
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("카테고리 이름") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (categoryName.isNotBlank()) {
                        viewModel.addCategory(categoryName.trim())
                        showAddCategoryDialog = false
                    }
                }) { Text("추가") }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) { Text("취소") }
            }
        )
    }
}

@Composable
fun CategoryFilterChips(
    categories: List<com.bizconnect.v2.data.local.db.entity.CategoryEntity>,
    selectedCategoryId: Long?,
    onCategorySelected: (Long?) -> Unit,
    onAddCategory: () -> Unit
) {
    if (categories.isEmpty() && selectedCategoryId == null) {
        // Show minimal + button when no categories exist
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            AssistChip(
                onClick = onAddCategory,
                label = { Text("카테고리 추가", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "전체" chip
        FilterChip(
            selected = selectedCategoryId == null,
            onClick = { onCategorySelected(null) },
            label = { Text("전체", style = MaterialTheme.typography.labelMedium) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = SamsungBlue,
                selectedLabelColor = androidx.compose.ui.graphics.Color.White
            )
        )

        // Category chips
        categories.forEach { category ->
            FilterChip(
                selected = selectedCategoryId == category.id,
                onClick = { onCategorySelected(category.id) },
                label = { Text(category.name, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SamsungBlue,
                    selectedLabelColor = androidx.compose.ui.graphics.Color.White
                )
            )
        }

        // + Add button
        AssistChip(
            onClick = onAddCategory,
            label = { Text("+", style = MaterialTheme.typography.labelMedium) }
        )
    }
}
