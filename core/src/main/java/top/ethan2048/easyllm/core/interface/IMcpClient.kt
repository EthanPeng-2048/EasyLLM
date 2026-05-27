package top.ethan2048.easyllm.core.`interface`

import kotlinx.coroutines.flow.Flow
import top.ethan2048.easyllm.core.model.McpEvent
import top.ethan2048.easyllm.core.model.McpPrompt
import top.ethan2048.easyllm.core.model.McpPromptResult
import top.ethan2048.easyllm.core.model.McpResource
import top.ethan2048.easyllm.core.model.McpResourceContent
import top.ethan2048.easyllm.core.model.McpServerConfig
import top.ethan2048.easyllm.core.model.McpServerInfo
import top.ethan2048.easyllm.core.model.McpTool
import top.ethan2048.easyllm.core.model.McpToolResult

/**
 * MCP 模块对外接口
 * 提供 MCP 服务器连接、工具调用、资源读取等能力
 */
interface IMcpClient {
    /** 连接到 MCP 服务器 */
    suspend fun connect(config: McpServerConfig): Result<McpServerInfo>

    /** 断开指定服务器连接 */
    suspend fun disconnect(serverId: String)

    /** 查询服务器是否已连接 */
    fun isConnected(serverId: String): Boolean

    /** 获取服务器信息 */
    fun getServerInfo(serverId: String): McpServerInfo?

    // ---- Tools ----

    /** 列出可用工具 */
    suspend fun listTools(serverId: String): Result<List<McpTool>>

    /** 调用工具 */
    suspend fun callTool(
        serverId: String,
        toolName: String,
        arguments: Map<String, Any>
    ): Result<McpToolResult>

    // ---- Resources ----

    /** 列出可用资源 */
    suspend fun listResources(serverId: String): Result<List<McpResource>>

    /** 读取资源内容 */
    suspend fun readResource(serverId: String, uri: String): Result<List<McpResourceContent>>

    /** 列出资源模板 */
    suspend fun listResourceTemplates(serverId: String): Result<List<top.ethan2048.easyllm.core.model.McpResourceTemplate>>

    // ---- Prompts ----

    /** 列出可用提示模板 */
    suspend fun listPrompts(serverId: String): Result<List<McpPrompt>>

    /** 获取提示模板内容 */
    suspend fun getPrompt(
        serverId: String,
        name: String,
        arguments: Map<String, String>? = null
    ): Result<McpPromptResult>

    // ---- Events ----

    /** MCP 事件流（工具列表变更、资源更新、断开连接等） */
    val events: Flow<McpEvent>
}
