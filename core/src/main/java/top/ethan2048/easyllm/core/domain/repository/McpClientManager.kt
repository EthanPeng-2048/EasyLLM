package top.ethan2048.easyllm.core.domain.repository

import kotlinx.coroutines.flow.Flow
import top.ethan2048.easyllm.core.domain.api.IMcpClient
import top.ethan2048.easyllm.core.domain.model.*

/**
 * MCP 客户端管理器接口
 */
interface McpClientManager {
    val events: Flow<top.ethan2048.easyllm.core.domain.model.mcp.McpEvent>

    fun connect(config: McpServerConfig): Result<Unit>
    fun disconnect(serverId: String)
    fun isConnected(serverId: String): Boolean
    fun getServerInfo(serverId: String): McpServerInfo?

    suspend fun listTools(serverId: String): Result<List<top.ethan2048.easyllm.core.domain.model.mcp.McpTool>>
    suspend fun callTool(
        serverId: String,
        toolName: String,
        arguments: Map<String, Any>
    ): Result<top.ethan2048.easyllm.core.domain.model.mcp.top.ethan2048.easyllm.core.domain.model.mcp.McpToolResult>

    suspend fun listResources(serverId: String): Result<List<top.ethan2048.easyllm.core.domain.model.mcp.McpResource>>
    suspend fun readResource(serverId: String, uri: String): Result<List<top.ethan2048.easyllm.core.domain.model.mcp.top.ethan2048.easyllm.core.domain.model.mcp.McpResourceContent>>
    suspend fun listResourceTemplates(serverId: String): Result<List<top.ethan2048.easyllm.core.domain.model.mcp.top.ethan2048.easyllm.core.domain.model.mcp.McpResourceTemplate>>

    suspend fun listPrompts(serverId: String): Result<List<top.ethan2048.easyllm.core.domain.model.mcp.McpPrompt>>
    suspend fun getPrompt(
        serverId: String,
        name: String,
        arguments: Map<String, String>? = null
    ): Result<top.ethan2048.easyllm.core.domain.model.mcp.top.ethan2048.easyllm.core.domain.model.mcp.McpPromptResult>
}
