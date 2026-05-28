package top.ethan2048.easyllm.mcp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ============ JSON-RPC 2.0 Message Models ============

/**
 * JSON-RPC 2.0 请求
 */
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val id: JsonRpcId? = null,
    val method: String,
    val params: JsonObject? = null
)

/**
 * JSON-RPC 2.0 响应
 */
@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val id: JsonRpcId? = null,
    val result: JsonObject? = null,
    val error: JsonRpcError? = null
)

/**
 * JSON-RPC 2.0 错误
 */
@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

// ============ JSON-RPC ID ============

/**
 * JSON-RPC 2.0 请求/响应 ID
 *
 * 使用自定义序列化器，使 id 在 JSON 中呈现为纯字符串 "1"，
 * 而非对象 {"value": "1"}，符合 JSON-RPC 规范。
 */
@Serializable(with = JsonRpcIdSerializer::class)
data class JsonRpcId(val value: String) {
    override fun toString() = value
}

object JsonRpcIdSerializer : kotlinx.serialization.KSerializer<JsonRpcId> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "JsonRpcId",
        kotlinx.serialization.descriptors.PrimitiveKind.STRING
    )

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: JsonRpcId) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): JsonRpcId {
        return JsonRpcId(decoder.decodeString())
    }
}

/**
 * JSON-RPC 2.0 服务端通知（SSE 推送）
 * 与 JsonRpcRequest 类似，但没有 id（由服务端发出）
 */
@Serializable
data class JsonRpcNotification(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonObject? = null
)

/**
 * SSE 事件：可以是响应或通知
 */
sealed class SseEvent {
    data class Response(val response: JsonRpcResponse) : SseEvent()
    data class Notification(val notification: JsonRpcNotification) : SseEvent()
}

// ============ JSON-RPC Error Codes ============

object JsonRpcErrorCodes {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
    const val RESOURCE_NOT_FOUND = -32002
}

// ============ Helper Functions ============

private var requestCounter = 0

fun nextRequestId(): JsonRpcId = JsonRpcId("${++requestCounter}")

fun buildJsonRpcRequest(
    method: String,
    params: Map<String, Any>? = null
): JsonRpcRequest {
    val paramsObj = params?.let {
        buildJsonObject {
            it.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Int -> put(key, value)
                    is Long -> put(key, value)
                    is Float -> put(key, value)
                    is Double -> put(key, value)
                    is Boolean -> put(key, value)
                    is JsonElement -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
        }
    }
    return JsonRpcRequest(
        id = nextRequestId(),
        method = method,
        params = paramsObj
    )
}

fun buildJsonRpcNotification(
    method: String,
    params: Map<String, Any>? = null
): JsonRpcRequest {
    val paramsObj = params?.let {
        buildJsonObject {
            it.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Int -> put(key, value)
                    is Long -> put(key, value)
                    is Float -> put(key, value)
                    is Double -> put(key, value)
                    is Boolean -> put(key, value)
                    is JsonElement -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
        }
    }
    return JsonRpcRequest(
        id = null,  // notifications have no id
        method = method,
        params = paramsObj
    )
}
