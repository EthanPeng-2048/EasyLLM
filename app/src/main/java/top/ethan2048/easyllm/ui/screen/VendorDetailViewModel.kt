package top.ethan2048.easyllm.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.ethan2048.easyllm.core.domain.model.Model
import top.ethan2048.easyllm.core.domain.model.ModelConfig
import top.ethan2048.easyllm.core.domain.model.Vendor
import top.ethan2048.easyllm.data.AppRepository
import java.util.UUID

data class VendorDetailUiState(
    val vendor: Vendor? = null,
    val showVendorEditDialog: Boolean = false,
    val showModelConfigDialog: Boolean = false,
    val editingModelConfig: ModelConfig? = null,
    val availableModels: List<Model> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelLoadError: String? = null
)

class VendorDetailViewModel(
    private val vendorId: String,
    private val repository: AppRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VendorDetailUiState())
    val uiState: StateFlow<VendorDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val vendor = repository.getVendor(vendorId)
        _uiState.value = _uiState.value.copy(vendor = vendor)
    }

    fun showVendorEditDialog() {
        _uiState.value = _uiState.value.copy(showVendorEditDialog = true)
    }

    fun dismissVendorEditDialog() {
        _uiState.value = _uiState.value.copy(showVendorEditDialog = false)
    }

    fun saveVendor(name: String, endpoint: String, apiKey: String) {
        val currentVendor = _uiState.value.vendor ?: return
        val updatedVendor = currentVendor.copy(
            name = name,
            endpoint = endpoint.trimEnd('/'),
            apiKey = apiKey
        )
        repository.updateVendor(updatedVendor)
        refresh()
        dismissVendorEditDialog()
    }

    fun showModelConfigDialog(modelConfig: ModelConfig? = null) {
        _uiState.value = _uiState.value.copy(
            showModelConfigDialog = true,
            editingModelConfig = modelConfig,
            availableModels = emptyList(),
            modelLoadError = null
        )
        
        // 自动加载模型列表
        val vendor = _uiState.value.vendor ?: return
        if (vendor.endpoint.isNotBlank() && vendor.apiKey.isNotBlank()) {
            loadAvailableModels(vendor.endpoint, vendor.apiKey)
        }
    }

    fun dismissModelConfigDialog() {
        _uiState.value = _uiState.value.copy(
            showModelConfigDialog = false,
            editingModelConfig = null,
            availableModels = emptyList(),
            modelLoadError = null
        )
    }

    fun loadAvailableModels(endpoint: String, apiKey: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingModels = true,
                modelLoadError = null
            )
            
            repository.getModelsFromEndpoint(endpoint, apiKey).fold(
                onSuccess = { models ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingModels = false,
                        availableModels = models
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingModels = false,
                        modelLoadError = error.message
                    )
                }
            )
        }
    }

    fun saveModelConfig(
        name: String,
        model: String,
        temperature: Float,
        topK: Int,
        topP: Float,
        maxTokens: Int,
        contextLength: Int
    ) {
        val vendor = _uiState.value.vendor ?: return
        val existing = _uiState.value.editingModelConfig
        
        val modelConfig = if (existing != null) {
            existing.copy(
                name = name,
                model = model,
                temperature = temperature,
                topK = topK,
                topP = topP,
                maxTokens = maxTokens,
                contextLength = contextLength
            )
        } else {
            ModelConfig(
                id = UUID.randomUUID().toString(),
                name = name,
                model = model,
                temperature = temperature,
                topK = topK,
                topP = topP,
                maxTokens = maxTokens,
                contextLength = contextLength
            )
        }

        if (existing != null) {
            repository.updateModelConfig(vendor.id, modelConfig)
        } else {
            repository.addModelConfig(vendor.id, modelConfig)
        }
        
        refresh()
        dismissModelConfigDialog()
    }

    fun deleteModelConfig(modelConfigId: String) {
        val vendor = _uiState.value.vendor ?: return
        repository.deleteModelConfig(vendor.id, modelConfigId)
        refresh()
    }

    fun setActiveModelConfig(modelConfigId: String) {
        repository.setActiveModelConfig(modelConfigId)
        repository.setActiveVendor(vendorId)
    }

    class Factory(
        private val vendorId: String,
        private val repository: AppRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VendorDetailViewModel(vendorId, repository) as T
        }
    }
}
