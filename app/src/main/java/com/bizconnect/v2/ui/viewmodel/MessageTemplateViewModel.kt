package com.bizconnect.v2.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizconnect.v2.data.local.db.dao.MessageTemplateDao
import com.bizconnect.v2.data.local.db.entity.MessageTemplateEntity
import com.bizconnect.v2.domain.engine.TemplateEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MessageTemplateViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val messageTemplateDao: MessageTemplateDao,
    private val templateEngine: TemplateEngine
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val templates: StateFlow<List<MessageTemplateEntity>> = _selectedCategory
        .flatMapLatest { category ->
            if (category == null) {
                messageTemplateDao.getAllFlow()
            } else {
                messageTemplateDao.getByCategory(category)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch {
            messageTemplateDao.deleteById(id)
        }
    }

    fun getPreview(content: String): String {
        val sampleContext = TemplateEngine.TemplateContext(
            customerName = "홍길동",
            phoneNumber = "010-1234-5678",
            companyName = "비즈커넥트",
            managerName = "김담당",
            customFields = mapOf(
                "기념일" to "2026-05-05",
                "주소" to "서울시 강남구",
                "메모" to "VIP 고객",
                "커스텀1" to "값1",
                "커스텀2" to "값2",
                "커스텀3" to "값3",
                "커스텀4" to "값4",
                "커스텀5" to "값5"
            )
        )
        return templateEngine.process(content, sampleContext)
    }

    // --- Edit screen support ---

    private val _editTemplate = MutableStateFlow<MessageTemplateEntity?>(null)
    val editTemplate: StateFlow<MessageTemplateEntity?> = _editTemplate.asStateFlow()

    private val _editName = MutableStateFlow("")
    val editName: StateFlow<String> = _editName.asStateFlow()

    private val _editContent = MutableStateFlow("")
    val editContent: StateFlow<String> = _editContent.asStateFlow()

    private val _editCategory = MutableStateFlow("general")
    val editCategory: StateFlow<String> = _editCategory.asStateFlow()

    private val _editIsMms = MutableStateFlow(false)
    val editIsMms: StateFlow<Boolean> = _editIsMms.asStateFlow()

    private val _editImageUri = MutableStateFlow<String?>(null)
    val editImageUri: StateFlow<String?> = _editImageUri.asStateFlow()

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

    fun loadTemplate(id: Long) {
        viewModelScope.launch {
            val template = messageTemplateDao.getById(id)
            if (template != null) {
                _editTemplate.value = template
                _editName.value = template.name
                _editContent.value = template.content
                _editCategory.value = template.category
                _editIsMms.value = template.isMms
                _editImageUri.value = template.imageUri
            }
        }
    }

    fun resetEditState() {
        _editTemplate.value = null
        _editName.value = ""
        _editContent.value = ""
        _editCategory.value = "general"
        _editIsMms.value = false
        _editImageUri.value = null
        _isSaved.value = false
    }

    fun setEditName(name: String) { _editName.value = name }
    fun setEditContent(content: String) { _editContent.value = content }
    fun setEditCategory(category: String) { _editCategory.value = category }
    fun setEditIsMms(isMms: Boolean) { _editIsMms.value = isMms }
    fun setEditImageUri(uri: String?) {
        if (uri == null) {
            _editImageUri.value = null
            return
        }
        // Copy image to internal storage for permanent access
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val permanentUri = copyToInternalStorage(Uri.parse(uri))
            _editImageUri.value = permanentUri
        }
    }

    private fun copyToInternalStorage(sourceUri: Uri): String? {
        return try {
            val inputStream = appContext.contentResolver.openInputStream(sourceUri) ?: return null
            val dir = File(appContext.filesDir, "template_images")
            dir.mkdirs()
            val file = File(dir, "tmpl_${System.nanoTime()}.jpg")
            file.outputStream().use { out -> inputStream.copyTo(out) }
            inputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("TemplateVM", "Failed to copy image: ${e.message}")
            null
        }
    }

    fun quickCreate(name: String, content: String) {
        viewModelScope.launch {
            messageTemplateDao.insert(MessageTemplateEntity(name = name, content = content))
        }
    }

    fun saveTemplate() {
        val name = _editName.value.trim()
        val content = _editContent.value.trim()
        if (name.isBlank() || content.isBlank()) return

        viewModelScope.launch {
            val existing = _editTemplate.value
            if (existing != null) {
                messageTemplateDao.update(
                    existing.copy(
                        name = name,
                        content = content,
                        category = _editCategory.value,
                        isMms = _editIsMms.value,
                        imageUri = _editImageUri.value,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                messageTemplateDao.insert(
                    MessageTemplateEntity(
                        name = name,
                        content = content,
                        category = _editCategory.value,
                        isMms = _editIsMms.value,
                        imageUri = _editImageUri.value
                    )
                )
            }
            _isSaved.value = true
        }
    }
}
