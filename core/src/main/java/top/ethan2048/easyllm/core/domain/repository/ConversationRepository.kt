package top.ethan2048.easyllm.core.domain.repository

import top.ethan2048.easyllm.core.domain.model.Conversation

/**
 * 对话仓库接口
 */
interface ConversationRepository {
    val conversations: List<Conversation>
    val activeConversationId: String?

    fun addConversation(conversation: Conversation)
    fun updateConversation(conversation: Conversation)
    fun deleteConversation(conversationId: String)
    fun setActiveConversation(conversationId: String)
    fun getConversation(conversationId: String): Conversation?
    fun getActiveConversation(): Conversation?
}
