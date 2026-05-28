package top.ethan2048.easyllm.mcp.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.ethan2048.easyllm.core.domain.model.McpServerCapabilities
import top.ethan2048.easyllm.core.domain.model.mcp.McpServerInfo
import top.ethan2048.easyllm.mcp.transport.McpTransport
import top.ethan2048.easyllm.mcp.transport.TransportException

/**
 * MCP 连接生命周期管理
 *
 * 状态转换:
 * UNINITIALIZED → (initialize) → INITIALIZING → (initialized) → OPERATION → (close) → SHUTDOWN
 */
enum class McpConnectionState {
    UNINITIALIZED,
    INITIALIZING,
    OPERATION,
    SHUTDOWN
}

class McpLifecycle(private val transport: McpTransport) {

    var state: McpConnectionState = McpConnectionState.UNINITIALIZED
        private set

    var serverInfo: McpServerInfo? = null
        private set

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 执行 MCP 初始化握手
     */
    suspend fun initialize(): Result<McpServerInfo> = runCatching {
        if (state != McpConnectionState.UNINITIALIZED) {
            throw TransportException("Cannot initialize from state $state")
        }

        state = McpConnectionState.INITIALIZING

        // Step 1: 发送 initialize 请求
        val initParams = buildJsonObject {
            put("protocolVersion", transport.protocolVersion)
            put("capabilities", buildJsonObject {
                // 客户端能力声明
                put("roots", buildJsonObject { put("listChanged", true) })
                put("sampling", buildJsonObject {})
                put("elicitation", buildJsonObject {})
            })
            put("clientInfo", buildJsonObject {
                put("name", "EasyLLM")
                put("title", "EasyLLM MCP Client")
                put("version", "1.0.0")
            })
        }

        val initRequest = JsonRpcRequest(
            id = nextRequestId(),
            method = "initialize",
            params = initParams
        )

        val initResponse = transport.sendRequest(initRequest).getOrThrow()

        if (initResponse.error != null) {
            state = McpConnectionState.UNINITIALIZED
            throw TransportException("Initialize failed: ${initResponse.error.message}")
        }

        val result = initResponse.result
            ?: throw TransportException("Initialize response missing result")

        // 解析服务端信息
        val protocolVersion = result["protocolVersion"]?.toString()?.trim('"')
            ?: throw TransportException("Missing protocolVersion")

        val serverInfoJson = result["serverInfo"]
            ?: throw TransportException("Missing serverInfo")

        val serverInfoData = json.decodeFromJsonElement(ServerInfoData.serializer(), serverInfoJson)

        val capabilities = result["capabilities"]?.let {
            json.decodeFromJsonElement(McpServerCapabilities.serializer(), it)
        } ?: McpServerCapabilities()

        val info = McpServerInfo(
            name = serverInfoData.name,
            title = serverInfoData.title,
            version = serverInfoData.version,
            protocolVersion = protocolVersion,
            capabilities = capabilities,
            instructions = result["instructions"]?.toString()?.trim('"')
        )

        this.serverInfo = info

        // Step 2: 发送 initialized 通知
        val initializedNotification = JsonRpcRequest(
            method = "notifications/initialized"
        )
        transport.sendNotification(initializedNotification).getOrThrow()

        state = McpConnectionState.OPERATION
        info
    }

    /**
     * 关闭连接
     */
    suspend fun shutdown(): Result<Unit> = runCatching {
        if (state == McpConnectionState.OPERATION) {
            transport.closeSession().getOrNull()
        }
        state = McpConnectionState.SHUTDOWN
    }

    @kotlinx.serialization.Serializable
    data class ServerInfoData(
        val name: String,
        val title: String? = null,
        val version: String
    )
}
