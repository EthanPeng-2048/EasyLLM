# EasyLLM 开发文档

## 1. 项目概述

EasyLLM 是一款 Android AI 客户端应用，支持接入 LLM API 和 MCP (Model Context Protocol) 服务器，实现智能对话与工具调用。

### 核心功能
- **LLM API 对话**：兼容 OpenAI ChatCompletion API，支持流式响应
- **MCP 工具调用**：连接 MCP 服务器，发现和调用工具、读取资源、使用提示模板
- **多服务器管理**：同时管理多个 API 和 MCP 服务器配置

### 设计原则
- **严格模块化**：API处理、MCP处理、GUI 三大模块分离，仅通过公开接口交互
- **Material 3 UI**：简洁风格，无模糊效果，无多余动画
- **Kotlin + Jetpack Compose**：现代 Android 开发技术栈

---

## 2. 项目架构

```
EasyLLM/
├── app/                    # GUI 模块 (Android Application)
│   └── src/main/java/top/ethan2048/easyllm/
│       ├── MainActivity.kt
│       ├── ui/             # Compose UI
│       │   ├── screen/     # 各页面
│       │   ├── component/  # 可复用组件
│       │   └── theme/      # Material3 主题
│       └── di/             # 依赖注入
│
├── core/                   # 公共模块 (Android Library)
│   └── src/main/java/top/ethan2048/easyllm/core/
│       ├── model/          # 数据模型
│       ├── interface/      # 模块间接口定义
│       └── util/           # 工具类
│
├── api/                    # API 模块 (Android Library)
│   └── src/main/java/top/ethan2048/easyllm/api/
│       ├── OpenAiClient.kt
│       ├── SSEParser.kt
│       └── ApiConfig.kt
│
└── mcp/                    # MCP 模块 (Android Library)
    └── src/main/java/top/ethan2048/easyllm/mcp/
        ├── McpClient.kt
        ├── transport/      # 传输层
        │   └── StreamableHttpTransport.kt
        ├── protocol/       # 协议层
        │   ├── JsonRpc.kt
        │   ├── Lifecycle.kt
        │   ├── Tools.kt
        │   ├── Resources.kt
        │   └── Prompts.kt
        └── McpConfig.kt
```

### 模块依赖关系

```
    ┌─────┐
    │ app │  (GUI - 依赖 core, api, mcp)
    └──┬──┘
       │
    ┌──┴──────────────────┐
    │                     │
┌───┴───┐           ┌────┴────┐
│  api  │           │   mcp   │  (各依赖 core)
└───┬───┘           └────┬────┘
    │                    │
    └────────┬───────────┘
             │
        ┌────┴────┐
        │  core   │  (基础模块，无外部依赖)
        └─────────┘
```

---

## 3. 模块间接口定义

### 3.1 IChatApi（API 模块对外接口）

```kotlin
interface IChatApi {
    // 配置
    var config: ApiConfig

    // 非流式对话
    suspend fun chat(messages: List<ChatMessage>, tools: List<ToolDefinition>? = null): ChatResponse

    // 流式对话
    fun chatStream(messages: List<ChatMessage>, tools: List<ToolDefinition>? = null): Flow<ChatStreamChunk>

    // 测试连接
    suspend fun testConnection(): Result<Boolean>
}

data class ApiConfig(
    val endpoint: String,      // e.g. "https://api.openai.com/v1"
    val apiKey: String,
    val model: String,         // e.g. "gpt-4o"
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f
)
```

### 3.2 IMcpClient（MCP 模块对外接口）

```kotlin
interface IMcpClient {
    // 连接管理
    suspend fun connect(config: McpServerConfig): Result<McpServerInfo>
    suspend fun disconnect(serverId: String)
    fun isConnected(serverId: String): Boolean

    // 工具
    suspend fun listTools(serverId: String): Result<List<McpTool>>
    suspend fun callTool(serverId: String, toolName: String, arguments: Map<String, Any>): Result<McpToolResult>

    // 资源
    suspend fun listResources(serverId: String): Result<List<McpResource>>
    suspend fun readResource(serverId: String, uri: String): Result<McpResourceContent>

    // 提示模板
    suspend fun listPrompts(serverId: String): Result<List<McpPrompt>>
    suspend fun getPrompt(serverId: String, name: String, arguments: Map<String, String>? = null): Result<McpPromptResult>

    // 事件流
    val events: Flow<McpEvent>
}

data class McpServerConfig(
    val id: String,
    val name: String,
    val endpoint: String,      // e.g. "https://example.com/mcp"
    val headers: Map<String, String> = emptyMap()  // 自定义认证头
)
```

### 3.3 核心数据模型

