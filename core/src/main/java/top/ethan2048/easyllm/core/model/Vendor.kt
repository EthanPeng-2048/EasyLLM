package top.ethan2048.easyllm.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Vendor(
    val id: String,
    val name: String,
    val endpoint: String,
    val apiKey: String,
    val models: List<ModelConfig> = emptyList()
)

@Serializable
data class ModelConfig(
    val id: String,
    val name: String,
    val model: String,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 1.0f,
    val maxTokens: Int = 4096,
    val contextLength: Int = 128000
)
