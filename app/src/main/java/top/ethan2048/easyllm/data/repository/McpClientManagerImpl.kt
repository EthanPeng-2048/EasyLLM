package top.ethan2048.easyllm.data.repository

import kotlinx.coroutines.flow.Flow
import top.ethan2048.easyllm.core.domain.model.*
import top.ethan2048.easyllm.core.domain.repository.McpClientManager
import top.ethan2048.easyllm.mcp.McpClient

/**
 * MCP 客户端管理器实现
 * 基于 McpClient 封装，提供应用级的 MCP 管理能力
 */
class McpClientManagerImpl(
    private val mcpClient: McpClient = McpClient()
) : McpClientManager {

    override val events: Flow<top.ethan2048.easyllm.core.domain.model.mcp.McpEvent> = mcpClient.events

    override fun connect(config: McpServerConfig): Result<Unit> {
        // McpClient.connect 是 suspend 函数，这里需要在外部调用
        // 为了保持接口简洁，将连接操作交给 ViewModel 或 UseCase 层处理
        return runCatching {
            // 注意：实际使用时需要在协程作用域中调用
            throw IllegalStateException("Connect must be called via connectAsync in coroutine scope")
        }
    }

    /**
     * 异步连接方法，供内部使用
     */
    suspend fun connectAsync(config: McpServerConfig): Result<McpServerInfo> {
        return mcpClient.connect(config)
    }

    override fun disconnect(serverId: String) {
        mcpClient.disconnect(serverId)
    }

    override fun isConnected(serverId: String): Boolean {
        return mcpClient.isConnected(serverId)
    }

    override fun getServerInfo(serverId: String): McpServerInfo? {
        return mcpClient.getServerInfo(serverId)
    }

    override suspend fun listTools(serverId: String): Result<List<top.ethan2048.easyllm.core.domain.model.mcp.McpTool>> {
        return mcpClient.listTools(serverId)
    }

    override suspend fun callTool(
        serverId: String,
        toolName: String,
        arguments: Map<String, Any>
    ): Result<top.ethan2048.easyllm.core.domain.model.mcp.top.ethan2048.easyllm.core.domain.model.mcp.McpToolResult> {
        return mcpClient.callTool(serverId, toolName, arguments)
    }

    override suspend fun listResources(serverId: String): Result<List<top.ethan2048.easyllm.core.domain.model.mcp.McpResource>> {
        return mcpClient.listResources(serverId)
    }

    override suspend fun readResource(serverId: String, uri: String): Result<List<top.ethan2048.easyllm.core.domain.model.mcp.top.ethan2048.easyllm.core.domain.model.mcp.McpResourceContent>> {
        return mcpClient.readResource(serverId, uri)
    }

    override suspend fun listResourceTemplates(serverId: String): Result<List<top.ethan2048.easyllm.core.domain.model.mcp.top.ethan2048.easyllm.core.domain.model.mcp.McpResourceTemplate>> {
        return mcpClient.listResourceTemplates(serverId)
    }

    override suspend fun listPrompts(serverId: String): Result<List<top.ethan2048.easyllm.core.domain.model.mcp.McpPrompt>> {
        return mcpClient.listPrompts(serverId)
    }

    override suspend fun getPrompt(
        serverId: String,
        name: String,
        arguments: Map<String, String>?
    ): Result<top.ethan2048.easyllm.core.domain.model.mcp.top.ethan2048.easyllm.core.domain.model.mcp.McpPromptResult> {
        return mcpClient.getPrompt(serverId, name, arguments)
    }
}
