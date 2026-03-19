package com.bizconnect.v2.ui.business

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bizconnect.v2.data.local.db.entity.SpamFilterEntity
import com.bizconnect.v2.data.remote.spam.SpamApiService
import com.bizconnect.v2.ui.components.SearchBar
import com.bizconnect.v2.ui.theme.SamsungBlue
import com.bizconnect.v2.ui.theme.SamsungRed
import com.bizconnect.v2.ui.viewmodel.SpamManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpamManagementScreen(
    onBackClick: () -> Unit = {},
    onAddBlockClick: () -> Unit = {},
    viewModel: SpamManagementViewModel = hiltViewModel()
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val aggressiveMode by viewModel.aggressiveMode.collectAsStateWithLifecycle()
    val overseasBlockEnabled by viewModel.overseasBlockEnabled.collectAsStateWithLifecycle()
    val overseasExceptions by viewModel.overseasExceptions.collectAsStateWithLifecycle()

    val blockedNumbers by viewModel.blockedNumbers.collectAsStateWithLifecycle()
    val keywordFilters by viewModel.keywordFilters.collectAsStateWithLifecycle()
    val spamStats by viewModel.spamStats.collectAsStateWithLifecycle()
    val lookupResult by viewModel.lookupResult.collectAsStateWithLifecycle()
    val isLookingUp by viewModel.isLookingUp.collectAsStateWithLifecycle()
    val lookupError by viewModel.lookupError.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var showExceptionDialog by remember { mutableStateOf(false) }

    val tabs = listOf("차단 번호", "키워드 필터", "통계", "번호 조회")

    // Filter by search query
    val filteredNumbers = if (searchQuery.isBlank()) blockedNumbers
        else blockedNumbers.filter { it.value.contains(searchQuery, ignoreCase = true) }

    val filteredKeywords = if (searchQuery.isBlank()) keywordFilters
        else keywordFilters.filter { it.value.contains(searchQuery, ignoreCase = true) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "스팸 관리",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTabIndex < 2) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = SamsungBlue
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add filter",
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Security mode toggle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        SamsungRed.copy(alpha = 0.1f),
                        RoundedCornerShape(0.dp)
                    )
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "적극적 보안 모드",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "의심 메시지를 자동으로 차단",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Switch(
                        checked = aggressiveMode,
                        onCheckedChange = { viewModel.setAggressiveMode(it) }
                    )
                }
            }

            // Block unknown numbers (not in contacts)
            SpamToggleItem(
                title = "모르는 번호 차단",
                subtitle = "연락처에 없는 번호의 문자를 차단",
                isChecked = viewModel.blockUnknownEnabled.collectAsStateWithLifecycle().value,
                onCheckedChange = { viewModel.toggleBlockUnknown(it) }
            )

            // Block all except contacts
            SpamToggleItem(
                title = "연락처만 허용",
                subtitle = "연락처에 있는 번호만 문자 수신 (나머지 모두 차단)",
                isChecked = viewModel.contactsOnlyEnabled.collectAsStateWithLifecycle().value,
                onCheckedChange = { viewModel.toggleContactsOnly(it) }
            )

            // Block number patterns
            SpamToggleItem(
                title = "070 번호 차단",
                subtitle = "인터넷 전화(070) 번호 자동 차단",
                isChecked = viewModel.block070Enabled.collectAsStateWithLifecycle().value,
                onCheckedChange = { viewModel.toggleBlock070(it) }
            )

            // Overseas blocking section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        SamsungBlue.copy(alpha = 0.08f),
                        RoundedCornerShape(0.dp)
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = SamsungBlue,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(end = 4.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "해외번호 차단",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "한국 번호가 아닌 해외 번호를 자동 차단",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Switch(
                            checked = overseasBlockEnabled,
                            onCheckedChange = { viewModel.toggleOverseasBlock(it) }
                        )
                    }

                    if (overseasBlockEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showExceptionDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("예외 번호 관리 (${overseasExceptions.size}개)")
                        }
                    }
                }
            }

            // Tab row
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            // Search bar (only for first two tabs)
            if (selectedTabIndex < 2) {
                SearchBar(
                    placeholder = when (selectedTabIndex) {
                        0 -> "번호 검색"
                        else -> "키워드 검색"
                    },
                    onSearchChange = { searchQuery = it }
                )
            }

            // Tab content
            when (selectedTabIndex) {
                0 -> BlockedNumbersTab(
                    numbers = filteredNumbers,
                    onDelete = { viewModel.deleteFilter(it) }
                )
                1 -> KeywordFiltersTab(
                    keywords = filteredKeywords,
                    onDelete = { viewModel.deleteFilter(it) }
                )
                2 -> SpamStatsTab(
                    last30DaysBlocked = spamStats.last30DaysBlocked,
                    blockedNumbersCount = spamStats.blockedNumbers,
                    keywordFiltersCount = spamStats.keywordFilters
                )
                3 -> NumberLookupTab(
                    lookupResult = lookupResult,
                    isLookingUp = isLookingUp,
                    lookupError = lookupError,
                    onLookup = { viewModel.lookupNumber(it) },
                    onBlock = { number, reason ->
                        viewModel.addBlockedNumber(number, reason)
                    },
                    onClear = { viewModel.clearLookupResult() }
                )
            }
        }
    }

    // Add dialog
    if (showAddDialog) {
        AddFilterDialog(
            isNumberTab = selectedTabIndex == 0,
            onDismiss = { showAddDialog = false },
            onConfirm = { value, reason ->
                if (selectedTabIndex == 0) {
                    viewModel.addBlockedNumber(value, reason.ifBlank { null })
                } else {
                    viewModel.addKeywordFilter(value)
                }
                showAddDialog = false
            }
        )
    }

    // Overseas exception dialog
    if (showExceptionDialog) {
        OverseasExceptionDialog(
            exceptions = overseasExceptions,
            onDismiss = { showExceptionDialog = false },
            onAdd = { phone -> viewModel.addOverseasException(phone) },
            onRemove = { exception -> viewModel.removeOverseasException(exception) }
        )
    }
}

