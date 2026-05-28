package top.ethan2048.easyllm.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.ethan2048.easyllm.api.OpenAiClient
import top.ethan2048.easyllm.core.`interface`.IChatApi
import top.ethan2048.easyllm.core.`interface`.IMcpClient
import top.ethan2048.easyllm.core.model.ApiConfig
import top.ethan2048.easyllm.core.model.Conversation
import top.ethan2048.easyllm.core.model.McpServerConfig
import top.ethan2048.easyllm.core.model.Model
import top.ethan2048.easyllm.core.model.ModelConfig
import top.ethan2048.easyllm.core.model.Vendor
import top.ethan2048.easyllm.mcp.McpClient

/**
 * 应用全局数据仓库
 *
 * 管理供应商配置、模型配置、MCP 配置列表，并提供对应的客户端实例。
 * 配置数据通过 SharedPreferences 持久化。
 */
class AppRepository(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("easyllm_configs", Context.MODE_PRIVATE)

    /** 供应商配置列表 */
    private val _vendors = mutableListOf<Vendor>()
    val vendors: List<Vendor> get() = _vendors.toList()

    /** MCP 配置列表 */
    private val _mcpConfigs = mutableListOf<McpServerConfig>()
    val mcpConfigs: List<McpServerConfig> get() = _mcpConfigs.toList()

    /** 对话列表 */
    private val _conversations = mutableListOf<Conversation>()
    val conversations: List<Conversation> get() = _conversations.toList()

    /** 当前选中的对话 ID */
    var activeConversationId: String? = null
        private set

    /** 当前选中的供应商 ID */
    var activeVendorId: String? = null
        private set

    /** 当前选中的模型配置 ID */
    var activeModelConfigId: String? = null
        private set

    /** 客户端缓存 */
    private val chatApiCache = mutableMapOf<String, IChatApi>()
    private var mcpClient: IMcpClient? = null

    init {
        loadConfigs()
    }

    // ============ 供应商管理 ============

    fun addVendor(vendor: Vendor) {
        _vendors.add(vendor)
        saveVendors()
    }

    fun updateVendor(vendor: Vendor) {
        val index = _vendors.indexOfFirst { it.id == vendor.id }
        if (index >= 0) {
            _vendors[index] = vendor
            chatApiCache.remove(vendor.id)
            saveVendors()
        }
    }

    fun deleteVendor(vendorId: String) {
        _vendors.removeAll { it.id == vendorId }
        chatApiCache.remove(vendorId)
        if (activeVendorId == vendorId) {
            activeVendorId = _vendors.firstOrNull()?.id
            activeModelConfigId = null
        }
        saveVendors()
    }

    fun setActiveVendor(vendorId: String) {
        activeVendorId = vendorId
        prefs.edit().putString("active_vendor_id", vendorId).apply()
    }

    fun getVendor(vendorId: String): Vendor? {
        return _vendors.find { it.id == vendorId }
    }

    // ============ 模型配置管理 ============

    fun addModelConfig(vendorId: String, modelConfig: ModelConfig) {
        val index = _vendors.indexOfFirst { it.id == vendorId }
        if (index >= 0) {
            val vendor = _vendors[index]
            val updatedModels = vendor.models + modelConfig
            _vendors[index] = vendor.copy(models = updatedModels)
            saveVendors()
        }
    }

    fun updateModelConfig(vendorId: String, modelConfig: ModelConfig) {
        val vendorIndex = _vendors.indexOfFirst { it.id == vendorId }
        if (vendorIndex >= 0) {
            val vendor = _vendors[vendorIndex]
            val modelIndex = vendor.models.indexOfFirst { it.id == modelConfig.id }
            if (modelIndex >= 0) {
                val updatedModels = vendor.models.toMutableList()
                updatedModels[modelIndex] = modelConfig
                _vendors[vendorIndex] = vendor.copy(models = updatedModels)
                saveVendors()
            }
        }
    }

    fun deleteModelConfig(vendorId: String, modelConfigId: String) {
        val vendorIndex = _vendors.indexOfFirst { it.id == vendorId }
        if (vendorIndex >= 0) {
            val vendor = _vendors[vendorIndex]
            val updatedModels = vendor.models.filter { it.id != modelConfigId }
            _vendors[vendorIndex] = vendor.copy(models = updatedModels)
            if (activeModelConfigId == modelConfigId) {
                activeModelConfigId = null
            }
            saveVendors()
        }
    }

    fun setActiveModelConfig(modelConfigId: String) {
        activeModelConfigId = modelConfigId
        prefs.edit().putString("active_model_config_id", modelConfigId).apply()
    }

    // ============ API 客户端 ============

    fun getChatApi(vendorId: String): IChatApi {
        return chatApiCache.getOrPut(vendorId) {
            val vendor = _vendors.find { it.id == vendorId }
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

    suspend fun getModelsFromVendor(vendorId: String): Result<List<Model>> = withContext(Dispatchers.IO) {
        runCatching {
            val vendor = _vendors.find { it.id == vendorId }
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

    suspend fun getModelsFromEndpoint(endpoint: String, apiKey: String): Result<List<Model>> = withContext(Dispatchers.IO) {
        runCatching {
            val tempConfig = ApiConfig(
                id = "temp",
                name = "temp",
                endpoint = endpoint.trimEnd('/'),
                apiKey = apiKey,
                model = ""
            )
            val api = OpenAiClient(tempConfig)
            val result = api.getModels()
            result.getOrThrow()
        }
    }

    fun getActiveChatApi(): IChatApi? {
        val vendorId = activeVendorId ?: return null
        val modelConfigId = activeModelConfigId ?: return null
        
        val vendor = _vendors.find { it.id == vendorId } ?: return null
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

    fun addMcpConfig(config: McpServerConfig) {
        _mcpConfigs.add(config)
        saveMcpConfigs()
    }

    fun updateMcpConfig(config: McpServerConfig) {
        val index = _mcpConfigs.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            _mcpConfigs[index] = config
            saveMcpConfigs()
        }
    }

    fun deleteMcpConfig(configId: String) {
        _mcpConfigs.removeAll { it.id == configId }
        saveMcpConfigs()
    }

    fun getMcpClient(): IMcpClient {
        if (mcpClient == null) {
            mcpClient = McpClient()
        }
        return mcpClient!!
    }

    // ============ 对话管理 ============

    fun addConversation(conversation: Conversation) {
        _conversations.add(0, conversation)
        saveConversations()
    }

    fun updateConversation(conversation: Conversation) {
        val index = _conversations.indexOfFirst { it.id == conversation.id }
        if (index >= 0) {
            _conversations[index] = conversation.copy(updatedAt = System.currentTimeMillis())
            saveConversations()
        }
    }

    fun deleteConversation(conversationId: String) {
        _conversations.removeAll { it.id == conversationId }
        if (activeConversationId == conversationId) {
            activeConversationId = _conversations.firstOrNull()?.id
        }
        saveConversations()
    }

    fun setActiveConversation(conversationId: String) {
        activeConversationId = conversationId
        prefs.edit().putString("active_conversation_id", conversationId).apply()
    }

    fun getConversation(conversationId: String): Conversation? {
        return _conversations.find { it.id == conversationId }
    }

    fun getActiveConversation(): Conversation? {
        val id = activeConversationId ?: return null
        return _conversations.find { it.id == id }
    }

    // ============ 持久化 ============

    private fun loadConfigs() {
        try {
            val vendorJson = prefs.getString("vendors", "[]") ?: "[]"
            _vendors.clear()
            _vendors.addAll(json.decodeFromString<List<Vendor>>(vendorJson))

            val mcpJson = prefs.getString("mcp_configs", "[]") ?: "[]"
            _mcpConfigs.clear()
            _mcpConfigs.addAll(json.decodeFromString<List<McpServerConfig>>(mcpJson))

            val conversationJson = prefs.getString("conversations", "[]") ?: "[]"
            _conversations.clear()
            _conversations.addAll(json.decodeFromString<List<Conversation>>(conversationJson))

            activeVendorId = prefs.getString("active_vendor_id", _vendors.firstOrNull()?.id)
            activeModelConfigId = prefs.getString("active_model_config_id", null)
            activeConversationId = prefs.getString("active_conversation_id", _conversations.firstOrNull()?.id)
        } catch (_: Exception) {
            // 解析失败时使用空列表
        }
    }

    private fun saveVendors() {
        prefs.edit().putString("vendors", json.encodeToString(_vendors)).apply()
    }

    private fun saveMcpConfigs() {
        prefs.edit().putString("mcp_configs", json.encodeToString(_mcpConfigs)).apply()
    }

    private fun saveConversations() {
        prefs.edit().putString("conversations", json.encodeToString(_conversations)).apply()
    }
}
