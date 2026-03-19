package com.bizconnect.v2.ui.business

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.bizconnect.v2.ui.theme.SamsungBlue
import com.bizconnect.v2.ui.viewmodel.MessageTemplateViewModel

private val categoryOptions = listOf(
    "general" to "일반",
    "callback" to "콜백",
    "bulk" to "대량발송",
    "scheduled" to "예약발송"
)

private val variableChips = listOf(
    "%이름%",
    "%전화번호%",
    "%날짜%",
    "%시간%",
    "%요일%",
    "%회사명%",
    "%담당자%",
    "%기념일%",
    "%주소%",
    "%메모%"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageTemplateEditScreen(
    templateId: Long? = null,
    onBackClick: () -> Unit = {},
    viewModel: MessageTemplateViewModel = hiltViewModel()
) {
    val editName by viewModel.editName.collectAsStateWithLifecycle()
    val editContent by viewModel.editContent.collectAsStateWithLifecycle()
    val editCategory by viewModel.editCategory.collectAsStateWithLifecycle()
    val editIsMms by viewModel.editIsMms.collectAsStateWithLifecycle()
    val editImageUri by viewModel.editImageUri.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()

    val isNew = templateId == null

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setEditImageUri(uri.toString())
            viewModel.setEditIsMms(true)
        }
    }

    // Content as TextFieldValue for cursor position tracking
    var contentFieldValue by remember { mutableStateOf(TextFieldValue(editContent)) }

    // Load template if editing
    LaunchedEffect(templateId) {
        if (templateId != null) {
            viewModel.loadTemplate(templateId)
        } else {
            viewModel.resetEditState()
        }
    }

    // Sync editContent changes from viewModel (e.g., after load)
    LaunchedEffect(editContent) {
        if (contentFieldValue.text != editContent) {
            contentFieldValue = TextFieldValue(editContent, TextRange(editContent.length))
        }
    }

    // Navigate back after save
    LaunchedEffect(isSaved) {
        if (isSaved) {
            viewModel.resetEditState()
            onBackClick()
        }
    }

    var dropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (isNew) "새 템플릿" else "템플릿 편집",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.resetEditState()
                        onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveTemplate() },
                        enabled = editName.isNotBlank() && editContent.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "저장",
                            tint = if (editName.isNotBlank() && editContent.isNotBlank())
                                SamsungBlue else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Name field
            OutlinedTextField(
                value = editName,
                onValueChange = { viewModel.setEditName(it) },
                label = { Text("템플릿 이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Category dropdown
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = categoryOptions.find { it.first == editCategory }?.second ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("카테고리") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    categoryOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.setEditCategory(value)
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Content field
            OutlinedTextField(
                value = contentFieldValue,
                onValueChange = {
                    contentFieldValue = it
                    viewModel.setEditContent(it.text)
                },
                label = { Text("내용") },
                minLines = 6,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth()
            )

            // Variable insertion toolbar
            Column {
                Text(
                    text = "변수 삽입",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    variableChips.forEach { variable ->
                        AssistChip(
                            onClick = {
                                val cursorPos = contentFieldValue.selection.start
                                val newText = contentFieldValue.text.substring(0, cursorPos) +
                                    variable +
                                    contentFieldValue.text.substring(cursorPos)
                                val newCursorPos = cursorPos + variable.length
                                contentFieldValue = TextFieldValue(newText, TextRange(newCursorPos))
                                viewModel.setEditContent(newText)
                            },
                            label = { Text(variable, style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = SamsungBlue.copy(alpha = 0.1f),
                                labelColor = SamsungBlue
                            )
                        )
                    }
                }
            }

            // Image attachment section
            Column {
                Text(
                    text = "이미지 첨부",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (!editImageUri.isNullOrBlank()) {
                    // Image preview
                    Box(modifier = Modifier.padding(bottom = 8.dp)) {
                        AsyncImage(
                            model = Uri.parse(editImageUri!!),
                            contentDescription = "첨부 이미지",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = {
                                viewModel.setEditImageUri(null)
                                viewModel.setEditIsMms(false)
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.8f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "제거", tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(
                        text = "MMS로 발송됩니다 (63원/건)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Add image button
                    AssistChip(
                        onClick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        label = { Text("이미지 추가") },
                        leadingIcon = {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = SamsungBlue.copy(alpha = 0.1f),
                            labelColor = SamsungBlue,
                            leadingIconContentColor = SamsungBlue
                        )
                    )
                }
            }

            // Preview section
            if (editContent.isNotBlank()) {
                Column {
                    Text(
                        text = "미리보기",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            text = viewModel.getPreview(editContent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Save button
            Button(
                onClick = { viewModel.saveTemplate() },
                enabled = editName.isNotBlank() && editContent.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue)
            ) {
                Text("저장", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
