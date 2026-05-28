package top.ethan2048.easyllm.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.ethan2048.easyllm.core.domain.model.Vendor
import top.ethan2048.easyllm.data.AppRepository
import java.util.UUID

data class ApiConfigUiState(
    val vendors: List<Vendor> = emptyList(),
    val showDialog: Boolean = false,
    val editingVendor: Vendor? = null
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
        _uiState.value = _uiState.value.copy(vendors = repository.vendors)
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(
            showDialog = true,
            editingVendor = null
        )
    }

    fun showEditDialog(vendor: Vendor) {
        _uiState.value = _uiState.value.copy(
            showDialog = true,
            editingVendor = vendor
        )
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(
            showDialog = false,
            editingVendor = null
        )
    }

    fun saveVendor(
        name: String,
        endpoint: String,
        apiKey: String
    ) {
        val existing = _uiState.value.editingVendor
        val vendor = if (existing != null) {
            existing.copy(
                name = name,
                endpoint = endpoint.trimEnd('/'),
                apiKey = apiKey
            )
        } else {
            Vendor(
                id = UUID.randomUUID().toString(),
                name = name,
                endpoint = endpoint.trimEnd('/'),
                apiKey = apiKey,
                models = emptyList()
            )
        }

        if (existing != null) {
            repository.updateVendor(vendor)
        } else {
            repository.addVendor(vendor)
        }
        dismissDialog()
        refresh()
    }

    fun deleteVendor(vendorId: String) {
        repository.deleteVendor(vendorId)
        refresh()
    }

    class Factory(private val repository: AppRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ApiConfigViewModel(repository) as T
        }
    }
}