@Composable
fun OverseasExceptionDialog(
    exceptions: List<SpamFilterEntity>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (SpamFilterEntity) -> Unit
) {
    var newNumber by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("해외번호 예외 관리") },
        text = {
            Column {
                Text(
                    text = "해외 번호 차단에서 제외할 번호를 관리합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newNumber,
                        onValueChange = { newNumber = it },
                        label = { Text("전화번호") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            if (newNumber.isNotBlank()) {
                                onAdd(newNumber)
                                newNumber = ""
                            }
                        },
                        enabled = newNumber.isNotBlank()
                    ) {
                        Text("추가")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (exceptions.isEmpty()) {
                    Text(
                        text = "등록된 예외 번호가 없습니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        exceptions.forEach { exception ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = exception.value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = { onRemove(exception) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "삭제",
                                        tint = SamsungRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

@Composable
fun SpamStatsTab(
    last30DaysBlocked: Int,
    blockedNumbersCount: Int,
    keywordFiltersCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "스팸 차단 통계",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        StatsCard(
            icon = Icons.Default.Shield,
            title = "최근 30일 차단 수",
            value = "${last30DaysBlocked}건",
            color = SamsungBlue
        )

        StatsCard(
            icon = Icons.Default.Block,
            title = "차단 번호 수",
            value = "${blockedNumbersCount}개",
            color = SamsungRed
        )

        StatsCard(
            icon = Icons.Default.FilterList,
            title = "키워드 필터 수",
            value = "${keywordFiltersCount}개",
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
fun StatsCard(
    icon: ImageVector,
    title: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun AddFilterDialog(
    isNumberTab: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (value: String, reason: String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isNumberTab) "번호 차단 추가" else "키워드 필터 추가")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(if (isNumberTab) "전화번호" else "키워드") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (isNumberTab) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("차단 사유 (선택)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (value.isNotBlank()) onConfirm(value, reason) },
                enabled = value.isNotBlank()
            ) {
                Text("추가")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
fun BlockedNumbersTab(
    numbers: List<SpamFilterEntity>,
    onDelete: (SpamFilterEntity) -> Unit = {}
) {
    if (numbers.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "차단된 번호가 없습니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            items(numbers, key = { it.id }) { item ->
                BlockedNumberItem(item, onDelete = { onDelete(item) })
            }
        }
    }
}

@Composable
fun BlockedNumberItem(item: SpamFilterEntity, onDelete: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = null,
                tint = SamsungRed,
                modifier = Modifier.padding(end = 12.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = item.reason ?: "차단됨",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = SamsungRed
                )
            }
        }
    }
}

@Composable
fun KeywordFiltersTab(
    keywords: List<SpamFilterEntity>,
    onDelete: (SpamFilterEntity) -> Unit = {}
) {
    if (keywords.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "키워드 필터가 없습니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            items(keywords, key = { it.id }) { item ->
                KeywordFilterItem(item, onDelete = { onDelete(item) })
            }
        }
    }
}

@Composable
fun KeywordFilterItem(item: SpamFilterEntity, onDelete: () -> Unit = {}) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "\"${item.value}\"",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = if (item.isActive) "활성" else "비활성",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = SamsungRed
                )
            }
        }
    }
}

