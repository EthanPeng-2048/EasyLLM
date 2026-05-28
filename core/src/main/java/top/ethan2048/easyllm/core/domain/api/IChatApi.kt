package top.ethan2048.easyllm.core.domain.api

import kotlinx.coroutines.flow.Flow
import top.ethan2048.easyllm.core.domain.model.ApiConfig
import top.ethan2048.easyllm.core.domain.model.ChatMessage
import top.ethan2048.easyllm.core.domain.model.ChatResponse
import top.ethan2048.easyllm.core.domain.model.ChatStreamChunk
import top.ethan2048.easyllm.core.domain.model.Model
import top.ethan2048.easyllm.core.domain.model.ToolDefinition

/**
 * API 模块对外接口
 * 提供 LLM API 对话能力
 */
interface IChatApi {
    /** 当前 API 配置 */
    var config: ApiConfig

    /** 非流式对话 */
    suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>? = null
    ): Result<ChatResponse>

    /** 流式对话，返回 Flow 逐块推送 */
    fun chatStream(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>? = null
    ): Flow<ChatStreamChunk>

    /** 测试连接是否可用 */
    suspend fun testConnection(): Result<Boolean>

    /** 获取模型列表 */
    suspend fun getModels(): Result<List<Model>>
}
