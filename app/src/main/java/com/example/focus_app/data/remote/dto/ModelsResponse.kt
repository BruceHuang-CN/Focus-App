package com.example.focus_app.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * GET /models 的响应体，兼容 DeepSeek 与 OpenAI 兼容端点。
 */
data class ModelsResponse(
    @SerializedName("object") val obj: String? = null,
    val data: List<ModelInfo> = emptyList()
)

data class ModelInfo(
    val id: String,
    val owned_by: String? = null,
    val created: Long? = null
)
