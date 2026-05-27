package top.ethan2048.easyllm.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiConfig(
    val id: String,
    val name: String,
    val endpoint: String,       // e.g. "https://api.openai.com/v1"
    val apiKey: String,
    val model: String,          // e.g. "gpt-4o"
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f
)
