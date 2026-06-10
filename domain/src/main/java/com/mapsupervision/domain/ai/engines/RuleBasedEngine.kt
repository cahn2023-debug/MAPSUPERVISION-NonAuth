package com.mapsupervision.domain.ai.engines

import com.mapsupervision.domain.ai.AiCapability
import com.mapsupervision.domain.ai.AiCapability.TIMELINE_SUMMARY
import com.mapsupervision.domain.ai.AiDecisionSource
import com.mapsupervision.domain.ai.AiEngine
import com.mapsupervision.domain.ai.AiEngineInterface
import com.mapsupervision.domain.ai.AiPayload
import com.mapsupervision.domain.ai.AiResult
import com.mapsupervision.domain.ai.ChatAssistantPayload
import com.mapsupervision.domain.ai.ChatAssistantResult
import com.mapsupervision.domain.ai.ChatActionParser
import com.mapsupervision.domain.ai.DiscrepancyCheckPayload
import com.mapsupervision.domain.ai.DiscrepancyResult
import com.mapsupervision.domain.ai.ImportMappingPayload
import com.mapsupervision.domain.ai.ImportMappingResult
import com.mapsupervision.domain.ai.NoteSummarizationPayload
import com.mapsupervision.domain.ai.NoteSummarizationResult
import com.mapsupervision.domain.ai.TaskRecommendationPayload
import com.mapsupervision.domain.ai.TaskRecommendationResult
import com.mapsupervision.domain.ai.OpsRecommendationPayload
import com.mapsupervision.domain.ai.OpsRecommendationResult
import com.mapsupervision.domain.ai.PhotoQualityPayload
import com.mapsupervision.domain.ai.PhotoQualityResult
import com.mapsupervision.domain.ai.ReportDraftPayload
import com.mapsupervision.domain.ai.ReportDraftResult
import com.mapsupervision.domain.ai.TimelineSummaryPayload
import com.mapsupervision.domain.ai.TimelineSummaryResult

/**
 * Rule-based AI engine - always available, lowest resource usage
 * Used as final fallback when other engines are unavailable
 */
class RuleBasedEngine : AiEngineInterface {
    override val engineType = AiEngine.RULE_BASED
    override val priority = 0 // Lowest priority
    
    override fun canHandle(capability: AiCapability): Boolean = true
    
    override suspend fun isAvailable(): Boolean = true
    
    override suspend fun execute(payload: AiPayload): AiResult {
        return when (payload) {
            is ChatAssistantPayload -> fallbackChat(payload)
            is ImportMappingPayload -> fallbackImportMapping(payload)
            is DiscrepancyCheckPayload -> fallbackDiscrepancies(payload)
            is TimelineSummaryPayload -> fallbackTimeline(payload)
            is PhotoQualityPayload -> fallbackPhotoQuality(payload)
            is ReportDraftPayload -> fallbackReportDraft(payload)
            is OpsRecommendationPayload -> fallbackOps(payload)
            is NoteSummarizationPayload -> fallbackNoteSummarize(payload)
            is TaskRecommendationPayload -> fallbackTaskRecommend(payload)
            else -> throw IllegalArgumentException("Unsupported payload: ${payload::class.simpleName}")
        }
    }
    
    override fun getResourceUsageScore(): Int = 5 // Very low resource usage
    
    private fun fallbackImportMapping(payload: ImportMappingPayload): ImportMappingResult {
        return ImportMappingHelper.suggestMapping(payload.headers)
    }
    
    private fun fallbackDiscrepancies(payload: DiscrepancyCheckPayload): DiscrepancyResult {
        val issues = payload.rows.mapNotNull { row ->
            when {
                row.distanceMeters > 50.0 -> "Node ${row.code}: lệch tọa độ ${row.distanceMeters.toInt()}m."
                row.incomingContractor.isNotBlank() && row.existingContractor.isNotBlank() &&
                    !row.incomingContractor.equals(row.existingContractor, ignoreCase = true) ->
                    "Node ${row.code}: khác nhà thầu '${row.incomingContractor}' vs '${row.existingContractor}'."
                else -> null
            }
        }
        val actions = if (issues.isEmpty()) {
            listOf("Không phát hiện sai lệch đáng kể.")
        } else {
            listOf("Rà soát thủ công các node có sai lệch.", "Ưu tiên xác minh node lệch tọa độ > 50m.")
        }
        return DiscrepancyResult(issues = issues, recommendedActions = actions)
    }
    
