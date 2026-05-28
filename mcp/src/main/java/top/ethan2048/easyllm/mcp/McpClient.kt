package top.ethan2048.easyllm.mcp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import top.ethan2048.easyllm.core.`interface`.IMcpClient
import top.ethan2048.easyllm.core.model.McpEvent
import top.ethan2048.easyllm.core.model.McpPrompt
import top.ethan2048.easyllm.core.model.McpPromptResult
import top.ethan2048.easyllm.core.model.McpResource
import top.ethan2048.easyllm.core.model.McpResourceContent
import top.ethan2048.easyllm.core.model.McpResourceTemplate
import top.ethan2048.easyllm.core.model.McpServerConfig
import top.ethan2048.easyllm.core.model.McpServerInfo
import top.ethan2048.easyllm.core.model.McpTool
import top.ethan2048.easyllm.core.model.McpToolResult
import top.ethan2048.easyllm.mcp.protocol.McpConnectionState
import top.ethan2048.easyllm.mcp.protocol.McpLifecycle
import top.ethan2048.easyllm.mcp.protocol.PromptsProtocol
import top.ethan2048.easyllm.mcp.protocol.ResourcesProtocol
import top.ethan2048.easyllm.mcp.protocol.SseEvent
import top.ethan2048.easyllm.mcp.protocol.ToolsProtocol
import top.ethan2048.easyllm.core.model.McpTransportType
import top.ethan2048.easyllm.mcp.transport.McpTransport
import top.ethan2048.easyllm.mcp.transport.SseTransport
import top.ethan2048.easyllm.mcp.transport.StreamableHttpTransport

/**
 * MCP 客户端统一入口
 *
 * 将传输层、生命周期、工具、资源、提示模板等协议整合在一起，
 * 对外提供统一的 IMcpClient 接口。
 *
 * 支持同时管理多个 MCP 服务器连接，每个连接由 serverId 标识。
 */
