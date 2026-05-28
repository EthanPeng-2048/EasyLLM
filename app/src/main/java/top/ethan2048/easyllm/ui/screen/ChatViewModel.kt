package top.ethan2048.easyllm.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import top.ethan2048.easyllm.core.model.ChatMessage
import top.ethan2048.easyllm.core.model.MessageRole
import top.ethan2048.easyllm.data.AppRepository
import java.util.UUID

data class ChatUiMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val isLoading: Boolean = false
)

data class ChatUiState(
    val messages: List<ChatUiMessage> = emptyList(),
    val inputText: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
    val selectedModelName: String? = null
)

class ChatViewModel(
    private val repository: AppRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        updateModelName()
    }

    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isStreaming) return

        val chatApi = repository.getActiveChatApi()
        if (chatApi == null) {
            _uiState.value = _uiState.value.copy(error = "请先在 API 配置中选择一个供应商和模型")
            return
        }

        val userMessage = ChatUiMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = text
        )

        val currentMessages = _uiState.value.messages + userMessage
        _uiState.value = _uiState.value.copy(
            messages = currentMessages,
            inputText = "",
            isStreaming = true,
            error = null
        )

        viewModelScope.launch {
            val pendingId = UUID.randomUUID().toString()
            // 添加占位消息
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + ChatUiMessage(
                    id = pendingId,
                    role = MessageRole.ASSISTANT,
                    content = "",
                    isLoading = true
                )
            )

            val chatMessages = buildChatMessages(_uiState.value.messages)

            chatApi.chatStream(chatMessages)
                .catch { e ->
                    updateAssistantMessage(pendingId, "错误: ${e.message}", isLoading = false)
                    _uiState.value = _uiState.value.copy(isStreaming = false, error = e.message)
                }
                .collect { chunk ->
                    val delta = chunk.choices.firstOrNull()?.delta
                    val content = delta?.content ?: ""
                    val finishReason = chunk.choices.firstOrNull()?.finishReason

                    appendToAssistantMessage(pendingId, content)

                    if (finishReason != null) {
                        finalizeAssistantMessage(pendingId)
                        _uiState.value = _uiState.value.copy(isStreaming = false)
                    }
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearMessages() {
        _uiState.value = ChatUiState(selectedModelName = _uiState.value.selectedModelName)
        updateModelName()
    }

    fun refreshModelName() {
        updateModelName()
    }

    private fun updateModelName() {
        val vendorId = repository.activeVendorId
        val modelConfigId = repository.activeModelConfigId
        
        val name = if (vendorId != null && modelConfigId != null) {
            val vendor = repository.getVendor(vendorId)
            val modelConfig = vendor?.models?.find { it.id == modelConfigId }
            if (vendor != null && modelConfig != null) {
                "${vendor.name} - ${modelConfig.name}"
            } else null
        } else null
        
        _uiState.value = _uiState.value.copy(selectedModelName = name)
    }

    private fun buildChatMessages(uiMessages: List<ChatUiMessage>): List<ChatMessage> {
        return uiMessages
            .filter { !it.isLoading && it.role != MessageRole.ASSISTANT }
            .map { ChatMessage(role = it.role, content = it.content) }
            .let { list ->
                // 加上当前正在流式响应的 assistant 消息
                val pendingAssistant = uiMessages.find { it.isLoading }
                if (pendingAssistant != null) {
                    list + ChatMessage(role = MessageRole.ASSISTANT, content = pendingAssistant.content)
                } else {
                    list
                }
            }
    }

    private fun updateAssistantMessage(id: String, content: String, isLoading: Boolean) {
        val updated = _uiState.value.messages.map {
            if (it.id == id) it.copy(content = content, isLoading = isLoading) else it
        }
        _uiState.value = _uiState.value.copy(messages = updated)
    }

    private fun appendToAssistantMessage(id: String, delta: String) {
        val updated = _uiState.value.messages.map {
            if (it.id == id) it.copy(content = it.content + delta) else it
        }
        _uiState.value = _uiState.value.copy(messages = updated)
    }

    private fun finalizeAssistantMessage(id: String) {
        val updated = _uiState.value.messages.map {
            if (it.id == id) it.copy(isLoading = false) else it
        }
        _uiState.value = _uiState.value.copy(messages = updated)
    }

    class Factory(private val repository: AppRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(repository) as T
        }
    }
}
