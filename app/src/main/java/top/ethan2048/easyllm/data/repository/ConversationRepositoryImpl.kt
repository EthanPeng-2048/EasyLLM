package top.ethan2048.easyllm.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.ethan2048.easyllm.core.domain.model.Conversation
import top.ethan2048.easyllm.core.domain.repository.ConversationRepository
import java.util.UUID

/**
 * 对话仓库实现
 */
class ConversationRepositoryImpl(context: Context) : ConversationRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("easyllm_configs", Context.MODE_PRIVATE)

    private val _conversations = mutableListOf<Conversation>()
    override val conversations: List<Conversation> get() = _conversations.toList()

    override var activeConversationId: String? = null
        private set

    init {
        loadConversations()
    }

    override fun addConversation(conversation: Conversation) {
        _conversations.add(0, conversation)
        saveConversations()
    }

    override fun updateConversation(conversation: Conversation) {
        val index = _conversations.indexOfFirst { it.id == conversation.id }
        if (index >= 0) {
            _conversations[index] = conversation.copy(updatedAt = System.currentTimeMillis())
            saveConversations()
        }
    }

    override fun deleteConversation(conversationId: String) {
        _conversations.removeAll { it.id == conversationId }
        if (activeConversationId == conversationId) {
            activeConversationId = _conversations.firstOrNull()?.id
        }
        saveConversations()
    }

    override fun setActiveConversation(conversationId: String) {
        activeConversationId = conversationId
        prefs.edit().putString("active_conversation_id", conversationId).apply()
    }

    override fun getConversation(conversationId: String): Conversation? {
        return _conversations.find { it.id == conversationId }
    }

    override fun getActiveConversation(): Conversation? {
        val id = activeConversationId ?: return null
        return _conversations.find { it.id == id }
    }

    /**
     * 创建新对话
     */
    fun createConversation(title: String, messages: List<top.ethan2048.easyllm.core.domain.model.ChatMessage>): Conversation {
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            title = title,
            messages = messages
        )
        addConversation(conversation)
        return conversation
    }

    private fun loadConversations() {
        try {
            val conversationJson = prefs.getString("conversations", "[]") ?: "[]"
            _conversations.clear()
            _conversations.addAll(json.decodeFromString<List<Conversation>>(conversationJson))
            activeConversationId = prefs.getString("active_conversation_id", _conversations.firstOrNull()?.id)
        } catch (_: Exception) {
            // 解析失败时使用空列表
        }
    }

    private fun saveConversations() {
        prefs.edit().putString("conversations", json.encodeToString(_conversations)).apply()
        prefs.edit().putString("active_conversation_id", activeConversationId).apply()
    }
}
