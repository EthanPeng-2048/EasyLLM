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
    val selectedModelName: String? = null,
    val conversationTitle: String = "新对话"
)

class ChatViewModel(
    private val repository: AppRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** 当前加载的对话 ID，null 表示新对话 */
    private var currentConversationId: String? = null

    init {
        updateModelName()
        // 尝试加载当前激活的对话
        loadActiveConversation()
    }

    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun startNewConversation() {
        currentConversationId = null
        _uiState.value = ChatUiState(
            selectedModelName = _uiState.value.selectedModelName,
            conversationTitle = "新对话"
        )
        updateModelName()
    }

    fun loadConversation(conversationId: String) {
        val conversation = repository.getConversation(conversationId) ?: return
        currentConversationId = conversation.id
        repository.setActiveConversation(conversation.id)
        _uiState.value = _uiState.value.copy(
            messages = conversation.messages.map { msg ->
                ChatUiMessage(
                    id = UUID.randomUUID().toString(),
                    role = msg.role,
                    content = msg.content ?: "",
                    isLoading = false
                )
            },
            conversationTitle = conversation.title
        )
    }

    private fun loadActiveConversation() {
        val conversation = repository.getActiveConversation()
        if (conversation != null && conversation.messages.isNotEmpty()) {
            currentConversationId = conversation.id
            _uiState.value = _uiState.value.copy(
                messages = conversation.messages.map { msg ->
                    ChatUiMessage(
                        id = UUID.randomUUID().toString(),
                        role = msg.role,
                        content = msg.content ?: "",
                        isLoading = false
                    )
                },
                conversationTitle = conversation.title
            )
        }
    }

    /** 将当前内存中的消息保存到 Conversation */
    private fun saveConversation() {
        val messages = _uiState.value.messages
        if (messages.isEmpty()) return

        val chatMessages = messages
            .filter { !it.isLoading }
            .map { ChatMessage(role = it.role, content = it.content) }

        val existing = currentConversationId?.let { repository.getConversation(it) }

        if (existing != null) {
            repository.updateConversation(existing.copy(messages = chatMessages))
        } else {
            val firstUserMsg = messages.firstOrNull { it.role == MessageRole.USER }
            val title = firstUserMsg?.content?.take(30)?.replace("\n", " ") ?: "新对话"
            val conversation = top.ethan2048.easyllm.core.model.Conversation(
                id = UUID.randomUUID().toString(),
                title = title,
                messages = chatMessages
            )
            repository.addConversation(conversation)
            currentConversationId = conversation.id
        }
        // 更新标题
        val conversationTitle = currentConversationId?.let { repository.getConversation(it) }?.title ?: "新对话"
        _uiState.value = _uiState.value.copy(conversationTitle = conversationTitle)
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

        // 发送消息前先保存一次（创建/更新对话）
        saveConversation()

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
                        // 流式完成后再次保存（更新 assistant 的完整回复）
                        saveConversation()
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
        currentConversationId = null
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
        // 排除正在加载中的占位消息，保留所有历史消息
        val validMessages = uiMessages.filter { !it.isLoading }
        
        // 如果最后一条消息是正在流式加载中的 assistant 占位，提取其已有内容单独添加
        val pendingAssistant = uiMessages.find { it.isLoading }
        
        return validMessages.map { ChatMessage(role = it.role, content = it.content) } +
            if (pendingAssistant != null) {
                listOf(ChatMessage(role = MessageRole.ASSISTANT, content = pendingAssistant.content))
            } else {
                emptyList()
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
