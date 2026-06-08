package com.mapsupervision.domain.model

/**
 * Nhật ký công việc hằng ngày (DailyLog) ghi nhận tiến độ hoạt động tại công trường.
 * 
 * @property id Mã định danh duy nhất của bản ghi nhật ký.
 * @property projectId Mã dự án quản lý.
 * @property workItem Tên hạng mục công việc được thực hiện (ví dụ: rải cáp, đổ bê tông móng).
 * @property manpower Số lượng nhân sự/nhân công tham gia thực hiện hạng mục này trong ngày.
 * @property note Ghi chú chi tiết hoặc báo cáo khó khăn phát sinh tại công trường.
 * @property createdAtEpochMs Dấu thời gian tạo nhật ký (Epoch Milliseconds) để sắp xếp theo dòng thời gian (timeline).
 */
data class DailyLog(
    val id: String,
    val projectId: String,
    val workItem: String,
    val manpower: Int,
    val note: String,
    val createdAtEpochMs: Long,
    val weather: String = "",
    val temperature: Double = 0.0,
    val nodeCode: String? = null,
    val dateEpochDay: Long = 0L,
    val volume: Double = 0.0,
    val unit: String = "",
    val categoryName: String = ""
)
