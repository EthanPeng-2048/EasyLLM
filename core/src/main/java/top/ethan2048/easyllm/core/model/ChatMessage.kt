package top.ethan2048.easyllm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MessageRole {
    @SerialName("system") SYSTEM,
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
    @SerialName("tool") TOOL
}

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: ToolCallFunction
)

@Serializable
data class ToolCallFunction(
    val name: String,
    val arguments: String
)

@Serializable
data class ChatMessage(
    val role: MessageRole,
    val content: String,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null
)

@Serializable
data class ToolDefinition(
    val type: String = "function",
    val function: ToolFunctionDef
)

@Serializable
data class ToolFunctionDef(
    val name: String,
    val description: String? = null,
    val parameters: String? = null  // JSON string of JSON Schema
)

@Serializable
data class ChatResponse(
    val id: String,
    val model: String,
    val choices: List<ChatChoice>,
    val usage: ChatUsage? = null
)

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChatUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

@Serializable
data class ChatStreamChunk(
    val id: String,
    val model: String,
    val choices: List<ChatStreamChoice>
)

@Serializable
data class ChatStreamChoice(
    val index: Int = 0,
    val delta: ChatStreamDelta,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChatStreamDelta(
    val role: MessageRole? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDelta>? = null
)

@Serializable
data class ToolCallDelta(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: ToolCallFunctionDelta? = null
)

@Serializable
data class ToolCallFunctionDelta(
    val name: String? = null,
    val arguments: String? = null
)
