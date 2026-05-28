package top.ethan2048.easyllm.mcp.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import top.ethan2048.easyllm.mcp.protocol.JsonRpcNotification
import top.ethan2048.easyllm.mcp.protocol.JsonRpcRequest
import top.ethan2048.easyllm.mcp.protocol.JsonRpcResponse
import top.ethan2048.easyllm.mcp.protocol.SseEvent

class SseTransport(
    private val endpoint: String,
    private val ssePath: String = "/sse",
    private val messagesPath: String = "/messages/",
    private val customHeaders: Map<String, String> = emptyMap(),
    private val httpClient: OkHttpClient = OkHttpClient()
) : McpTransport {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    override val protocolVersion: String = "2025-06-18"

    override var sessionId: String? = null

    private val responseChannel = Channel<Pair<String?, JsonRpcResponse>>(Channel.UNLIMITED)
    private val eventChannel = Channel<SseEvent>(Channel.UNLIMITED)

    private var sseJob: Job? = null
    private var sseResponse: Response? = null

    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.IO
    )

    suspend fun connect(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val sseUrl = endpoint.trimEnd('/') + ssePath
            val requestBuilder = Request.Builder()
                .url(sseUrl)
                .addHeader("Accept", "text/event-stream")
                .get()

            customHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val tempResponse = httpClient.newCall(requestBuilder.build()).execute()

            if (!tempResponse.isSuccessful) {
                val errorBody = tempResponse.body?.string() ?: ""
                throw TransportException("SSE connection failed: HTTP ${tempResponse.code} - $errorBody")
            }

            sseResponse = tempResponse
            val body = sseResponse!!.body ?: throw TransportException("Empty SSE body")
            val source = body.source()
            val sseParser = McpSseParser()
            val sessionIdChannel = kotlinx.coroutines.channels.Channel<String>(1)

            sseJob = scope.launch {
                try {
                    while (isActive && !source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        val eventData = sseParser.parseLine(line) ?: continue

                        if (sessionId == null) {
                            val parsedId = tryParseSessionId(eventData)
                            if (parsedId != null) {
                                sessionId = parsedId
                                sessionIdChannel.trySend(parsedId)
                                continue
                            }
                        }

                        val notification = tryParseNotification(eventData)
                        if (notification != null) {
                            eventChannel.trySend(SseEvent.Notification(notification))
                            continue
                        }

                        try {
                            val response = json.decodeFromString(JsonRpcResponse.serializer(), eventData)
                            val requestId = response.id?.value
                            responseChannel.send(requestId to response)
                            eventChannel.trySend(SseEvent.Response(response))
                        } catch (_: Exception) {
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    eventChannel.close()
                    responseChannel.close()
                    sessionIdChannel.close()
                }
            }

            // 等待 sessionId 或者 5 秒超时
            var waited = 0
            while (sessionId == null && waited < 50) {
                delay(100)
                waited++
            }

            sessionId ?: throw TransportException("No session ID received from SSE stream (url: $sseUrl)")
        }
    }

    override suspend fun sendRequest(request: JsonRpcRequest): Result<JsonRpcResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val requestJson = json.encodeToString(JsonRpcRequest.serializer(), request)
                val messagesUrl = buildMessagesUrl()
                val httpRequestBuilder = Request.Builder()
                    .url(messagesUrl)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json, text/event-stream")
                    .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))

                customHeaders.forEach { (k, v) -> httpRequestBuilder.addHeader(k, v) }

                val httpResponse = httpClient.newCall(httpRequestBuilder.build()).execute()

                if (!httpResponse.isSuccessful) {
                    throw TransportException("HTTP ${httpResponse.code}: ${httpResponse.body?.string()}")
                }

                val contentType = httpResponse.header("Content-Type", "")
                if (contentType?.contains("application/json") == true) {
                    val body = httpResponse.body?.string()
                        ?: throw TransportException("Empty response body")
                    json.decodeFromString(JsonRpcResponse.serializer(), body)
                } else {
                    val requestId = request.id?.value
                    var matched: JsonRpcResponse? = null
                    while (matched == null) {
                        val entry = responseChannel.receive()
                        val id = entry.first
                        val resp = entry.second
                        if (requestId == null || id == requestId) {
                            matched = resp
                        }
                    }
                    matched
                }
            }
        }

    override suspend fun sendNotification(request: JsonRpcRequest): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val requestJson = json.encodeToString(JsonRpcRequest.serializer(), request)
                val messagesUrl = buildMessagesUrl()
                val httpRequestBuilder = Request.Builder()
                    .url(messagesUrl)
                    .addHeader("Content-Type", "application/json")
                    .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))

                customHeaders.forEach { (k, v) -> httpRequestBuilder.addHeader(k, v) }

                val httpResponse = httpClient.newCall(httpRequestBuilder.build()).execute()

                if (!httpResponse.isSuccessful && httpResponse.code != 202) {
                    throw TransportException("HTTP ${httpResponse.code}")
                }
            }
        }

    override fun openSseStream(): Flow<SseEvent> = flow {
        for (event in eventChannel) {
            emit(event)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun closeSession(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            sseJob?.cancel()
            sseJob = null
            sseResponse?.close()
            sseResponse = null
            sessionId = null
            kotlin.runCatching { responseChannel.close() }
            kotlin.runCatching { eventChannel.close() }
            Unit
        }
    }

    private fun buildMessagesUrl(): String {
        val base = endpoint.trimEnd('/')
        val sid = sessionId ?: return base + messagesPath
        return base + messagesPath + "?sessionId=$sid"
    }

    private fun tryParseSessionId(data: String): String? {
        return try {
            val obj = json.decodeFromString<kotlinx.serialization.json.JsonObject>(data)
            // 尝试多种可能的键名
            (obj["sessionId"] ?: obj["session_id"])?.toString()?.trim('"')
        } catch (_: Exception) {
            // 尝试直接作为字符串
            if (data.isNotBlank() && data.length < 100 && !data.startsWith("{") && !data.startsWith("[")) {
                data.trim()
            } else null
        }
    }

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
}