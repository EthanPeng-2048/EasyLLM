package top.ethan2048.easyllm.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import top.ethan2048.easyllm.core.`interface`.IChatApi
import top.ethan2048.easyllm.core.model.ApiConfig
import top.ethan2048.easyllm.core.model.ChatMessage
import top.ethan2048.easyllm.core.model.ChatResponse
import top.ethan2048.easyllm.core.model.ChatStreamChunk
import top.ethan2048.easyllm.core.model.ToolDefinition

/**
 * OpenAI 兼容 API 客户端
 * 支持 ChatCompletion 接口，包括流式响应
 */
class OpenAiClient(
    override var config: ApiConfig,
    private val httpClient: OkHttpClient = OkHttpClient()
) : IChatApi {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>?
    ): Result<ChatResponse> = runCatching {
        val requestBody = buildRequestBody(messages, tools, stream = false)
        val request = buildRequest(requestBody)

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw ApiException("HTTP ${response.code}: ${response.body?.string()}")
        }

        val body = response.body?.string()
            ?: throw ApiException("Empty response body")

        json.decodeFromString<ChatResponse>(body)
    }

    override fun chatStream(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>?
    ): Flow<ChatStreamChunk> = flow {
        val requestBody = buildRequestBody(messages, tools, stream = true)
        val request = buildRequest(requestBody)

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw ApiException("HTTP ${response.code}: ${response.body?.string()}")
        }

        val body = response.body ?: throw ApiException("Empty response body")
        val source = body.source()

        val sseParser = SSEParser()
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            val data = sseParser.parseLine(line) ?: continue

            if (data == "[DONE]") break

            val chunk = json.decodeFromString<ChatStreamChunk>(data)
            emit(chunk)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun testConnection(): Result<Boolean> = runCatching {
        val request = Request.Builder()
            .url("${config.endpoint.trimEnd('/')}/models")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        response.isSuccessful
    }

    private fun buildRequestBody(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>?,
        stream: Boolean
    ): String {
        val requestObj = buildJsonObject {
            put("model", config.model)
            put("messages", buildJsonArray {
                for (msg in messages) {
                    add(json.encodeToJsonElement(msg))
                }
            })
            put("max_tokens", config.maxTokens)
            put("temperature", config.temperature)
            put("stream", stream)
            if (!tools.isNullOrEmpty()) {
                put("tools", buildJsonArray {
                    for (tool in tools) {
                        add(json.encodeToJsonElement(tool))
                    }
                })
            }
        }
        return requestObj.toString()
    }

    private fun buildRequest(requestBody: String): Request {
        return Request.Builder()
            .url("${config.endpoint.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }
}

class ApiException(message: String) : Exception(message)