    private fun fallbackTimeline(payload: TimelineSummaryPayload): TimelineSummaryResult {
        val total = payload.progress.size
        val delayedList = payload.progress.filter { it.delayed }
        val delayedCount = delayedList.size
        val avgProgress = if (payload.progress.isEmpty()) 0f else payload.progress.map { it.actual }.average().toFloat()

        val summary = "Hệ thống ghi nhận tổng số $total trạm hạ tầng. Tiến độ thực tế trung bình đạt ${"%.2f".format(avgProgress)}%. Hiện có $delayedCount trạm thi công chậm đang bị trễ hạn so với kế hoạch ban đầu."

        val highlights = buildList {
            if (delayedCount > 0) {
                add("Cảnh báo: Có $delayedCount trạm hạ tầng đang thi công chậm so với tiến độ kế hoạch.")
                val sampleCodes = delayedList.take(3).map { it.nodeCode }
                add("Một số trạm trễ tiêu biểu: ${sampleCodes.joinToString(", ")}")
            } else {
                add("Tiến độ đạt yêu cầu: 100% các trạm đang thi công đúng hoặc vượt tiến độ kế hoạch.")
            }
            if (payload.logs.isNotEmpty()) {
                add("Ghi nhận hoạt động: Có ${payload.logs.size} nhật ký công việc được cập nhật hôm nay.")
                val latestLog = payload.logs.maxByOrNull { it.createdAtEpochMs }
                latestLog?.let { add("Hoạt động mới nhất: Hạng mục \"${it.workItem}\", chi tiết ghi nhận: \"${it.note.take(60)}...\"") }
            }
            if (payload.photoCount > 0) {
                add("Đối chiếu hình ảnh: Có ${payload.photoCount} ảnh hiện trường đã được tải lên và gắn định vị GPS thành công.")
            } else {
                add("Thiếu dữ liệu: Chưa có ảnh hiện trường để đối chiếu trực quan (Khuyến nghị bổ sung ảnh thực địa).")
            }
        }

        val actions = buildList {
            if (delayedCount > 0) {
                add("Yêu cầu đội ngũ thi công báo cáo nguyên nhân và lập biện pháp bù tiến độ cho $delayedCount trạm trễ hạn.")
                add("Tăng cường giám sát tại hiện trường đối với các điểm nóng.")
            } else {
                add("Duy trì tốc độ thi công hiện tại và tiếp tục cập nhật nhật ký hàng ngày.")
            }
            if (payload.photoCount == 0) {
                add("Yêu cầu kỹ sư chụp ảnh nghiệm thu có kèm đóng dấu GPS của các trạm đang triển khai.")
            }
        }

        return TimelineSummaryResult(summary = summary, issueHighlights = highlights, recommendedActions = actions)
    }
    
    private fun fallbackPhotoQuality(payload: PhotoQualityPayload): PhotoQualityResult {
        val issues = mutableListOf<String>()
        var score = 100
        
        if (payload.latitude == null || payload.longitude == null) {
            issues += "Ảnh không đính kèm tọa độ GPS (Thiếu tag Exif/Vị trí)."
            score -= 30
        }
        if (payload.objectCode.isBlank()) {
            issues += "Không nhận diện được mã đối tượng/nút hạ tầng gắn kèm."
            score -= 20
        }
        if (payload.engineer.isBlank() || payload.engineer.lowercase() == "unknown") {
            issues += "Thông tin kỹ sư giám sát thực địa chưa rõ ràng."
            score -= 10
        }
        
        val shouldRetake = score < 70
        val recommendation = when {
            score >= 90 -> "Chất lượng thông tin ảnh hoàn hảo, sẵn sàng xuất báo cáo."
            score >= 70 -> "Ảnh đạt yêu cầu cơ bản nhưng cần bổ sung thêm thông tin định vị nếu có thể."
            else -> "Cảnh báo: Ảnh không đủ điều kiện pháp lý nghiệm thu. Bắt buộc phải chụp lại bằng camera tích hợp GPS của MapSupervision."
        }
        
        return PhotoQualityResult(score = score, issues = issues, recommendation = recommendation, shouldRetake = shouldRetake)
    }
    
