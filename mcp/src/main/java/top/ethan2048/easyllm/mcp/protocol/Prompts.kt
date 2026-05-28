package top.ethan2048.easyllm.mcp.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.ethan2048.easyllm.core.domain.model.mcp.McpPrompt
import top.ethan2048.easyllm.core.domain.model.mcp.McpPromptMessage
import top.ethan2048.easyllm.core.domain.model.mcp.McpPromptResult
import top.ethan2048.easyllm.mcp.transport.McpTransport

/**
 * MCP Prompts 协议实现
 *
 * 对应 MCP 规范中的 prompts/list 和 prompts/get 方法。
 * 用于发现和获取提示模板（Prompt Templates）。
 */
class PromptsProtocol(private val transport: McpTransport) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * prompts/list - 获取可用提示模板列表
     *
     * @param cursor 分页游标（可选）
     * @return 提示模板列表
     */
    suspend fun list(cursor: String? = null): Result<List<McpPrompt>> = runCatching {
        val params = mutableMapOf<String, Any>()
        cursor?.let { params["cursor"] = it }

        val request = buildJsonRpcRequest("prompts/list", params.ifEmpty { null })
        val response = transport.sendRequest(request).getOrThrow()

        if (response.error != null) {
            throw McpProtocolException("prompts/list failed: ${response.error.message}")
        }

        val result = response.result ?: throw McpProtocolException("Missing result")

        val promptsArray = result["prompts"]?.jsonArray
            ?: throw McpProtocolException("Missing prompts in response")

        promptsArray.map { element ->
            json.decodeFromJsonElement(McpPrompt.serializer(), element)
        }
    }

    /**
     * prompts/get - 获取指定提示模板的内容
     *
     * @param name 提示模板名称
     * @param arguments 模板参数（可选）
     * @return 提示模板结果，包含消息列表
     */
    suspend fun get(
        name: String,
        arguments: Map<String, String>? = null
    ): Result<McpPromptResult> = runCatching {
        val params = mutableMapOf<String, Any>("name" to name)
        arguments?.let { params["arguments"] = it }

        val request = buildJsonRpcRequest("prompts/get", params)
        val response = transport.sendRequest(request).getOrThrow()

        if (response.error != null) {
            throw McpProtocolException("prompts/get failed: ${response.error.message}")
        }

        val result = response.result ?: throw McpProtocolException("Missing result")

        val description = result["description"]?.jsonPrimitive?.content

        val messagesArray = result["messages"]?.jsonArray
            ?: throw McpProtocolException("Missing messages in response")

        val messages = messagesArray.map { element ->
            json.decodeFromJsonElement(McpPromptMessage.serializer(), element)
        }

        McpPromptResult(
            description = description,
            messages = messages
        )
    }
}
