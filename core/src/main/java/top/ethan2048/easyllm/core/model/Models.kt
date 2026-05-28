package top.ethan2048.easyllm.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelListResponse(
    @SerialName("object") val objectType: String? = null,
    val data: List<Model> = emptyList(),
    val error: ApiError? = null
)

@Serializable
data class Model(
    val id: String,
    @SerialName("object") val objectType: String? = null,
    val created: Long? = null,
    @SerialName("owned_by") val ownedBy: String? = null,
    val permission: List<ModelPermission>? = null
)

@Serializable
data class ModelPermission(
    val id: String? = null,
    @SerialName("object") val objectType: String? = null,
    val created: Long? = null,
    @SerialName("allow_create_engine") val allowCreateEngine: Boolean? = null,
    @SerialName("allow_sampling") val allowSampling: Boolean? = null,
    @SerialName("allow_logprobs") val allowLogprobs: Boolean? = null,
    @SerialName("allow_search_indices") val allowSearchIndices: Boolean? = null,
    @SerialName("allow_view") val allowView: Boolean? = null,
    @SerialName("allow_fine_tuning") val allowFineTuning: Boolean? = null,
    val organization: String? = null,
    val group: String? = null,
    @SerialName("is_blocking") val isBlocking: Boolean? = null
)