class McpClient(
    private val httpClient: OkHttpClient = OkHttpClient()
) : IMcpClient {

    private val json = Json { ignoreUnknownKeys = true }

    /** 每个服务器连接的内部状态 */
    private val sessions = mutableMapOf<String, McpSession>()

    /** 全局事件总线，聚合所有服务器的事件 */
    private val _events = MutableSharedFlow<McpEvent>(replay = 0, extraBufferCapacity = 64)
    override val events: Flow<McpEvent> = _events.asSharedFlow()

    override suspend fun connect(config: McpServerConfig): Result<McpServerInfo> = runCatching {
        // 如果已存在连接，先断开
        disconnect(config.id)

        val transport: McpTransport = when (config.transportType) {
            McpTransportType.STREAMABLE_HTTP -> StreamableHttpTransport(
                endpoint = config.endpoint,
                customHeaders = config.headers,
                httpClient = httpClient
            )
            McpTransportType.SSE -> {
                val sseTransport = SseTransport(
                    endpoint = config.endpoint,
                    ssePath = config.ssePath,
                    messagesPath = config.messagesPath,
                    customHeaders = config.headers,
                    httpClient = httpClient
                )
                // SSE 模式下需要先建立 SSE 连接获取 sessionId
                sseTransport.connect().getOrThrow()
                sseTransport
            }
        }

        val lifecycle = McpLifecycle(transport)
        val toolsProtocol = ToolsProtocol(transport)
        val resourcesProtocol = ResourcesProtocol(transport)
        val promptsProtocol = PromptsProtocol(transport)

        // 执行初始化握手
        val serverInfo = lifecycle.initialize().getOrThrow()

        val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val session = McpSession(
            config = config,
            transport = transport,
            lifecycle = lifecycle,
            toolsProtocol = toolsProtocol,
            resourcesProtocol = resourcesProtocol,
            promptsProtocol = promptsProtocol,
            scope = sessionScope
        )

        sessions[config.id] = session

        // 启动 SSE 通知监听
        startNotificationListener(config.id, session)

        serverInfo
    }

    override suspend fun disconnect(serverId: String) {
        sessions.remove(serverId)?.let { session ->
            session.lifecycle.shutdown()
            session.scope.cancel()
        }
    }

    override fun isConnected(serverId: String): Boolean {
        return sessions[serverId]?.lifecycle?.state == McpConnectionState.OPERATION
    }

    override fun getServerInfo(serverId: String): McpServerInfo? {
        return sessions[serverId]?.lifecycle?.serverInfo
    }

    // ---- Tools ----

    override suspend fun listTools(serverId: String): Result<List<McpTool>> {
        val session = getSession(serverId)
        return session.toolsProtocol.list()
    }

    override suspend fun callTool(
        serverId: String,
        toolName: String,
        arguments: Map<String, Any>
    ): Result<McpToolResult> {
        val session = getSession(serverId)
        return session.toolsProtocol.call(toolName, arguments)
    }

    // ---- Resources ----

    override suspend fun listResources(serverId: String): Result<List<McpResource>> {
        val session = getSession(serverId)
        return session.resourcesProtocol.list()
    }

    override suspend fun readResource(serverId: String, uri: String): Result<List<McpResourceContent>> {
        val session = getSession(serverId)
        return session.resourcesProtocol.read(uri)
    }

    override suspend fun listResourceTemplates(serverId: String): Result<List<McpResourceTemplate>> {
        val session = getSession(serverId)
        return session.resourcesProtocol.listTemplates()
    }

    // ---- Prompts ----

    override suspend fun listPrompts(serverId: String): Result<List<McpPrompt>> {
        val session = getSession(serverId)
        return session.promptsProtocol.list()
    }

    override suspend fun getPrompt(
        serverId: String,
        name: String,
        arguments: Map<String, String>?
    ): Result<McpPromptResult> {
        val session = getSession(serverId)
        return session.promptsProtocol.get(name, arguments)
    }

    // ============ Private ============

    private fun getSession(serverId: String): McpSession {
        return sessions[serverId]
            ?: throw IllegalStateException("MCP server not connected: $serverId")
    }

    /**
     * 启动 SSE 通知监听，将服务端推送转换为 McpEvent
     */
    private fun startNotificationListener(serverId: String, session: McpSession) {
        session.scope.launch {
            session.transport.openSseStream()
                .catch { e ->
                    _events.emit(McpEvent.ServerDisconnected(serverId))
                }
                .onCompletion {
                    _events.emit(McpEvent.ServerDisconnected(serverId))
                }
                .collect { sseEvent ->
                    val event = when (sseEvent) {
                        is SseEvent.Notification -> parseNotification(serverId, sseEvent.notification)
                        is SseEvent.Response -> null // 忽略常规响应
                    }
                    if (event != null) {
                        _events.emit(event)
                    }
                }
        }
    }

    /**
     * 解析服务端 JSON-RPC 通知为 McpEvent
     */
    private fun parseNotification(
        serverId: String,
        notification: top.ethan2048.easyllm.mcp.protocol.JsonRpcNotification
    ): McpEvent? {
        return when (notification.method) {
            "notifications/tools/list_changed" ->
                McpEvent.ToolsListChanged(serverId)
            "notifications/resources/list_changed" ->
                McpEvent.ResourcesListChanged(serverId)
            "notifications/resources/updated" -> {
                val uri = notification.params?.get("uri")?.jsonPrimitive?.content
                if (uri != null) McpEvent.ResourceUpdated(serverId, uri) else null
            }
            "notifications/logging/message" -> {
                val level = notification.params?.get("level")?.jsonPrimitive?.content ?: "info"
                val message = notification.params?.get("message")?.jsonPrimitive?.content ?: ""
                McpEvent.LogMessage(serverId, level, message)
            }
            else -> null // 忽略未知通知
        }
    }
}

/**
 * 单个 MCP 服务器连接的内部状态
 */
private class McpSession(
    val config: McpServerConfig,
    val transport: McpTransport,
    val lifecycle: McpLifecycle,
    val toolsProtocol: ToolsProtocol,
    val resourcesProtocol: ResourcesProtocol,
    val promptsProtocol: PromptsProtocol,
    val scope: CoroutineScope
)
