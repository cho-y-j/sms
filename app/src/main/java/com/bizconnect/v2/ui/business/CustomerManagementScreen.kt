package com.bizconnect.v2.ui.business

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bizconnect.v2.ui.components.AvatarView
import com.bizconnect.v2.ui.components.SearchBar
import com.bizconnect.v2.ui.theme.SamsungBlue
import com.bizconnect.v2.ui.viewmodel.CustomerManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerManagementScreen(
    onBackClick: () -> Unit = {},
    onContactClick: (phone: String) -> Unit = {},
    viewModel: CustomerManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CustomerManagementViewModel.CategoryWithCount?>(null) }

    val tabs = listOf("카테고리", "연락처별")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("고객 관리") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTabIndex == 0) showAddDialog = true
                    else showAddContactDialog = true
                },
                containerColor = SamsungBlue
            ) {
                Icon(Icons.Default.Add, contentDescription = "추가", tint = androidx.compose.ui.graphics.Color.White)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            SearchBar(
                placeholder = if (selectedTabIndex == 0) "카테고리 검색" else "연락처 검색",
                onSearchChange = { searchQuery = it }
            )

            when (selectedTabIndex) {
                0 -> CategoryListTab(
                    categories = uiState.categories.filter {
                        searchQuery.isBlank() || it.category.name.contains(searchQuery, true)
                    },
                    onEdit = { editingCategory = it },
                    onDelete = { viewModel.deleteCategory(it.category.id) },
                    onContactClick = onContactClick,
                    viewModel = viewModel
                )
                1 -> ContactsByCategoryTab(
                    contactsWithCategories = uiState.contactsWithCategories.filter {
                        searchQuery.isBlank() || it.name.contains(searchQuery, true) || it.phone.contains(searchQuery)
                    },
                    onContactClick = onContactClick
                )
            }
        }
    }

    // Add category dialog
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("카테고리 추가") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("카테고리 이름") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.addCategory(name.trim())
                        showAddDialog = false
                    }
                }) { Text("추가") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("취소") }
            }
        )
    }

    // Edit category dialog
    editingCategory?.let { catWithCount ->
        var name by remember { mutableStateOf(catWithCount.category.name) }
        AlertDialog(
            onDismissRequest = { editingCategory = null },
            title = { Text("카테고리 수정") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("카테고리 이름") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.updateCategory(catWithCount.category.id, name.trim())
                        editingCategory = null
                    }
                }) { Text("저장") }
            },
            dismissButton = {
                TextButton(onClick = { editingCategory = null }) { Text("취소") }
            }
        )
    }

    // Add contact dialog
    if (showAddContactDialog) {
        var contactName by remember { mutableStateOf("") }
        var contactPhone by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddContactDialog = false },
            title = { Text("연락처 등록") },
            text = {
                Column {
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = { Text("이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text("전화번호") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (contactName.isNotBlank() && contactPhone.isNotBlank()) {
                        viewModel.addContact(contactName.trim(), contactPhone.trim())
                        showAddContactDialog = false
                    }
                }) { Text("등록") }
            },
            dismissButton = {
                TextButton(onClick = { showAddContactDialog = false }) { Text("취소") }
            }
        )
    }
}

@Composable
fun CategoryListTab(
    categories: List<CustomerManagementViewModel.CategoryWithCount>,
    onEdit: (CustomerManagementViewModel.CategoryWithCount) -> Unit,
    onDelete: (CustomerManagementViewModel.CategoryWithCount) -> Unit,
    onContactClick: (phone: String) -> Unit = {},
    viewModel: CustomerManagementViewModel? = null
) {
    if (categories.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("카테고리를 추가해주세요", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var expandedCategoryId by remember { mutableStateOf<Long?>(null) }
    var expandedContacts by remember { mutableStateOf<List<CustomerManagementViewModel.ContactWithCategories>>(emptyList()) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(categories) { catWithCount ->
            val isExpanded = expandedCategoryId == catWithCount.category.id

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isExpanded) {
                            expandedCategoryId = null
                            expandedContacts = emptyList()
                        } else {
                            expandedCategoryId = catWithCount.category.id
                            viewModel?.getContactsForCategory(catWithCount.category.id) { contacts ->
                                expandedContacts = contacts
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(48.dp)
                        .background(SamsungBlue.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = SamsungBlue
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = catWithCount.category.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${catWithCount.contactCount}명",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { onEdit(catWithCount) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "수정",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = { onDelete(catWithCount) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "삭제",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expanded contacts list
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    if (expandedContacts.isEmpty()) {
                        Text(
                            text = "연락처가 없습니다",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 76.dp, top = 8.dp, bottom = 8.dp)
                        )
                    } else {
                        expandedContacts.forEach { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onContactClick(contact.phone) }
                                    .padding(start = 76.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarView(name = contact.name, size = 32.dp, modifier = Modifier.padding(end = 10.dp))
                                Column {
                                    Text(
                                        text = contact.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = contact.phone,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

@Composable
fun ContactsByCategoryTab(
    contactsWithCategories: List<CustomerManagementViewModel.ContactWithCategories>,
    onContactClick: (phone: String) -> Unit = {}
) {
    if (contactsWithCategories.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("연락처가 없습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(contactsWithCategories) { contact ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onContactClick(contact.phone) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarView(name = contact.name, size = 44.dp, modifier = Modifier.padding(end = 12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(contact.name, style = MaterialTheme.typography.titleSmall)
                    Text(contact.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (contact.categoryNames.isNotEmpty()) {
                        Row(modifier = Modifier.padding(top = 4.dp)) {
                            contact.categoryNames.forEach { catName ->
                                Text(
                                    text = catName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SamsungBlue,
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .background(SamsungBlue.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}