```kotlin
// 聊天消息
data class ChatMessage(
    val role: MessageRole,     // SYSTEM, USER, ASSISTANT, TOOL
    val content: String,
    val toolCallId: String? = null,
    val toolCalls: List<ToolCall>? = null
)

enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String      // JSON string
)

// MCP 工具
data class McpTool(
    val name: String,
    val title: String?,
    val description: String?,
    val inputSchema: JsonObject
)

data class McpToolResult(
    val content: List<McpContent>,
    val isError: Boolean
)

// MCP 资源
data class McpResource(
    val uri: String,
    val name: String,
    val title: String?,
    val description: String?,
    val mimeType: String?
)

data class McpResourceContent(
    val uri: String,
    val mimeType: String?,
    val text: String?,
    val blob: String?          // base64
)

// MCP 提示模板
data class McpPrompt(
    val name: String,
    val title: String?,
    val description: String?,
    val arguments: List<McpPromptArgument>?
)

data class McpPromptResult(
    val description: String?,
    val messages: List<McpPromptMessage>
)

// MCP 事件
sealed class McpEvent {
    data class ToolsListChanged(val serverId: String) : McpEvent()
    data class ResourcesListChanged(val serverId: String) : McpEvent()
    data class ResourceUpdated(val serverId: String, val uri: String) : McpEvent()
    data class ServerDisconnected(val serverId: String) : McpEvent()
}
```

---

## 4. API 模块设计

### OpenAI 兼容 API

采用 OpenAI ChatCompletion 格式，兼容所有 OpenAI API 兼容的服务端：

- **Endpoint**: `{base_url}/chat/completions`
- **Auth**: `Authorization: Bearer {api_key}`
- **流式**: SSE 格式，`data: {json}\n\n`，结束标记 `data: [DONE]`

### 请求格式

```json
{
  "model": "gpt-4o",
  "messages": [{"role": "user", "content": "Hello"}],
  "stream": true,
  "tools": [{"type": "function", "function": {"name": "get_weather", "parameters": {...}}}]
}
```

### 流式响应解析

SSE 事件中的 `delta` 字段逐步拼接为完整响应：
- `choices[0].delta.content` → 增量文本
- `choices[0].delta.tool_calls` → 增量工具调用

---

## 5. MCP 模块设计

### 传输层：Streamable HTTP

基于 MCP 2025-06-18 规范的 Streamable HTTP 传输：

| 操作 | HTTP方法 | 说明 |
|------|---------|------|
| 发送消息 | POST | 所有 JSON-RPC 消息通过 POST 发送 |
| 监听通知 | GET | 可选，打开 SSE 流接收服务端推送 |
| 关闭会话 | DELETE | 终止 MCP 会话 |

**关键 HTTP 头**：
- `Accept: application/json, text/event-stream` — 客户端必须声明
- `Mcp-Session-Id: {id}` — 初始化后必须携带
- `MCP-Protocol-Version: 2025-06-18` — 初始化后必须携带

### 连接生命周期

```
UNINITIALIZED → (发送 initialize) → INITIALIZING → (发送 notifications/initialized) → OPERATION → (关闭连接) → SHUTDOWN
```

1. **initialize**: 客户端发送协议版本、能力声明、客户端信息
2. **服务端响应**: 返回协议版本、能力声明、服务端信息
3. **notifications/initialized**: 客户端通知初始化完成
4. **正常操作**: tools/call, resources/read 等
5. **关闭**: HTTP DELETE 或直接断开

### 工具调用流程

```
1. tools/list → 获取可用工具列表
2. 构造工具调用参数
3. tools/call → 执行工具
4. 返回结果（content 数组，支持 text/image/audio/resource）
```

### 错误处理

| 错误类型 | 处理方式 |
|---------|---------|
| 协议错误 | JSON-RPC error (code -326xx) |
| 工具执行错误 | `isError: true` in result |
| 连接断开 | HTTP 404 → 重新初始化 |
| 超时 | 发送取消通知，停止等待 |

---

## 6. GUI 模块设计

### 页面结构

```
MainActivity
├── MainScreen (Scaffold + NavigationBar)
│   ├── ChatScreen        # 聊天页面
│   ├── ApiConfigScreen   # API 配置
│   └── McpConfigScreen   # MCP 配置
```

### UI 设计规范

- **Material 3** 主题，Dynamic Color
- **无模糊效果**：不使用 `blur()`, `BlurredContent` 等
- **无多余动画**：仅使用必要的页面切换过渡，不使用复杂动画
- **无半透明叠加**：背景使用纯色，不用半透明
- 组件使用标准 Material 3 组件：`TopAppBar`, `NavigationBar`, `Card`, `FilledTextField` 等

### 聊天界面

- 顶部：当前模型名称 + MCP 状态指示
- 中部：消息列表（LazyColumn），区分用户/AI/工具消息
- 底部：输入框 + 发送按钮
- 流式响应：逐字追加显示

---

## 7. 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Kotlin 2.2.10 |
| UI | Jetpack Compose + Material 3 |
| 网络 | OkHttp + Retrofit (API), OkHttp (MCP raw HTTP) |
| 序列化 | Kotlinx Serialization (JSON-RPC, SSE) |
| 异步 | Kotlin Coroutines + Flow |
| 依赖注入 | 手动 DI（避免重框架，保持简洁） |
| 数据存储 | SharedPreferences / DataStore (配置) |
| 最低 SDK | 28 (Android 9) |
| 目标 SDK | 36 |

### 新增依赖

```kotlin
// core 模块
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

// api 模块
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")

// mcp 模块
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// app 模块
implementation("androidx.navigation:navigation-compose:2.8.0")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
```

---

## 8. 开发优先级

1. **先通 API 模块** → 能跑通基本对话
2. **再通 MCP 模块** → 能连接 MCP 服务器调用工具
3. **最后集成 GUI** → 完整交互体验

每个模块独立开发、独立测试，最后通过 core 模块的接口集成。
