package top.ethan2048.easyllm.mcp

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.SseTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpTransport
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
import okhttp3.OkHttpClient
import top.ethan2048.easyllm.core.domain.api.IMcpClient
import top.ethan2048.easyllm.core.domain.model.mcp.McpEvent
import top.ethan2048.easyllm.core.domain.model.mcp.McpPrompt
import top.ethan2048.easyllm.core.domain.model.mcp.McpPromptResult
import top.ethan2048.easyllm.core.domain.model.mcp.McpResource
import top.ethan2048.easyllm.core.domain.model.mcp.McpResourceContent
import top.ethan2048.easyllm.core.domain.model.mcp.McpResourceTemplate
import top.ethan2048.easyllm.core.domain.model.mcp.McpServerConfig
import top.ethan2048.easyllm.core.domain.model.mcp.McpServerInfo
import top.ethan2048.easyllm.core.domain.model.mcp.McpTool
import top.ethan2048.easyllm.core.domain.model.mcp.McpToolResult
import top.ethan2048.easyllm.core.domain.model.mcp.McpTransportType

/**
 * 基于 modelcontextprotocol/kotlin-sdk 官方库的 MCP 客户端
 */
class McpClient(
    private val httpClient: OkHttpClient = OkHttpClient()
) : IMcpClient {

    /** 每个服务器连接的内部 Client */
    private val clients = mutableMapOf<String, McpSession>()

    /** 全局事件总线，聚合所有服务器的事件 */
    private val _events = MutableSharedFlow<McpEvent>(replay = 0, extraBufferCapacity = 64)
    override val events: Flow<McpEvent> = _events.asSharedFlow()

    override suspend fun connect(config: McpServerConfig): Result<McpServerInfo> = runCatching {
        // 如果已存在连接，先断开
        disconnect(config.id)

        val transport = when (config.transportType) {
            McpTransportType.STREAMABLE_HTTP -> {
                StreamableHttpTransport(
                    url = config.endpoint,
                    headers = config.headers.mapValues { it.value },
                    client = httpClient
                )
            }
            McpTransportType.SSE -> {
                val sseUrl = if (config.ssePath.startsWith("/")) {
                    "${config.endpoint.trimEnd('/')}${config.ssePath}"
                } else {
                    "${config.endpoint.trimEnd('/')}/${config.ssePath}"
                }
                SseTransport(
                    url = sseUrl,
                    headers = config.headers.mapValues { it.value },
                    client = httpClient
                )
            }
        }

        val client = Client(
            implementation = io.modelcontextprotocol.kotlin.sdk.Implementation(
                name = "EasyLLM",
                version = "1.0.0"
            ),
            options = null
        )

        val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val session = McpSession(
            config = config,
            client = client,
            transport = transport,
            scope = sessionScope
        )

        // 连接并初始化
        client.connect(transport).getOrThrow()

        clients[config.id] = session

        // 获取服务器信息
        val serverInfo = McpServerInfo(
            name = client.serverCapabilities?.let { "MCP Server" } ?: "Unknown",
            version = "1.0.0"
        )

        // 启动事件监听
        startNotificationListener(config.id, session)

        serverInfo
    }

    override suspend fun disconnect(serverId: String) {
        clients.remove(serverId)?.let { session ->
            try {
                session.client.close()
            } catch (e: Exception) {
                // Ignore close errors
            }
            session.scope.cancel()
        }
    }

    override fun isConnected(serverId: String): Boolean {
        return clients[serverId]?.client?.isClosed != false
    }

    override fun getServerInfo(serverId: String): McpServerInfo? {
        return clients[serverId]?.config?.let { config ->
            McpServerInfo(
                name = config.name,
                version = "1.0.0"
            )
        }
    }

    // ---- Tools ----

    override suspend fun listTools(serverId: String): Result<List<McpTool>> {
        val session = getSession(serverId)
        return runCatching {
            val toolsResponse = session.client.listTools()
            toolsResponse.tools.map { tool ->
                McpTool(
                    name = tool.name,
                    description = tool.description ?: "",
                    inputSchema = tool.inputSchema
                )
            }
        }
    }

    override suspend fun callTool(
        serverId: String,
        toolName: String,
        arguments: Map<String, Any>
    ): Result<McpToolResult> {
        val session = getSession(serverId)
        return runCatching {
            val result = session.client.callTool(
                io.modelcontextprotocol.kotlin.sdk.ToolCall(
                    id = null,
                    method = null,
                    params = io.modelcontextprotocol.kotlin.sdk.ToolCallParams(
                        name = toolName,
                        arguments = arguments
                    )
                )
            )
            McpToolResult(
                content = result.content.map { content ->
                    when (content) {
                        is io.modelcontextprotocol.kotlin.sdk.TextContent -> 
                            top.ethan2048.easyllm.core.domain.model.mcp.McpTextContent(text = content.text)
                        is io.modelcontextprotocol.kotlin.sdk.ImageContent ->
                            top.ethan2048.easyllm.core.domain.model.mcp.McpImageContent(
                                data = content.data,
                                mimeType = content.mimeType
                            )
                        is io.modelcontextprotocol.kotlin.sdk.ResourceContent ->
                            top.ethan2048.easyllm.core.domain.model.mcp.McpResourceContent(
                                uri = content.resource.uri,
                                text = content.resource.text,
                                blob = content.resource.blob,
                                mimeType = content.resource.mimeType
                            )
                        else -> top.ethan2048.easyllm.core.domain.model.mcp.McpTextContent(text = content.toString())
                    }
                },
                isError = result.isError
            )
        }
    }

    // ---- Resources ----

    override suspend fun listResources(serverId: String): Result<List<McpResource>> {
        val session = getSession(serverId)
        return runCatching {
            val resourcesResponse = session.client.listResources()
            resourcesResponse.resources.map { resource ->
                McpResource(
                    uri = resource.uri,
                    name = resource.name,
                    description = resource.description,
                    mimeType = resource.mimeType
                )
            }
        }
    }

    override suspend fun readResource(serverId: String, uri: String): Result<List<McpResourceContent>> {
        val session = getSession(serverId)
        return runCatching {
            val result = session.client.readResource(
                io.modelcontextprotocol.kotlin.sdk.ReadResourceRequestParams(
                    uri = uri
                )
            )
            result.contents.map { content ->
                when (content) {
                    is io.modelcontextprotocol.kotlin.sdk.TextResourceContents ->
                        McpResourceContent(
                            uri = content.uri,
                            text = content.text,
                            mimeType = content.mimeType
                        )
                    is io.modelcontextprotocol.kotlin.sdk.BlobResourceContents ->
                        McpResourceContent(
                            uri = content.uri,
                            blob = content.blob,
                            mimeType = content.mimeType
                        )
                    else -> McpResourceContent(uri = content.toString(), text = null)
                }
            }
        }
    }

    override suspend fun listResourceTemplates(serverId: String): Result<List<McpResourceTemplate>> {
        val session = getSession(serverId)
        return runCatching {
            val response = session.client.listResourceTemplates()
            response.resourceTemplates.map { template ->
                McpResourceTemplate(
                    uriTemplate = template.uriTemplate,
                    name = template.name,
                    description = template.description,
                    mimeType = template.mimeType
                )
            }
        }
    }

    // ---- Prompts ----

    override suspend fun listPrompts(serverId: String): Result<List<McpPrompt>> {
        val session = getSession(serverId)
        return runCatching {
            val response = session.client.listPrompts()
            response.prompts.map { prompt ->
                McpPrompt(
                    name = prompt.name,
                    description = prompt.description,
                    arguments = prompt.arguments?.map { arg ->
                        arg.name to top.ethan2048.easyllm.core.domain.model.mcp.McpPromptArgument(
                            name = arg.name,
                            description = arg.description,
                            required = arg.required
                        )
                    }?.toMap()
                )
            }
        }
    }

    override suspend fun getPrompt(
        serverId: String,
        name: String,
        arguments: Map<String, String>?
    ): Result<McpPromptResult> {
        val session = getSession(serverId)
        return runCatching {
            val result = session.client.getPrompt(
                io.modelcontextprotocol.kotlin.sdk.PromptReference(name = name),
                arguments?.mapValues { it.value }
            )
            McpPromptResult(
                description = result.description,
                messages = result.messages.map { msg ->
                    top.ethan2048.easyllm.core.domain.model.mcp.McpPromptMessage(
                        role = msg.role.name.lowercase(),
                        content = when (val content = msg.content) {
                            is io.modelcontextprotocol.kotlin.sdk.TextContent -> 
                                top.ethan2048.easyllm.core.domain.model.mcp.McpTextContent(text = content.text)
                            is io.modelcontextprotocol.kotlin.sdk.ImageContent ->
                                top.ethan2048.easyllm.core.domain.model.mcp.McpImageContent(
                                    data = content.data,
                                    mimeType = content.mimeType
                                )
                            else -> top.ethan2048.easyllm.core.domain.model.mcp.McpTextContent(text = content.toString())
                        }
                    )
                }
            )
        }
    }

    // ============ Private ============

    private fun getSession(serverId: String): McpSession {
        return clients[serverId]
            ?: throw IllegalStateException("MCP server not connected: $serverId")
    }

    /**
     * 启动事件监听
     */
    private fun startNotificationListener(serverId: String, session: McpSession) {
        session.scope.launch {
            // 官方 SDK 通过 Flow 推送通知
            // 这里简化处理，实际需要根据 SDK 提供的通知机制适配
            launch {
                try {
                    session.client.listTools()
                    _events.emit(McpEvent.ToolsListChanged(serverId))
                } catch (e: Exception) {
                    _events.emit(McpEvent.ServerDisconnected(serverId))
                }
            }
        }
    }
}

/**
 * 单个 MCP 服务器连接的内部状态
 */
private class McpSession(
    val config: McpServerConfig,
    val client: Client,
    val transport: io.modelcontextprotocol.kotlin.sdk.client.Transport,
    val scope: CoroutineScope
)
