package top.ethan2048.easyllm.mcp.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import top.ethan2048.easyllm.core.model.McpContent
import top.ethan2048.easyllm.core.model.McpTool
import top.ethan2048.easyllm.core.model.McpToolResult
import top.ethan2048.easyllm.mcp.transport.StreamableHttpTransport

/**
 * MCP Tools 协议实现
 */
class ToolsProtocol(private val transport: StreamableHttpTransport) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * tools/list - 获取可用工具列表
     */
    suspend fun list(cursor: String? = null): Result<List<McpTool>> = runCatching {
        val params = buildJsonObject {
            cursor?.let { put("cursor", it) }
        }

        val request = JsonRpcRequest(
            id = nextRequestId(),
            method = "tools/list",
            params = if (cursor != null) params else null
        )

        val response = transport.sendRequest(request).getOrThrow()

        if (response.error != null) {
            throw McpProtocolException("tools/list failed: ${response.error.message}")
        }

        val result = response.result ?: throw McpProtocolException("Missing result")

        val toolsArray = result["tools"]?.jsonArray
            ?: throw McpProtocolException("Missing tools in response")

        toolsArray.map { element ->
            json.decodeFromJsonElement(McpTool.serializer(), element)
        }
    }

    /**
     * tools/call - 调用工具
     */
    suspend fun call(name: String, arguments: Map<String, Any>): Result<McpToolResult> = runCatching {
        val argsObj = buildJsonObject {
            arguments.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Int -> put(key, value)
                    is Long -> put(key, value)
                    is Float -> put(key, value)
                    is Double -> put(key, value)
                    is Boolean -> put(key, value)
                    is JsonObject -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
        }

        val params = buildJsonObject {
            put("name", name)
            put("arguments", argsObj)
        }

        val request = JsonRpcRequest(
            id = nextRequestId(),
            method = "tools/call",
            params = params
        )

        val response = transport.sendRequest(request).getOrThrow()

        if (response.error != null) {
            throw McpProtocolException("tools/call failed: ${response.error.message}")
        }

        val result = response.result ?: throw McpProtocolException("Missing result")

        val contentArray = result["content"]?.jsonArray
            ?: throw McpProtocolException("Missing content in response")

        val content = contentArray.map { element ->
            json.decodeFromJsonElement(McpContent.serializer(), element)
        }

        val isError = (result["isError"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false

        McpToolResult(content = content, isError = isError)
    }
}

class McpProtocolException(message: String) : Exception(message)
