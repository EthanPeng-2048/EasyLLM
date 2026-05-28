package top.ethan2048.easyllm.mcp.transport

import kotlinx.coroutines.flow.Flow
import top.ethan2048.easyllm.mcp.protocol.JsonRpcRequest
import top.ethan2048.easyllm.mcp.protocol.JsonRpcResponse
import top.ethan2048.easyllm.mcp.protocol.SseEvent

/**
 * MCP 传输层统一接口
 *
 * Streamable HTTP 和 SSE 两种传输方式都实现此接口。
 */
interface McpTransport {
    /** MCP 协议版本 */
    val protocolVersion: String

    /** 当前会话 ID（如果有） */
    var sessionId: String?

    /** 发送 JSON-RPC 请求并等待响应 */
    suspend fun sendRequest(request: JsonRpcRequest): Result<JsonRpcResponse>

    /** 发送 JSON-RPC 通知（无响应期望） */
    suspend fun sendNotification(request: JsonRpcRequest): Result<Unit>

    /** 打开 SSE 事件流，用于接收服务端推送 */
    fun openSseStream(): Flow<SseEvent>

    /** 关闭会话 */
    suspend fun closeSession(): Result<Unit>
}
