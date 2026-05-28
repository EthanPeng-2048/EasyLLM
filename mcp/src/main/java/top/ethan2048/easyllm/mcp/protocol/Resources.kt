package top.ethan2048.easyllm.mcp.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.ethan2048.easyllm.core.domain.model.mcp.McpResource
import top.ethan2048.easyllm.core.domain.model.mcp.McpResourceContent
import top.ethan2048.easyllm.core.domain.model.mcp.McpResourceTemplate
import top.ethan2048.easyllm.mcp.transport.McpTransport

/**
 * MCP Resources 协议实现
 */
class ResourcesProtocol(private val transport: McpTransport) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * resources/list - 获取可用资源列表
     */
    suspend fun list(cursor: String? = null): Result<List<McpResource>> = runCatching {
        val params = mutableMapOf<String, Any>()
        cursor?.let { params["cursor"] = it }

        val request = buildJsonRpcRequest("resources/list", params.ifEmpty { null })
        val response = transport.sendRequest(request).getOrThrow()

        if (response.error != null) {
            throw McpProtocolException("resources/list failed: ${response.error.message}")
        }

        val result = response.result ?: throw McpProtocolException("Missing result")

        val resourcesArray = result["resources"]?.jsonArray
            ?: throw McpProtocolException("Missing resources in response")

        resourcesArray.map { element ->
            json.decodeFromJsonElement(McpResource.serializer(), element)
        }
    }

    /**
     * resources/read - 读取资源内容
     */
    suspend fun read(uri: String): Result<List<McpResourceContent>> = runCatching {
        val request = buildJsonRpcRequest("resources/read", mapOf("uri" to uri))
        val response = transport.sendRequest(request).getOrThrow()

        if (response.error != null) {
            throw McpProtocolException("resources/read failed: ${response.error.message}")
        }

        val result = response.result ?: throw McpProtocolException("Missing result")

        val contentsArray = result["contents"]?.jsonArray
            ?: throw McpProtocolException("Missing contents in response")

        contentsArray.map { element ->
            json.decodeFromJsonElement(McpResourceContent.serializer(), element)
        }
    }

    /**
     * resources/templates/list - 获取资源模板列表
     */
    suspend fun listTemplates(cursor: String? = null): Result<List<McpResourceTemplate>> = runCatching {
        val params = mutableMapOf<String, Any>()
        cursor?.let { params["cursor"] = it }

        val request = buildJsonRpcRequest("resources/templates/list", params.ifEmpty { null })
        val response = transport.sendRequest(request).getOrThrow()

        if (response.error != null) {
            throw McpProtocolException("resources/templates/list failed: ${response.error.message}")
        }

        val result = response.result ?: throw McpProtocolException("Missing result")

        val templatesArray = result["resourceTemplates"]?.jsonArray
            ?: throw McpProtocolException("Missing resourceTemplates in response")

        templatesArray.map { element ->
            json.decodeFromJsonElement(McpResourceTemplate.serializer(), element)
        }
    }

    /**
     * resources/subscribe - 订阅资源变更
     */
    suspend fun subscribe(uri: String): Result<Unit> = runCatching {
        val request = buildJsonRpcRequest("resources/subscribe", mapOf("uri" to uri))
        val response = transport.sendRequest(request).getOrThrow()

        if (response.error != null) {
            throw McpProtocolException("resources/subscribe failed: ${response.error.message}")
        }
    }

    /**
     * resources/unsubscribe - 取消订阅
     */
    suspend fun unsubscribe(uri: String): Result<Unit> = runCatching {
        val request = buildJsonRpcRequest("resources/unsubscribe", mapOf("uri" to uri))
        val response = transport.sendRequest(request).getOrThrow()

        if (response.error != null) {
            throw McpProtocolException("resources/unsubscribe failed: ${response.error.message}")
        }
    }
}
