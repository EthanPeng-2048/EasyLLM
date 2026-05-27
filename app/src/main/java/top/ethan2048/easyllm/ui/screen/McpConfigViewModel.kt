package top.ethan2048.easyllm.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.ethan2048.easyllm.core.model.McpServerConfig
import top.ethan2048.easyllm.core.model.McpServerInfo
import top.ethan2048.easyllm.data.AppRepository
import java.util.UUID

data class McpConfigUiState(
    val configs: List<McpServerConfig> = emptyList(),
    val showDialog: Boolean = false,
    val editingConfig: McpServerConfig? = null,
    val connections: Map<String, McpConnectionState> = emptyMap()
)

data class McpConnectionState(
    val isConnected: Boolean,
    val serverInfo: McpServerInfo? = null,
    val isConnecting: Boolean = false,
    val error: String? = null
)

class McpConfigViewModel(
    private val repository: AppRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(McpConfigUiState())
    val uiState: StateFlow<McpConfigUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val mcpClient = repository.getMcpClient()
        val connections = repository.mcpConfigs.associate { config ->
            config.id to McpConnectionState(
                isConnected = mcpClient.isConnected(config.id),
                serverInfo = mcpClient.getServerInfo(config.id)
            )
        }
        _uiState.value = _uiState.value.copy(
            configs = repository.mcpConfigs,
            connections = connections
        )
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(
            showDialog = true,
            editingConfig = null
        )
    }

    fun showEditDialog(config: McpServerConfig) {
        _uiState.value = _uiState.value.copy(
            showDialog = true,
            editingConfig = config
        )
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(
            showDialog = false,
            editingConfig = null
        )
    }

    fun saveConfig(
        name: String,
        endpoint: String,
        headersJson: String
    ) {
        val existing = _uiState.value.editingConfig

        val headers = if (headersJson.isBlank()) {
            emptyMap()
        } else {
            try {
                // 简单解析 key:value 格式，每行一个
                headersJson.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .associate { line ->
                        val parts = line.split(":", limit = 2)
                        parts[0].trim() to parts.getOrElse(1) { "" }.trim()
                    }
            } catch (_: Exception) {
                emptyMap()
            }
        }

        val config = if (existing != null) {
            existing.copy(
                name = name,
                endpoint = endpoint.trimEnd('/'),
                headers = headers
            )
        } else {
            McpServerConfig(
                id = UUID.randomUUID().toString(),
                name = name,
                endpoint = endpoint.trimEnd('/'),
                headers = headers
            )
        }

        if (existing != null) {
            repository.updateMcpConfig(config)
        } else {
            repository.addMcpConfig(config)
        }
        dismissDialog()
        refresh()
    }

    fun deleteConfig(configId: String) {
        viewModelScope.launch {
            val mcpClient = repository.getMcpClient()
            if (mcpClient.isConnected(configId)) {
                mcpClient.disconnect(configId)
            }
        }
        repository.deleteMcpConfig(configId)
        refresh()
    }

    fun connect(configId: String) {
        val config = repository.mcpConfigs.find { it.id == configId } ?: return
        viewModelScope.launch {
            updateConnectionState(configId) { it.copy(isConnecting = true, error = null) }
            val mcpClient = repository.getMcpClient()
            val result = mcpClient.connect(config)
            result.onSuccess { info ->
                updateConnectionState(configId) {
                    it.copy(isConnected = true, serverInfo = info, isConnecting = false)
                }
            }.onFailure { e ->
                updateConnectionState(configId) {
                    it.copy(isConnecting = false, error = e.message ?: "连接失败")
                }
            }
        }
    }

    fun disconnect(configId: String) {
        viewModelScope.launch {
            val mcpClient = repository.getMcpClient()
            mcpClient.disconnect(configId)
            updateConnectionState(configId) { it.copy(isConnected = false, serverInfo = null) }
        }
    }

    private fun updateConnectionState(
        configId: String,
        updater: (McpConnectionState) -> McpConnectionState
    ) {
        val current = _uiState.value.connections.toMutableMap()
        current[configId] = updater(current[configId] ?: McpConnectionState(isConnected = false))
        _uiState.value = _uiState.value.copy(connections = current)
    }

    class Factory(private val repository: AppRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return McpConfigViewModel(repository) as T
        }
    }
}