    private fun fallbackReportDraft(payload: ReportDraftPayload): ReportDraftResult {
        val summary = buildString {
            append("BÁO CÁO GIÁM SÁT HẠ TẦNG DỰ ÁN\n")
            append("- Mã dự án: ${payload.projectId}\n")
            append("- Tổng số hạng mục/nút: ${payload.totalNodes} nút kỹ thuật\n")
            append("- Số nút đang thực hiện: ${payload.inProgressNodes} nút\n")
            append("- Tỷ lệ trễ hạn: ${"%.1f".format((payload.delayedNodes.toFloat() / payload.totalNodes.coerceAtLeast(1)) * 100)}% (${payload.delayedNodes}/${payload.totalNodes} nút)\n")
            append("- Tiến độ trung bình thực tế: ${"%.1f".format(payload.avgActualProgress)}%\n")
            if (payload.totalPhotos > 0) {
                append("- Dữ liệu hình ảnh: Có ${payload.totalPhotos} ảnh hiện trường tại nút: ${payload.photoNodesSummary}.\n\n")
            } else {
                append("- Dữ liệu hình ảnh: Chưa có ảnh hiện trường tại các nút.\n\n")
            }
            if (payload.nodesSummary.isNotBlank()) {
                append("[TÌNH HÌNH THI CÔNG CÁC NÚT GIAO & TUYẾN CÁP]:\n")
                append(payload.nodesSummary)
                append("\n\n")
            }
            if (payload.contractorsSummary.isNotBlank()) {
                append("[ĐÁNH GIÁ TIẾN ĐỘ NHÀ THẦU]:\n")
                append(payload.contractorsSummary)
            }
        }

        val risk = buildString {
            if (payload.notesSummary.isNotBlank()) {
                append("[TỔNG HỢP KHÓ KHĂN & VƯỚNG MẮC THỰC ĐỊA]:\n")
                append(payload.notesSummary)
            } else if (payload.delayedNodes > 0) {
                append("RỦI RO TIẾN ĐỘ NGHIÊM TRỌNG:\n")
                append("- Có ${payload.delayedNodes} nút hạ tầng thi công chậm tiến độ kế hoạch. Rủi ro này có thể làm trì hoãn tiến độ nghiệm thu bàn giao của toàn dự án.\n")
                append("- Khuyến nghị đưa nhóm ${payload.delayedNodes} nút này vào danh sách 'Điểm nóng cần giải quyết khẩn cấp'.")
            } else {
                append("RỦI RO THẤP:\n")
                append("- Toàn bộ dự án đang bám sát tiến độ đề ra. Chưa phát hiện rủi ro chậm tiến độ nghiêm trọng tại các nút.")
            }
        }

        val actions = buildList {
            if (payload.delayedNodes > 0) {
                add("Ưu tiên tập trung nhân lực giải quyết dứt điểm các nút trễ hạn (${payload.delayedNodes} nút).")
                add("Tổ chức cuộc họp khẩn với các nhà thầu phụ chịu trách nhiệm cho các hạng mục trễ tiến độ.")
                add("Yêu cầu báo cáo tiến độ chi tiết 2 lần/ngày đối với các điểm nóng.")
            } else {
                add("Tiếp tục kế hoạch giám sát tuần tra định kỳ.")
                add("Lên kế hoạch chuẩn bị nghiệm thu cuốn chiếu cho các phân đoạn đạt 100% tiến độ thực tế.")
            }
            if (payload.totalPhotos < payload.totalNodes) {
                add("Tăng cường chụp ảnh hiện trạng tại các nút có tiến độ thay đổi để hoàn thiện hồ sơ nghiệm thu.")
            }
            add("Kế hoạch: Bố trí lực lượng nghiệm thu kỹ thuật và đấu nối ngay khi có mặt bằng sạch.")
        }
        return ReportDraftResult(executiveSummary = summary, riskSection = risk, recommendedActions = actions)
    }
    
    
    private fun fallbackOps(payload: OpsRecommendationPayload): OpsRecommendationResult {
        val actions = mutableListOf<String>()
        var priority = 1

        if (payload.delayedNodes > 0) {
            actions += "Cảnh báo đỏ: Xử lý gấp ${payload.delayedNodes} điểm thi công trễ tiến độ kế hoạch."
            priority = 2
        }
        if (payload.importWarnings > 0) {
            actions += "Rà soát sai lệch: Khắc phục ${payload.importWarnings} cảnh báo sai lệch dữ liệu KML/Excel đã import."
            if (payload.importWarnings > 5) priority = priority.coerceAtLeast(2)
        }
        if (payload.completionPercent < 50f) {
            actions += "Cảnh báo tiến độ: Tỷ lệ hoàn thành dự án rất thấp (${"%.1f".format(payload.completionPercent)}%), cần huy động thêm nguồn lực thi công."
            priority = 3
        } else if (payload.completionPercent < 80f) {
            actions += "Tiến độ trung bình đạt mức khá (${"%.1f".format(payload.completionPercent)}%), cần đẩy nhanh hoàn thiện các hạng mục phụ trợ."
        }

        if (payload.delayedNodes > 10) priority = 3

        if (actions.isEmpty()) {
            actions += "Vận hành xuất sắc: Dự án đạt chuẩn tiến độ, dữ liệu sạch, duy trì tần suất báo cáo hàng tuần."
        }

        return OpsRecommendationResult(prioritizedActions = actions, priority = priority)
    }
    private fun fallbackNoteSummarize(payload: NoteSummarizationPayload): NoteSummarizationResult {
        val notes = payload.notes
        if (notes.isEmpty()) {
            return NoteSummarizationResult("Chưa có ghi chú nào được ghi nhận cho đối tượng này.")
        }
        val cleanNotes = notes.map { it.trim() }.filter { it.isNotBlank() }
        val summary = if (cleanNotes.size == 1) {
            "Ghi nhận thực địa: \"${cleanNotes.first()}\""
        } else {
            "Tổng hợp ${cleanNotes.size} ghi chú tại hiện trường:\n" +
                cleanNotes.mapIndexed { idx, note -> "${idx + 1}. $note" }.joinToString("\n")
        }
        return NoteSummarizationResult(summary)
    }