@Composable
fun NumberLookupTab(
    lookupResult: SpamApiService.SpamCheckResult?,
    isLookingUp: Boolean,
    lookupError: String?,
    onLookup: (String) -> Unit,
    onBlock: (String, String?) -> Unit,
    onClear: () -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "전화번호 스팸 조회",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "외부 스팸 데이터베이스에서 번호를 조회합니다",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = {
                    phoneNumber = it
                    onClear()
                },
                label = { Text("전화번호") },
                placeholder = { Text("01012345678") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = { onLookup(phoneNumber) },
                enabled = phoneNumber.isNotBlank() && !isLookingUp
            ) {
                if (isLookingUp) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("조회")
                }
            }
        }

        // Error message
        lookupError?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Result card
        lookupResult?.let { result ->
            if (result.source != "none") {
                SpamLookupResultCard(
                    result = result,
                    phoneNumber = phoneNumber,
                    onBlock = onBlock
                )
            }
        }
    }
}

@Composable
fun SpamLookupResultCard(
    result: SpamApiService.SpamCheckResult,
    phoneNumber: String,
    onBlock: (String, String?) -> Unit
) {
    val containerColor = if (result.isSpam) {
        SamsungRed.copy(alpha = 0.1f)
    } else {
        androidx.compose.ui.graphics.Color(0xFF4CAF50).copy(alpha = 0.1f)
    }

    val statusIcon = if (result.isSpam) Icons.Default.Warning else Icons.Default.CheckCircle
    val statusColor = if (result.isSpam) SamsungRed else androidx.compose.ui.graphics.Color(0xFF4CAF50)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (result.isSpam) "스팸 의심 번호" else "안전한 번호",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "출처: ${result.source.uppercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Score bar
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "위험도 점수",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${result.spamScore}/100",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { result.spamScore / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            // Spam type
            result.spamType?.let { type ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "유형",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = type,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Report count
            if (result.reportCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "신고 수",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${result.reportCount}건",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Block button if spam
            if (result.isSpam) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        onBlock(
                            phoneNumber,
                            result.spamType ?: "스팸 DB 조회 결과 차단"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("차단 추가")
                }
            }
        }
    }
}


@Composable
private fun SpamToggleItem(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                SamsungBlue.copy(alpha = 0.04f),
                RoundedCornerShape(0.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = isChecked, onCheckedChange = onCheckedChange)
        }
    }
}
