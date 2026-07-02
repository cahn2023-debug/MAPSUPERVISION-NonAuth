package com.mapsupervision.domain.model

/**
 * Lựa chọn hạng mục mẫu cho dropdown.
 */
data class WorkTemplateOption(
    val name: String,
    val unit: String,
    val source: String // "Công việc / khối lượng công việc" hoặc "Hạng mục công việc chung"
)