    private fun fallbackTaskRecommend(payload: TaskRecommendationPayload): TaskRecommendationResult {
        val notes = payload.notes.map { it.lowercase() }
        val tasks = mutableListOf<String>()

        // Highly refined regex / keyword matching in Vietnamese for technical precision
        val hasLeakOrWater = notes.any { it.contains("rò rỉ") || it.contains("nước") || it.contains("thấm") || it.contains("ngập") || it.contains("bẩn") }
        val hasConcreteIssue = notes.any { it.contains("bê tông") || it.contains("nứt") || it.contains("lún") || it.contains("móng") || it.contains("đứt") || it.contains("vỡ") }
        val hasCableOrElectric = notes.any { it.contains("cáp") || it.contains("điện") || it.contains("tiếp địa") || it.contains("đường dây") || it.contains("nối") || it.contains("đầu nối") }
        val hasMissingParts = notes.any { it.contains("thiếu") || it.contains("chưa lắp") || it.contains("chưa có") || it.contains("chờ vật tư") }
        val hasSurveyNeed = notes.any { it.contains("đo đạc") || it.contains("khảo sát") || it.contains("lệch") || it.contains("sai số") || it.contains("tọa độ") }

        if (hasLeakOrWater) {
            tasks += "Xử lý chống thấm và bơm hút nước/nạo vét bùn đất đọng tại hố ga"
            tasks += "Kiểm tra hệ thống thoát nước xung quanh tránh ngập úng cục bộ"
        }
        if (hasConcreteIssue) {
            tasks += "Kiểm tra chất lượng mác bê tông móng và gia cố kết cấu nứt lún"
            tasks += "Lập biên bản đánh giá khả năng chịu lực kết cấu hiện trường"
        }
        if (hasCableOrElectric) {
            tasks += "Đo thông mạch đường cáp và kiểm tra thông số điện trở hệ thống tiếp địa"
            tasks += "Thực hiện bọc cách điện bảo vệ mối nối cáp ngầm"
        }
        if (hasMissingParts) {
            tasks += "Đôn đốc nhà thầu cung cấp vật tư lắp ráp hoàn thiện hạng mục còn thiếu"
            tasks += "Lắp đặt bổ sung phụ kiện định vị và cố định cáp/thiết bị"
        }
        if (hasSurveyNeed) {
            tasks += "Sử dụng máy RTK định vị lại tọa độ chính xác của tâm hố ga/tuyến cáp"
            tasks += "Hiệu chỉnh sai lệch bản đồ thiết kế (KML) so với đo đạc thực tế"
        }

        if (tasks.isEmpty()) {
            tasks += "Nghiệm thu kỹ thuật nội bộ toàn bộ hạng mục"
            tasks += "Vệ sinh công nghiệp khu vực thi công và bàn giao mặt bằng sạch"
        }

        val existingLower = payload.existingTasks.map { it.lowercase().trim() }
        val filteredTasks = tasks.filter { t -> existingLower.none { it == t.lowercase().trim() } }

        return TaskRecommendationResult(suggestedTasks = filteredTasks.distinct())
    }

