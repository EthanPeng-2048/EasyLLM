package top.ethan2048.easyllm.data

import android.content.Context
import top.ethan2048.easyllm.api.OpenAiClient
import top.ethan2048.easyllm.core.domain.api.IChatApi
import top.ethan2048.easyllm.core.domain.model.ApiConfig
import top.ethan2048.easyllm.core.domain.model.Model
import top.ethan2048.easyllm.core.domain.model.ModelConfig
import top.ethan2048.easyllm.core.domain.repository.ConversationRepository
import top.ethan2048.easyllm.core.domain.repository.McpClientManager
import top.ethan2048.easyllm.core.domain.repository.McpRepository
import top.ethan2048.easyllm.core.domain.repository.VendorRepository
import top.ethan2048.easyllm.data.repository.ConversationRepositoryImpl
import top.ethan2048.easyllm.data.repository.McpClientManagerImpl
import top.ethan2048.easyllm.data.repository.McpRepositoryImpl
import top.ethan2048.easyllm.data.repository.VendorRepositoryImpl

/**
 * 应用全局数据仓库
 *
 * 整合各个子仓库，提供统一的数据访问入口。
 * 这是重构过程中的过渡方案，最终会将职责完全拆分到各子仓库。
 */
class AppRepository(
    context: Context,
    val vendorRepository: VendorRepository = VendorRepositoryImpl(context),
    val conversationRepository: ConversationRepository = ConversationRepositoryImpl(context),
    val mcpRepository: McpRepository = McpRepositoryImpl(context),
    val mcpClientManager: McpClientManager = McpClientManagerImpl()
) {

    /** 客户端缓存 */
    private val chatApiCache = mutableMapOf<String, IChatApi>()

    // ============ 委托给子仓库 ============

    val vendors get() = vendorRepository.vendors
    val mcpConfigs get() = mcpRepository.mcpConfigs
    val conversations get() = conversationRepository.conversations

    var activeVendorId: String?
        get() = vendorRepository.activeVendorId
        private set(value) { /* 通过 setActiveVendor 修改 */ }

    var activeModelConfigId: String?
        get() = vendorRepository.activeModelConfigId
        private set(value) { /* 通过 setActiveModelConfig 修改 */ }

    var activeConversationId: String?
        get() = conversationRepository.activeConversationId
        private set(value) { /* 通过 setActiveConversation 修改 */ }

    // ============ 供应商管理 ============

    fun addVendor(vendor: top.ethan2048.easyllm.core.domain.model.Vendor) {
        vendorRepository.addVendor(vendor)
    }

    fun updateVendor(vendor: top.ethan2048.easyllm.core.domain.model.Vendor) {
        vendorRepository.updateVendor(vendor)
        chatApiCache.remove(vendor.id)
    }

    fun deleteVendor(vendorId: String) {
        vendorRepository.deleteVendor(vendorId)
        chatApiCache.remove(vendorId)
        if (activeVendorId == vendorId) {
            activeVendorId = vendors.firstOrNull()?.id
            activeModelConfigId = null
        }
    }

    fun setActiveVendor(vendorId: String) {
        vendorRepository.setActiveVendor(vendorId)
    }

    fun getVendor(vendorId: String): top.ethan2048.easyllm.core.domain.model.Vendor? {
        return vendorRepository.getVendor(vendorId)
    }

    // ============ 模型配置管理 ============

    fun addModelConfig(vendorId: String, modelConfig: ModelConfig) {
        vendorRepository.addModelConfig(vendorId, modelConfig)
    }

    fun updateModelConfig(vendorId: String, modelConfig: ModelConfig) {
        vendorRepository.updateModelConfig(vendorId, modelConfig)
    }

    fun deleteModelConfig(vendorId: String, modelConfigId: String) {
        vendorRepository.deleteModelConfig(vendorId, modelConfigId)
        if (activeModelConfigId == modelConfigId) {
            activeModelConfigId = null
        }
    }

    fun setActiveModelConfig(modelConfigId: String) {
        vendorRepository.setActiveModelConfig(modelConfigId)
    }

    // ============ API 客户端 ============

    fun getChatApi(vendorId: String): IChatApi {
        return chatApiCache.getOrPut(vendorId) {
            val vendor = vendorRepository.getVendor(vendorId)
                ?: throw IllegalStateException("Vendor not found: $vendorId")
            val tempConfig = ApiConfig(
                id = vendor.id,
                name = vendor.name,
                endpoint = vendor.endpoint,
                apiKey = vendor.apiKey,
                model = ""
            )
            OpenAiClient(tempConfig)
        }
    }

    suspend fun getModelsFromVendor(vendorId: String): Result<List<Model>> {
        return runCatching {
            val vendor = vendorRepository.getVendor(vendorId)
                ?: throw IllegalStateException("Vendor not found: $vendorId")
            val tempConfig = ApiConfig(
                id = vendor.id,
                name = vendor.name,
                endpoint = vendor.endpoint,
                apiKey = vendor.apiKey,
                model = ""
            )
            val api = OpenAiClient(tempConfig)
            val result = api.getModels()
            result.getOrThrow()
        }
    }

    suspend fun getModelsFromEndpoint(endpoint: String, apiKey: String): Result<List<Model>> {
        return vendorRepository.fetchModelsFromEndpoint(endpoint, apiKey)
    }

    fun getActiveChatApi(): IChatApi? {
        val vendorId = activeVendorId ?: return null
        val modelConfigId = activeModelConfigId ?: return null
        
        val vendor = vendorRepository.getVendor(vendorId) ?: return null
        val modelConfig = vendor.models.find { it.id == modelConfigId } ?: return null
        
        return try {
            val api = getChatApi(vendorId)
            api.config = api.config.copy(
                model = modelConfig.model,
                maxTokens = modelConfig.maxTokens,
                temperature = modelConfig.temperature
            )
            api
        } catch (_: Exception) {
            null
        }
    }

    // ============ MCP 配置管理 ============

    fun addMcpConfig(config: top.ethan2048.easyllm.core.domain.model.mcp.McpServerConfig) {
        mcpRepository.addMcpConfig(config)
    }

    fun updateMcpConfig(config: top.ethan2048.easyllm.core.domain.model.mcp.McpServerConfig) {
        mcpRepository.updateMcpConfig(config)
    }

    fun deleteMcpConfig(configId: String) {
        mcpRepository.deleteMcpConfig(configId)
    }

    fun getMcpClient(): McpClientManager {
        return mcpClientManager
    }

    // ============ 对话管理 ============

    fun addConversation(conversation: top.ethan2048.easyllm.core.domain.model.Conversation) {
        conversationRepository.addConversation(conversation)
    }

    fun updateConversation(conversation: top.ethan2048.easyllm.core.domain.model.Conversation) {
        conversationRepository.updateConversation(conversation)
    }

    fun deleteConversation(conversationId: String) {
        conversationRepository.deleteConversation(conversationId)
        if (activeConversationId == conversationId) {
            activeConversationId = conversations.firstOrNull()?.id
        }
    }

    fun setActiveConversation(conversationId: String) {
        conversationRepository.setActiveConversation(conversationId)
    }

    fun getConversation(conversationId: String): top.ethan2048.easyllm.core.domain.model.Conversation? {
        return conversationRepository.getConversation(conversationId)
    }

    fun getActiveConversation(): top.ethan2048.easyllm.core.domain.model.Conversation? {
        return conversationRepository.getActiveConversation()
    }
}
