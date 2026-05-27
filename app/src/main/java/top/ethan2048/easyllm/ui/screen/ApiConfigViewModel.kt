package top.ethan2048.easyllm.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.ethan2048.easyllm.core.model.ApiConfig
import top.ethan2048.easyllm.data.AppRepository
import java.util.UUID

data class ApiConfigUiState(
    val configs: List<ApiConfig> = emptyList(),
    val showDialog: Boolean = false,
    val editingConfig: ApiConfig? = null,
    val testResult: String? = null
)

class ApiConfigViewModel(
    private val repository: AppRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiConfigUiState())
    val uiState: StateFlow<ApiConfigUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(configs = repository.apiConfigs)
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(
            showDialog = true,
            editingConfig = null,
            testResult = null
        )
    }

    fun showEditDialog(config: ApiConfig) {
        _uiState.value = _uiState.value.copy(
            showDialog = true,
            editingConfig = config,
            testResult = null
        )
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(
            showDialog = false,
            editingConfig = null,
            testResult = null
        )
    }

    fun saveConfig(
        name: String,
        endpoint: String,
        apiKey: String,
        model: String,
        maxTokens: Int,
        temperature: Float
    ) {
        val existing = _uiState.value.editingConfig
        val config = if (existing != null) {
            existing.copy(
                name = name,
                endpoint = endpoint.trimEnd('/'),
                apiKey = apiKey,
                model = model,
                maxTokens = maxTokens,
                temperature = temperature
            )
        } else {
            ApiConfig(
                id = UUID.randomUUID().toString(),
                name = name,
                endpoint = endpoint.trimEnd('/'),
                apiKey = apiKey,
                model = model,
                maxTokens = maxTokens,
                temperature = temperature
            )
        }

        if (existing != null) {
            repository.updateApiConfig(config)
        } else {
            repository.addApiConfig(config)
        }
        dismissDialog()
        refresh()
    }

    fun deleteConfig(configId: String) {
        repository.deleteApiConfig(configId)
        refresh()
    }

    fun setActive(configId: String) {
        repository.setActiveApiConfig(configId)
        refresh()
    }

    fun testConnection(config: ApiConfig) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(testResult = "测试中...")
            val api = repository.getChatApi(config.id)
            val result = api.testConnection()
            _uiState.value = _uiState.value.copy(
                testResult = if (result.isSuccess) "连接成功 ✅" else "连接失败: ${result.exceptionOrNull()?.message}"
            )
        }
    }

    class Factory(private val repository: AppRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ApiConfigViewModel(repository) as T
        }
    }
}
