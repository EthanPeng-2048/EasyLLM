package top.ethan2048.easyllm.mcp.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import top.ethan2048.easyllm.mcp.protocol.JsonRpcNotification
import top.ethan2048.easyllm.mcp.protocol.JsonRpcRequest
import top.ethan2048.easyllm.mcp.protocol.JsonRpcResponse
import top.ethan2048.easyllm.mcp.protocol.SseEvent

/**
 * Streamable HTTP 传输层
 * 实现 MCP 2025-06-18 规范的 Streamable HTTP 传输
 *
 * - 所有消息通过 HTTP POST 发送
 * - 服务端可通过 SSE 流返回多个消息
 * - 支持 Mcp-Session-Id 和 MCP-Protocol-Version 头
 */
class StreamableHttpTransport(
    private val endpoint: String,
    private val customHeaders: Map<String, String> = emptyMap(),
    private val httpClient: OkHttpClient = OkHttpClient()
) : McpTransport {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val HEADER_SESSION_ID = "MCP-Session-Id"
        private const val HEADER_PROTOCOL_VERSION = "MCP-Protocol-Version"
    }

    override val protocolVersion: String = "2025-06-18"

    override var sessionId: String? = null

    /**
     * 发送 JSON-RPC 请求，返回单个响应
     * 用于需要精确匹配请求-响应的调用（如 initialize, tools/list）
     */
    override suspend fun sendRequest(request: JsonRpcRequest): Result<JsonRpcResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val requestJson = json.encodeToString(JsonRpcRequest.serializer(), request)
            val httpRequest = buildPostRequest(requestJson)

            val httpResponse = httpClient.newCall(httpRequest).execute()

            // 提取 session ID
            extractSessionId(httpResponse)

            if (!httpResponse.isSuccessful) {
                val errorBody = httpResponse.body?.string() ?: ""
                // HTTP 404 表示会话已过期
                if (httpResponse.code == 404 && sessionId != null) {
                    sessionId = null
                    throw TransportException("Session expired (HTTP 404). Re-initialization required. Response: $errorBody")
                }
                throw TransportException("HTTP ${httpResponse.code} (url: $endpoint): $errorBody")
            }

            val contentType = httpResponse.header("Content-Type", "")

            when {
                contentType?.contains("text/event-stream") == true -> {
                    // SSE 响应 - 读取第一个匹配的响应
                    val sseResponse = readSseResponse(httpResponse, request.id?.value)
                    sseResponse ?: throw TransportException("No matching response found in SSE stream")
                }
                contentType?.contains("application/json") == true -> {
                    val body = httpResponse.body?.string()
                        ?: throw TransportException("Empty response body")
                    json.decodeFromString(JsonRpcResponse.serializer(), body)
                }
                else -> {
                    val body = httpResponse.body?.string()
                        ?: throw TransportException("Empty response body")
                    json.decodeFromString(JsonRpcResponse.serializer(), body)
                }
            }
        }
    }

    /**
     * 发送 JSON-RPC 请求，返回 SSE 事件流
     * 用于可能返回多个消息的场景
     */
    fun sendRequestStream(request: JsonRpcRequest): Flow<JsonRpcResponse> = flow {
        val requestJson = json.encodeToString(JsonRpcRequest.serializer(), request)
        val httpRequest = buildPostRequest(requestJson)

        val httpResponse = httpClient.newCall(httpRequest).execute()

        extractSessionId(httpResponse)

        if (!httpResponse.isSuccessful) {
            if (httpResponse.code == 404 && sessionId != null) {
                sessionId = null
                throw TransportException("Session expired (HTTP 404)")
            }
            throw TransportException("HTTP ${httpResponse.code}: ${httpResponse.body?.string()}")
        }

        val contentType = httpResponse.header("Content-Type", "")

        if (contentType?.contains("text/event-stream") == true) {
            val body = httpResponse.body ?: throw TransportException("Empty SSE body")
            val source = body.source()
            val sseParser = McpSseParser()

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                val eventData = sseParser.parseLine(line) ?: continue

                val response = json.decodeFromString(JsonRpcResponse.serializer(), eventData)
                emit(response)
            }
        } else {
            // 单个 JSON 响应
            val body = httpResponse.body?.string()
                ?: throw TransportException("Empty response body")
            val response = json.decodeFromString(JsonRpcResponse.serializer(), body)
            emit(response)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 发送通知（无 id，不期望响应）
     */
    override suspend fun sendNotification(request: JsonRpcRequest): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val requestJson = json.encodeToString(JsonRpcRequest.serializer(), request)
            val httpRequest = buildPostRequest(requestJson)

            val httpResponse = httpClient.newCall(httpRequest).execute()

            extractSessionId(httpResponse)

            if (!httpResponse.isSuccessful && httpResponse.code != 202) {
                throw TransportException("HTTP ${httpResponse.code}")
            }
        }
    }

    /**
     * 打开 GET SSE 流，用于接收服务端主动推送
     *
     * 返回 SseEvent，可能是 JSON-RPC 响应或通知。
     * 通知包含 method 字段（如 notifications/tools/list_changed）。
     */
    override fun openSseStream(): Flow<SseEvent> = flow {
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .addHeader("Accept", "text/event-stream")
            .get()

        sessionId?.let { requestBuilder.addHeader(HEADER_SESSION_ID, it) }
        customHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        val httpResponse = httpClient.newCall(requestBuilder.build()).execute()

        if (!httpResponse.isSuccessful) {
            throw TransportException("SSE stream failed: HTTP ${httpResponse.code}")
        }

        val body = httpResponse.body ?: throw TransportException("Empty SSE body")
        val source = body.source()
        val sseParser = McpSseParser()

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            val eventData = sseParser.parseLine(line) ?: continue

            // 尝试解析为通知（有 method，无 id）
            val notification = tryParseNotification(eventData)
            if (notification != null) {
                emit(SseEvent.Notification(notification))
                continue
            }

            // 尝试解析为响应
            val response = json.decodeFromString(JsonRpcResponse.serializer(), eventData)
            emit(SseEvent.Response(response))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 关闭会话 (HTTP DELETE)
     */
    override suspend fun closeSession(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sid = sessionId ?: throw TransportException("No active session")
            val request = Request.Builder()
                .url(endpoint)
                .addHeader(HEADER_SESSION_ID, sid)
                .delete()
                .build()

            httpClient.newCall(request).execute().close()
            sessionId = null
        }
    }

    // ============ Private Helpers ============

    private fun buildPostRequest(body: String): Request {
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json, text/event-stream")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))

        sessionId?.let { requestBuilder.addHeader(HEADER_SESSION_ID, it) }
        requestBuilder.addHeader(HEADER_PROTOCOL_VERSION, protocolVersion)
        customHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        return requestBuilder.build()
    }

    private fun extractSessionId(response: Response) {
        response.header(HEADER_SESSION_ID)?.let {
            sessionId = it
        }
    }

    /**
     * 尝试将 JSON 字符串解析为 JsonRpcNotification。
     * 通知的特征：包含 "method" 字段且不包含 "id" 字段。
     */
    private fun tryParseNotification(jsonString: String): JsonRpcNotification? {
        return try {
            val obj = json.decodeFromString<kotlinx.serialization.json.JsonObject>(jsonString)
            if (obj.containsKey("method") && !obj.containsKey("id")) {
                json.decodeFromString(JsonRpcNotification.serializer(), jsonString)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readSseResponse(response: Response, requestId: String?): JsonRpcResponse? {
        val body = response.body ?: return null
        val source = body.source()
        val sseParser = McpSseParser()

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            val eventData = sseParser.parseLine(line) ?: continue

            val rpcResponse = json.decodeFromString(JsonRpcResponse.serializer(), eventData)

            // 匹配请求 ID 的响应
            if (requestId == null || rpcResponse.id?.value == requestId) {
                return rpcResponse
            }
        }
        return null
    }
}

/**
 * MCP SSE 解析器
 */
class McpSseParser {
    private var currentData = StringBuilder()

    fun parseLine(line: String): String? {
        if (line.isEmpty()) {
            val data = currentData.toString().trim()
            currentData = StringBuilder()
            return if (data.isNotEmpty()) data else null
        }

        if (line.startsWith(":")) return null

        if (line.startsWith("data:")) {
            val dataContent = line.removePrefix("data:").trim()
            currentData.append(dataContent)
            return null
        }

        return null
    }

    fun reset() {
        currentData = StringBuilder()
    }
}

class TransportException(message: String) : Exception(message)
