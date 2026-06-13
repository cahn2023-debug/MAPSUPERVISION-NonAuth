package com.mapsupervision.domain.model

/**
 * Quản lý chi tiết tiến độ vật tư (MaterialProgress) cho từng nút kỹ thuật.
 * Giúp theo dõi lượng vật liệu (như chiều dài cáp rải, số camera lắp đặt) thực tế so với kế hoạch.
 * 
 * @property id Mã định danh duy nhất của bản ghi tiến độ vật tư.
 * @property projectId Mã dự án quản lý.
 * @property nodeCode Mã ký hiệu nút hạ tầng tương ứng (liên kết với [GisNode.code]).
 * @property materialName Tên loại vật tư, thiết bị (ví dụ: Cáp quang 24FO, Camera Bullet IP).
 * @property plannedQty Số lượng vật tư theo hồ sơ thiết kế kế hoạch đặt ra.
 * @property actualQty Số lượng vật tư thực tế đã lắp ráp hoặc lắp đặt tại thực địa.
 * @property updatedAtEpochMs Dấu thời gian cập nhật tiến độ vật tư lần cuối cùng.
 */
data class MaterialProgress(
    val id: String,
    val projectId: String,
    val nodeCode: String,
    val materialName: String,
    val plannedQty: Float,
    val actualQty: Float,
    val updatedAtEpochMs: Long,
    val unit: String = ""
)

