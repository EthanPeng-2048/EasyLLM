package top.ethan2048.easyllm.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.ethan2048.easyllm.api.OpenAiClient
import top.ethan2048.easyllm.core.`interface`.IChatApi
import top.ethan2048.easyllm.core.`interface`.IMcpClient
import top.ethan2048.easyllm.core.model.ApiConfig
import top.ethan2048.easyllm.core.model.McpServerConfig
import top.ethan2048.easyllm.mcp.McpClient

/**
 * 应用全局数据仓库
 *
 * 管理 API 配置、MCP 配置列表，并提供对应的客户端实例。
 * 配置数据通过 SharedPreferences 持久化。
 */
class AppRepository(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("easyllm_configs", Context.MODE_PRIVATE)

    /** API 配置列表 */
    private val _apiConfigs = mutableListOf<ApiConfig>()
    val apiConfigs: List<ApiConfig> get() = _apiConfigs.toList()

    /** MCP 配置列表 */
    private val _mcpConfigs = mutableListOf<McpServerConfig>()
    val mcpConfigs: List<McpServerConfig> get() = _mcpConfigs.toList()

    /** 当前选中的 API 配置 ID */
    var activeApiConfigId: String? = null
        private set

    /** 客户端缓存 */
    private val chatApiCache = mutableMapOf<String, IChatApi>()
    private var mcpClient: IMcpClient? = null

    init {
        loadConfigs()
    }

    // ============ API 配置管理 ============

    fun addApiConfig(config: ApiConfig) {
        _apiConfigs.add(config)
        saveApiConfigs()
    }

    fun updateApiConfig(config: ApiConfig) {
        val index = _apiConfigs.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            _apiConfigs[index] = config
            // 清除缓存的客户端，下次使用时重建
            chatApiCache.remove(config.id)
            saveApiConfigs()
        }
    }

    fun deleteApiConfig(configId: String) {
        _apiConfigs.removeAll { it.id == configId }
        chatApiCache.remove(configId)
        if (activeApiConfigId == configId) {
            activeApiConfigId = _apiConfigs.firstOrNull()?.id
        }
        saveApiConfigs()
    }

    fun setActiveApiConfig(configId: String) {
        activeApiConfigId = configId
        prefs.edit().putString("active_api_id", configId).apply()
    }

    fun getChatApi(configId: String): IChatApi {
        return chatApiCache.getOrPut(configId) {
            val config = _apiConfigs.find { it.id == configId }
                ?: throw IllegalStateException("API config not found: $configId")
            OpenAiClient(config)
        }
    }

    fun getActiveChatApi(): IChatApi? {
        val id = activeApiConfigId ?: return null
        return try {
            getChatApi(id)
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

    // ============ 持久化 ============

    private fun loadConfigs() {
        try {
            val apiJson = prefs.getString("api_configs", "[]") ?: "[]"
            _apiConfigs.clear()
            _apiConfigs.addAll(json.decodeFromString<List<ApiConfig>>(apiJson))

            val mcpJson = prefs.getString("mcp_configs", "[]") ?: "[]"
            _mcpConfigs.clear()
            _mcpConfigs.addAll(json.decodeFromString<List<McpServerConfig>>(mcpJson))

            activeApiConfigId = prefs.getString("active_api_id", _apiConfigs.firstOrNull()?.id)
        } catch (_: Exception) {
            // 解析失败时使用空列表
        }
    }

    private fun saveApiConfigs() {
        prefs.edit().putString("api_configs", json.encodeToString(_apiConfigs)).apply()
    }

    private fun saveMcpConfigs() {
        prefs.edit().putString("mcp_configs", json.encodeToString(_mcpConfigs)).apply()
    }
}
