package top.ethan2048.easyllm.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.ethan2048.easyllm.core.domain.model.mcp.McpServerConfig
import top.ethan2048.easyllm.core.domain.repository.McpRepository

/**
 * MCP 配置仓库实现
 */
class McpRepositoryImpl(context: Context) : McpRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("easyllm_configs", Context.MODE_PRIVATE)

    private val _mcpConfigs = mutableListOf<McpServerConfig>()
    override val mcpConfigs: List<McpServerConfig> get() = _mcpConfigs.toList()

    init {
        loadMcpConfigs()
    }

    override fun addMcpConfig(config: McpServerConfig) {
        _mcpConfigs.add(config)
        saveMcpConfigs()
    }

    override fun updateMcpConfig(config: McpServerConfig) {
        val index = _mcpConfigs.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            _mcpConfigs[index] = config
            saveMcpConfigs()
        }
    }

    override fun deleteMcpConfig(configId: String) {
        _mcpConfigs.removeAll { it.id == configId }
        saveMcpConfigs()
    }

    private fun loadMcpConfigs() {
        try {
            val mcpJson = prefs.getString("mcp_configs", "[]") ?: "[]"
            _mcpConfigs.clear()
            _mcpConfigs.addAll(json.decodeFromString<List<McpServerConfig>>(mcpJson))
        } catch (_: Exception) {
            // 解析失败时使用空列表
        }
    }

    private fun saveMcpConfigs() {
        prefs.edit().putString("mcp_configs", json.encodeToString(_mcpConfigs)).apply()
    }
}
