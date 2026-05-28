package top.ethan2048.easyllm.core.domain.model.mcp

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ============ MCP Server Config ============

@Serializable
enum class McpTransportType {
    @kotlinx.serialization.SerialName("streamable_http")
    STREAMABLE_HTTP,
    @kotlinx.serialization.SerialName("sse")
    SSE
}

@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val endpoint: String,           // e.g. "https://example.com/mcp"
    val headers: Map<String, String> = emptyMap(),
    val transportType: McpTransportType = McpTransportType.STREAMABLE_HTTP,
    val ssePath: String = "/sse",
    val messagesPath: String = "/messages/"
)

@Serializable
data class McpServerInfo(
    val name: String,
    val title: String? = null,
    val version: String,
    val protocolVersion: String,
    val capabilities: McpServerCapabilities,
    val instructions: String? = null
)

@Serializable
data class McpServerCapabilities(
    val tools: McpCapabilityTools? = null,
    val resources: McpCapabilityResources? = null,
    val prompts: McpCapabilityPrompts? = null,
    val logging: McpCapabilityLogging? = null
)

@Serializable
data class McpCapabilityTools(
    val listChanged: Boolean = false
)

@Serializable
data class McpCapabilityResources(
    val subscribe: Boolean = false,
    val listChanged: Boolean = false
)

@Serializable
data class McpCapabilityPrompts(
    val listChanged: Boolean = false
)

@Serializable
data class McpCapabilityLogging(
    val supported: Boolean = false
)

// ============ MCP Tools ============

@Serializable
data class McpTool(
    val name: String,
    val title: String? = null,
    val description: String? = null,
    val inputSchema: JsonObject = JsonObject(emptyMap()),
    val outputSchema: JsonObject? = null,
    val annotations: McpToolAnnotations? = null
)

@Serializable
data class McpToolAnnotations(
    val title: String? = null,
    val readOnlyHint: Boolean = false,
    val destructiveHint: Boolean = true,
    val idempotentHint: Boolean = false,
    val openWorldHint: Boolean = true
)

@Serializable
data class McpToolResult(
    val content: List<McpContent>,
    val isError: Boolean = false,
    val structuredContent: JsonObject? = null
)

// ============ MCP Content ============

@Serializable
data class McpContent(
    val type: String,       // "text", "image", "audio", "resource", "resource_link"
    val text: String? = null,
    val data: String? = null,       // base64 for image/audio
    val mimeType: String? = null,
    val uri: String? = null,
    val name: String? = null,
    val description: String? = null,
    val resource: McpEmbeddedResource? = null,
    val annotations: McpContentAnnotations? = null
)

@Serializable
data class McpEmbeddedResource(
    val uri: String,
    val mimeType: String? = null,
    val text: String? = null,
    val blob: String? = null,
    val annotations: McpContentAnnotations? = null
)

@Serializable
data class McpContentAnnotations(
    val audience: List<String>? = null,
    val priority: Float? = null,
    val lastModified: String? = null
)

// ============ MCP Resources ============

@Serializable
data class McpResource(
    val uri: String,
    val name: String,
    val title: String? = null,
    val description: String? = null,
    val mimeType: String? = null,
    val size: Long? = null,
    val annotations: McpContentAnnotations? = null
)

@Serializable
data class McpResourceTemplate(
    val uriTemplate: String,
    val name: String,
    val title: String? = null,
    val description: String? = null,
    val mimeType: String? = null,
    val annotations: McpContentAnnotations? = null
)

@Serializable
data class McpResourceContent(
    val uri: String,
    val mimeType: String? = null,
    val text: String? = null,
    val blob: String? = null   // base64
)

// ============ MCP Prompts ============

@Serializable
data class McpPrompt(
    val name: String,
    val title: String? = null,
    val description: String? = null,
    val arguments: List<McpPromptArgument>? = null
)

@Serializable
data class McpPromptArgument(
    val name: String,
    val description: String? = null,
    val required: Boolean = false
)

@Serializable
data class McpPromptResult(
    val description: String? = null,
    val messages: List<McpPromptMessage>
)

@Serializable
data class McpPromptMessage(
    val role: String,     // "user" or "assistant"
    val content: McpContent
)

// ============ MCP Events ============

sealed class McpEvent {
    data class ToolsListChanged(val serverId: String) : McpEvent()
    data class ResourcesListChanged(val serverId: String) : McpEvent()
    data class ResourceUpdated(val serverId: String, val uri: String) : McpEvent()
    data class ServerDisconnected(val serverId: String) : McpEvent()
    data class LogMessage(val serverId: String, val level: String, val message: String) : McpEvent()
}
