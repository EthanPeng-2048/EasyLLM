package top.ethan2048.easyllm.core.domain.repository

import kotlinx.coroutines.flow.Flow
import top.ethan2048.easyllm.core.domain.model.*

/**
 * 供应商和模型配置仓库接口
 */
interface VendorRepository {
    val vendors: List<Vendor>
    val activeVendorId: String?
    val activeModelConfigId: String?

    fun addVendor(vendor: Vendor)
    fun updateVendor(vendor: Vendor)
    fun deleteVendor(vendorId: String)
    fun setActiveVendor(vendorId: String)
    fun getVendor(vendorId: String): Vendor?

    fun addModelConfig(vendorId: String, modelConfig: ModelConfig)
    fun updateModelConfig(vendorId: String, modelConfig: ModelConfig)
    fun deleteModelConfig(vendorId: String, modelConfigId: String)
    fun setActiveModelConfig(modelConfigId: String)

    suspend fun fetchModelsFromEndpoint(endpoint: String, apiKey: String): Result<List<Model>>
}
