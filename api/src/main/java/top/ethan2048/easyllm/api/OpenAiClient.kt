package top.ethan2048.easyllm.api

import com.aallam.openai.api.chat.ChatCompletionChunk
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage as OpenAIChatMessage
import com.aallam.openai.api.chat.CompletionMessage
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import top.ethan2048.easyllm.core.domain.api.IChatApi
import top.ethan2048.easyllm.core.domain.model.ApiConfig
import top.ethan2048.easyllm.core.domain.model.ChatMessage
import top.ethan2048.easyllm.core.domain.model.ChatResponse
import top.ethan2048.easyllm.core.domain.model.ChatStreamChunk
import top.ethan2048.easyllm.core.domain.model.Model
import top.ethan2048.easyllm.core.domain.model.ToolDefinition
import top.ethan2048.easyllm.core.domain.model.ToolCall

/**
 * 基于 aallam/openai-kotlin 官方库的 OpenAI 兼容 API 客户端
 */
class OpenAiClient(
    override var config: ApiConfig,
    private val httpClient: OkHttpClient = OkHttpClient()
) : IChatApi {

    private val openAI: OpenAI by lazy {
        val okHttpClient = httpClient.newBuilder()
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("Authorization", "Bearer ${config.apiKey}")
                    .build()
                chain.proceed(request)
            }
            .build()

        OpenAI(
            token = config.apiKey,
            host = config.endpoint.trimEnd('/'),
            client = okHttpClient
        )
    }

    override suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>?
    ): Result<ChatResponse> = runCatching {
        val request = buildChatCompletionRequest(messages, tools, stream = false)
        val completion = openAI.chatCompletion(request)
        
        completion.choices.firstOrNull()?.let { choice ->
            ChatResponse(
                id = completion.id?.value ?: "",
                model = completion.model?.id ?: "",
                choices = listOf(
                    top.ethan2048.easyllm.core.domain.model.Choice(
                        index = choice.index,
                        message = ChatMessage(
                            role = choice.message.role.name.lowercase(),
                            content = choice.message.content ?: ""
                        ),
                        finishReason = choice.finishReason?.name?.lowercase()
                    )
                ),
                usage = completion.usage?.let { usage ->
                    top.ethan2048.easyllm.core.domain.model.Usage(
                        promptTokens = usage.promptTokens,
                        completionTokens = usage.completionTokens,
                        totalTokens = usage.totalTokens
                    )
                }
            )
        } ?: throw ApiException("Empty response from server")
    }

    override fun chatStream(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>?
    ): Flow<ChatStreamChunk> {
        val request = buildChatCompletionRequest(messages, tools, stream = true)
        return openAI.chatCompletions(request).map { chunk ->
            chunk.toChatStreamChunk()
        }
    }

    override suspend fun testConnection(): Result<Boolean> = runCatching {
        openAI.models().isNotEmpty()
    }

    override suspend fun getModels(): Result<List<Model>> = runCatching {
        openAI.models().map { model ->
            Model(
                id = model.id.value,
                name = model.id.value,
                ownedBy = model.ownedBy
            )
        }
    }

    private fun buildChatCompletionRequest(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>?,
        stream: Boolean
    ): ChatCompletionRequest {
        val openAIMessages = messages.map { msg ->
            when (msg.role) {
                "system" -> OpenAIChatMessage.SystemMessage(content = msg.content ?: "")
                "user" -> OpenAIChatMessage.UserMessage(
                    content = msg.content ?: "",
                    name = null
                )
                "assistant" -> CompletionMessage(
                    role = com.aallam.openai.api.chat.Role.Assistant,
                    content = msg.content ?: "",
                    toolCalls = msg.toolCalls?.map { toolCall ->
                        com.aallam.openai.api.chat.ToolCall(
                            id = toolCall.id,
                            type = "function",
                            function = com.aallam.openai.api.chat.FunctionCall(
                                name = toolCall.function.name,
                                arguments = toolCall.function.arguments
                            )
                        )
                    },
                    refusal = null
                )
                else -> OpenAIChatMessage.UserMessage(content = msg.content ?: "")
            }
        }

        val openAITools = tools?.map { tool ->
            com.aallam.openai.api.chat.Tool(
                type = "function",
                function = com.aallam.openai.api.chat.FunctionDefinition(
                    name = tool.function.name,
                    description = tool.function.description,
                    parameters = tool.function.parameters
                )
            )
        }

        return ChatCompletionRequest(
            model = ModelId(config.model),
            messages = openAIMessages,
            maxTokens = config.maxTokens,
            temperature = config.temperature.toFloat(),
            stream = stream,
            tools = openAITools
        )
    }

    private fun ChatCompletionChunk.toChatStreamChunk(): ChatStreamChunk {
        return ChatStreamChunk(
            id = this.id?.value ?: "",
            model = this.model?.id ?: "",
            choices = this.choices.map { choice ->
                top.ethan2048.easyllm.core.domain.model.StreamChoice(
                    index = choice.index,
                    delta = top.ethan2048.easyllm.core.domain.model.Delta(
                        role = choice.delta?.role?.name?.lowercase(),
                        content = choice.delta?.content
                    ),
                    finishReason = choice.finishReason?.name?.lowercase()
                )
            },
            usage = this.usage?.let { usage ->
                top.ethan2048.easyllm.core.domain.model.Usage(
                    promptTokens = usage.promptTokens,
                    completionTokens = usage.completionTokens,
                    totalTokens = usage.totalTokens
                )
            }
        )
    }
}

class ApiException(message: String, val errorType: String? = null) : Exception(message)
