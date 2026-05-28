package top.ethan2048.easyllm.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.ethan2048.easyllm.api.OpenAiClient
import top.ethan2048.easyllm.core.domain.model.*
import top.ethan2048.easyllm.core.domain.repository.VendorRepository

/**
 * 供应商和模型配置仓库实现
 */
class VendorRepositoryImpl(context: Context) : VendorRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("easyllm_configs", Context.MODE_PRIVATE)

    private val _vendors = mutableListOf<Vendor>()
    override val vendors: List<Vendor> get() = _vendors.toList()

    override var activeVendorId: String? = null
        private set

    override var activeModelConfigId: String? = null
        private set

    init {
        loadVendors()
    }

    override fun addVendor(vendor: Vendor) {
        _vendors.add(vendor)
        saveVendors()
    }

    override fun updateVendor(vendor: Vendor) {
        val index = _vendors.indexOfFirst { it.id == vendor.id }
        if (index >= 0) {
            _vendors[index] = vendor
            saveVendors()
        }
    }

    override fun deleteVendor(vendorId: String) {
        _vendors.removeAll { it.id == vendorId }
        if (activeVendorId == vendorId) {
            activeVendorId = _vendors.firstOrNull()?.id
            activeModelConfigId = null
        }
        saveVendors()
    }

    override fun setActiveVendor(vendorId: String) {
        activeVendorId = vendorId
        prefs.edit().putString("active_vendor_id", vendorId).apply()
    }

    override fun getVendor(vendorId: String): Vendor? {
        return _vendors.find { it.id == vendorId }
    }

    override fun addModelConfig(vendorId: String, modelConfig: ModelConfig) {
        val index = _vendors.indexOfFirst { it.id == vendorId }
        if (index >= 0) {
            val vendor = _vendors[index]
            val updatedModels = vendor.models + modelConfig
            _vendors[index] = vendor.copy(models = updatedModels)
            saveVendors()
        }
    }

    override fun updateModelConfig(vendorId: String, modelConfig: ModelConfig) {
        val vendorIndex = _vendors.indexOfFirst { it.id == vendorId }
        if (vendorIndex >= 0) {
            val vendor = _vendors[vendorIndex]
            val modelIndex = vendor.models.indexOfFirst { it.id == modelConfig.id }
            if (modelIndex >= 0) {
                val updatedModels = vendor.models.toMutableList()
                updatedModels[modelIndex] = modelConfig
                _vendors[vendorIndex] = vendor.copy(models = updatedModels)
                saveVendors()
            }
        }
    }

    override fun deleteModelConfig(vendorId: String, modelConfigId: String) {
        val vendorIndex = _vendors.indexOfFirst { it.id == vendorId }
        if (vendorIndex >= 0) {
            val vendor = _vendors[vendorIndex]
            val updatedModels = vendor.models.filter { it.id != modelConfigId }
            _vendors[vendorIndex] = vendor.copy(models = updatedModels)
            if (activeModelConfigId == modelConfigId) {
                activeModelConfigId = null
            }
            saveVendors()
        }
    }

    override fun setActiveModelConfig(modelConfigId: String) {
        activeModelConfigId = modelConfigId
        prefs.edit().putString("active_model_config_id", modelConfigId).apply()
    }

    override suspend fun fetchModelsFromEndpoint(endpoint: String, apiKey: String): Result<List<Model>> = withContext(Dispatchers.IO) {
        runCatching {
            val tempConfig = ApiConfig(
                id = "temp",
                name = "temp",
                endpoint = endpoint.trimEnd('/'),
                apiKey = apiKey,
                model = ""
            )
            val api = OpenAiClient(tempConfig)
            val result = api.getModels()
            result.getOrThrow()
        }
    }

    private fun loadVendors() {
        try {
            val vendorJson = prefs.getString("vendors", "[]") ?: "[]"
            _vendors.clear()
            _vendors.addAll(json.decodeFromString<List<Vendor>>(vendorJson))

            activeVendorId = prefs.getString("active_vendor_id", _vendors.firstOrNull()?.id)
            activeModelConfigId = prefs.getString("active_model_config_id", null)
        } catch (_: Exception) {
            // 解析失败时使用空列表
        }
    }

    private fun saveVendors() {
        prefs.edit().putString("vendors", json.encodeToString(_vendors)).apply()
        prefs.edit().putString("active_vendor_id", activeVendorId).apply()
        prefs.edit().putString("active_model_config_id", activeModelConfigId).apply()
    }
}