    private fun fallbackChat(payload: ChatAssistantPayload): ChatAssistantResult {
        return ChatActionParser.parse(payload.message, payload.contextSummary, payload.selectedNodeCode, payload.normalizationContext)
    }
}

object ImportMappingHelper {
    fun normalize(text: String): String {
        val temp = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        val pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        return pattern.matcher(temp).replaceAll("")
            .replace('\u0111', 'd')
            .replace('\u0110', 'd')
            .lowercase(java.util.Locale.US).trim()
    }

    fun suggestMapping(headers: List<String>): ImportMappingResult {
        var nodeCode = ""
        var lat = ""
        var lon = ""
        var contractor = ""
        val items = ArrayList<String>()

        for (h in headers) {
            val hn = normalize(h)
            val isContractorKeyword = hn.contains("contractor") || hn.contains("nha thau") || hn.contains("don vi") || hn.contains("team") || hn.contains("to thi cong") || hn.contains("doi thi cong")
            val isCoordinateKeyword = hn == "lat" || hn == "latitude" || hn.contains("vi do") || hn == "y" ||
                hn == "lon" || hn == "lng" || hn == "longitude" || hn.contains("kinh do") || hn == "x"

            if (nodeCode.isEmpty() && !isContractorKeyword && !isCoordinateKeyword && (
                    hn.contains("code") || hn.contains("ma") || hn.contains("name") || hn.contains("ten") ||
                    hn.contains("node") || hn.contains("tram") || hn.contains("vi tri") || hn.contains("position") ||
                    hn.contains("placemark") || hn.contains("nut") || hn.contains("tuyen") || hn.contains("doi tuong")
                )) {
                nodeCode = h
            } else if (lat.isEmpty() && (hn == "lat" || hn == "latitude" || hn.contains("vi do") || hn == "y")) {
                lat = h
            } else if (lon.isEmpty() && (hn == "lon" || hn == "lng" || hn == "longitude" || hn.contains("kinh do") || hn == "x")) {
                lon = h
            } else if (contractor.isEmpty() && isContractorKeyword) {
                contractor = h
            } else if (hn.contains("vat tu") || hn.contains("khoi luong") || hn.contains("qty") || hn.contains("cap") ||
                hn.contains("may") || hn.contains("thiet bi") || hn.contains("camera") || hn.contains("tu") ||
                hn.contains("dao") || hn.contains("dat") || hn.contains("be tong") || hn.contains("item") ||
                hn.contains("hang muc") || hn.contains("work")) {
                items.add(h)
            }
        }

        val requiresReview = nodeCode.isEmpty() || (lat.isEmpty() && lon.isEmpty())
        return ImportMappingResult(
            nodeCodeColumn = nodeCode,
            latitudeColumn = lat,
            longitudeColumn = lon,
            contractorColumn = contractor,
            itemColumns = items,
            requiresManualReview = requiresReview
        )
    }
}
