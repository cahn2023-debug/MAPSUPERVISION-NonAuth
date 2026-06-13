package com.mapsupervision.domain.model

/**
 * Lựa chọn hạng mục mẫu cho dropdown.
 */
data class WorkTemplateOption(
    val name: String,
    val unit: String,
    val source: String // "Vật tư / Thiết bị" hoặc "Hạng mục công việc chung"
)
