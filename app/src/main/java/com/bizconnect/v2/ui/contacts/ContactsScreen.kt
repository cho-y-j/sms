package com.bizconnect.v2.ui.contacts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bizconnect.v2.ui.components.AvatarView
import com.bizconnect.v2.ui.components.EmptyState
import com.bizconnect.v2.ui.components.SearchBar
import com.bizconnect.v2.ui.viewmodel.ContactsScreenViewModel

data class ContactItem(
    val id: Long,
    val name: String,
    val phone: String,
    val photoUrl: String? = null,
    val group: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onContactClick: (Long) -> Unit = {},
    onCallClick: (String) -> Unit = {},
    onMessageClick: (String) -> Unit = {},
    viewModel: ContactsScreenViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    var showCategorySheet by remember { mutableStateOf(false) }
    var selectedContactPhone by remember { mutableStateOf("") }
    var selectedContactName by remember { mutableStateOf("") }
    var selectedContactCategories by remember { mutableStateOf<List<Long>>(emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SearchBar(
            placeholder = "연락처 검색",
            onSearchChange = {
                searchQuery = it
                viewModel.search(it)
            }
        )

        val contacts = uiState.contacts

        if (contacts.isEmpty() && searchQuery.isNotEmpty()) {
            EmptyState(
                icon = "🔍",
                title = "결과 없음",
                subtitle = "\"$searchQuery\"와 일치하는 연락처가 없습니다"
            )
        } else if (contacts.isEmpty() && !uiState.isLoading) {
            EmptyState(
                icon = "👥",
                title = "연락처가 없습니다",
                subtitle = "새 연락처를 추가해보세요"
            )
        } else {
            val groupedContacts = contacts.groupBy { contact ->
                contact.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            }

            LazyColumn {
                groupedContacts.forEach { (letter, contactsInGroup) ->
                    item(key = "header_$letter") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = letter,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(contactsInGroup, key = { it.id }) { contact ->
                        ContactListItem(
                            contact = contact,
                            onClickContact = { onContactClick(contact.id) },
                            onClickCall = { onCallClick(contact.phone) },
                            onClickMessage = { onMessageClick(contact.phone) },
                            onLongClick = {
                                selectedContactPhone = contact.phone
                                selectedContactName = contact.name
                                viewModel.getCategoriesForContact(contact.phone) { cats ->
                                    selectedContactCategories = cats
                                    showCategorySheet = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Category bottom sheet
    if (showCategorySheet && uiState.categories.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { showCategorySheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    "$selectedContactName 카테고리 설정",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                uiState.categories.forEach { category ->
                    val isIn = selectedContactCategories.contains(category.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.toggleContactCategory(selectedContactPhone, category.id, isIn)
                                selectedContactCategories = if (isIn) selectedContactCategories - category.id
                                else selectedContactCategories + category.id
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isIn,
                            onCheckedChange = {
                                viewModel.toggleContactCategory(selectedContactPhone, category.id, isIn)
                                selectedContactCategories = if (isIn) selectedContactCategories - category.id
                                else selectedContactCategories + category.id
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactListItem(
    contact: ContactItem,
    onClickContact: () -> Unit,
    onClickCall: () -> Unit,
    onClickMessage: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClickContact,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarView(
            name = contact.name,
            photoUrl = contact.photoUrl,
            size = 48.dp,
            modifier = Modifier.padding(end = 12.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = contact.phone,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onClickMessage) {
            Icon(Icons.Default.Forum, contentDescription = "문자", tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onClickCall) {
            Icon(Icons.Default.Call, contentDescription = "전화", tint = MaterialTheme.colorScheme.primary)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
