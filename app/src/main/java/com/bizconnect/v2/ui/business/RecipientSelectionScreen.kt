package com.bizconnect.v2.ui.business

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.local.db.dao.CategoryDao
import com.bizconnect.v2.data.local.db.dao.ContactDao
import com.bizconnect.v2.data.local.db.entity.CategoryEntity
import com.bizconnect.v2.data.local.db.entity.ContactEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Recipient(val name: String, val phone: String)

@HiltViewModel
class RecipientSelectionViewModel @Inject constructor(
    private val contactDao: ContactDao,
    private val categoryDao: CategoryDao
) : ViewModel() {

    private val _allContacts = MutableStateFlow<List<ContactEntity>>(emptyList())
    val allContacts: StateFlow<List<ContactEntity>> = _allContacts

    private val _categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val categories: StateFlow<List<CategoryEntity>> = _categories

    private val _categoryContacts = MutableStateFlow<List<ContactEntity>>(emptyList())
    val categoryContacts: StateFlow<List<ContactEntity>> = _categoryContacts

    init {
        viewModelScope.launch { contactDao.getAll().collect { _allContacts.value = it } }
        viewModelScope.launch { categoryDao.getAllFlow().collect { _categories.value = it } }
    }

    fun searchContacts(query: String): List<ContactEntity> {
        if (query.isBlank()) return _allContacts.value
        val q = query.lowercase()
        return _allContacts.value.filter {
            it.name.lowercase().contains(q) || it.phoneNumber.contains(q)
        }
    }

    fun loadCategoryContacts(categoryId: Long) {
        viewModelScope.launch {
            val phones = categoryDao.getContactPhonesForCategory(categoryId)
            val contacts = phones.mapNotNull { phone -> contactDao.getByPhone(phone) }
            _categoryContacts.value = contacts
        }
    }

    suspend fun getContactPhonesForCategory(categoryId: Long): List<String> {
        return categoryDao.getContactPhonesForCategory(categoryId)
    }

    suspend fun getContactByPhone(phone: String): ContactEntity? {
        return contactDao.getByPhone(phone)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipientSelectionScreen(
    onBackClick: () -> Unit,
    onConfirm: (List<Recipient>) -> Unit,
    viewModel: RecipientSelectionViewModel = hiltViewModel()
) {
    val allContacts by viewModel.allContacts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val categoryContacts by viewModel.categoryContacts.collectAsStateWithLifecycle()

    var selectedRecipients by remember { mutableStateOf(mapOf<String, Recipient>()) }
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    val tabTitles = listOf("주소록", "카테고리", "번호입력")

    // Filter contacts by search
    val displayContacts = if (searchQuery.isBlank()) allContacts else viewModel.searchContacts(searchQuery)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("수신자 선택") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = { onConfirm(selectedRecipients.values.toList()) },
                enabled = selectedRecipients.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "선택 완료 (${selectedRecipients.size}명)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            when (selectedTab) {
                // === 주소록 (검색 포함) ===
                0 -> {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        placeholder = { Text("이름 또는 번호 검색") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            selectedRecipients = selectedRecipients + displayContacts.associate {
                                it.phoneNumber to Recipient(it.name, it.phoneNumber)
                            }
                        }) { Text("전체 선택") }
                        TextButton(onClick = {
                            val phones = displayContacts.map { it.phoneNumber }.toSet()
                            selectedRecipients = selectedRecipients.filterKeys { it !in phones }
                        }) { Text("전체 해제") }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(displayContacts, key = { it.id }) { contact ->
                            ContactCheckItem(
                                name = contact.name,
                                phone = contact.phoneNumber,
                                isChecked = contact.phoneNumber in selectedRecipients,
                                onToggle = { checked ->
                                    selectedRecipients = if (checked) {
                                        selectedRecipients + (contact.phoneNumber to Recipient(contact.name, contact.phoneNumber))
                                    } else {
                                        selectedRecipients - contact.phoneNumber
                                    }
                                }
                            )
                        }
                    }
                }

                // === 카테고리 → 명단 표시 ===
                1 -> {
                    // Category chips
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        categories.forEach { category ->
                            FilterChip(
                                selected = selectedCategoryId == category.id,
                                onClick = {
                                    selectedCategoryId = if (selectedCategoryId == category.id) null else category.id
                                    selectedCategoryId?.let { viewModel.loadCategoryContacts(it) }
                                },
                                label = { Text(category.name) }
                            )
                        }
                    }

                    if (selectedCategoryId != null) {
                        // Select all / deselect for this category
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${categoryContacts.size}명", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row {
                                TextButton(onClick = {
                                    selectedRecipients = selectedRecipients + categoryContacts.associate {
                                        it.phoneNumber to Recipient(it.name, it.phoneNumber)
                                    }
                                }) { Text("전체 추가") }
                                TextButton(onClick = {
                                    val phones = categoryContacts.map { it.phoneNumber }.toSet()
                                    selectedRecipients = selectedRecipients.filterKeys { it !in phones }
                                }) { Text("전체 제거") }
                            }
                        }

                        // Contact list for selected category
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(categoryContacts, key = { it.id }) { contact ->
                                ContactCheckItem(
                                    name = contact.name,
                                    phone = contact.phoneNumber,
                                    isChecked = contact.phoneNumber in selectedRecipients,
                                    onToggle = { checked ->
                                        selectedRecipients = if (checked) {
                                            selectedRecipients + (contact.phoneNumber to Recipient(contact.name, contact.phoneNumber))
                                        } else {
                                            selectedRecipients - contact.phoneNumber
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        Text(
                            "카테고리를 선택하면 명단이 표시됩니다",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }

                // === 번호 직접 입력 ===
                2 -> {
                    var phoneInput by remember { mutableStateOf("") }
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { phoneInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("전화번호 입력") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true, shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = {
                                val trimmed = phoneInput.trim()
                                if (trimmed.isNotEmpty() && trimmed !in selectedRecipients) {
                                    scope.launch {
                                        val contact = viewModel.getContactByPhone(trimmed)
                                        selectedRecipients = selectedRecipients + (trimmed to Recipient(contact?.name ?: trimmed, trimmed))
                                    }
                                    phoneInput = ""
                                }
                            }, enabled = phoneInput.trim().isNotEmpty()) { Text("추가") }
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // Show all selected recipients
                        Text("선택된 수신자", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            selectedRecipients.forEach { (phone, recipient) ->
                                InputChip(
                                    selected = true,
                                    onClick = { selectedRecipients = selectedRecipients - phone },
                                    label = { Text(if (recipient.name != phone) "${recipient.name}" else phone, style = MaterialTheme.typography.labelSmall) },
                                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "삭제", modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactCheckItem(
    name: String,
    phone: String,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isChecked) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isChecked, onCheckedChange = { onToggle(it) })
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
