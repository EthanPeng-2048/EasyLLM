package top.ethan2048.easyllm.core.domain.repository

import top.ethan2048.easyllm.core.domain.model.mcp.McpServerConfig

/**
 * MCP 配置仓库接口
 */
interface McpRepository {
    val mcpConfigs: List<McpServerConfig>

    fun addMcpConfig(config: McpServerConfig)
    fun updateMcpConfig(config: McpServerConfig)
    fun deleteMcpConfig(configId: String)
}
