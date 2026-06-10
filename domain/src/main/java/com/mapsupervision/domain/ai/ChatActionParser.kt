package com.mapsupervision.domain.ai

import com.mapsupervision.domain.model.TaskStatus

object ChatActionParser {

    fun parse(
        message: String,
        contextSummary: String = "",
        selectedNodeCode: String? = null,
        normalizationContext: String = ""
    ): ChatAssistantResult {
        return parseInternal(message, contextSummary, selectedNodeCode, normalizationContext)
    }

    fun parseLlmResponse(
        llmResponse: String,
        selectedNodeCode: String? = null,
        normalizationContext: String = ""
    ): ChatAssistantResult {
        val actionRegex = Regex("""\[ACTION:\s*([A-Z_]+)\s*(.*?)\]""", RegexOption.IGNORE_CASE)
        val match = actionRegex.find(llmResponse) ?: return ChatAssistantResult(
            answer = llmResponse,
            writeDisposition = WriteDisposition.REJECT,
            confidence = ChatConfidenceScore(50, 0, 0, 40, false)
        )
        val actionName = match.groupValues[1].uppercase()
        val paramsContent = match.groupValues[2]
        val cleanAnswer = llmResponse.replace(match.value, "").trim()
        val params = parseParams(paramsContent)

        return processPipeline(
            actionName = actionName,
            params = params,
            message = llmResponse,
            cleanAnswer = cleanAnswer.ifBlank { "Đã xử lý yêu cầu." },
            selectedNodeCode = selectedNodeCode,
            normalizationContext = normalizationContext
        )
    }

    private fun parseInternal(
        message: String,
        contextSummary: String,
        selectedNodeCode: String?,
        normalizationContext: String
    ): ChatAssistantResult {
        val normalized = normalizeText(message)
        val actionName: String
        val params = mutableMapOf<String, String>()

        when {
            normalized.contains("nhiem vu") || normalized.contains("task") || normalized.contains("viec can lam") -> {
                actionName = "ADD_TASK"
                params["objectCode"] = extractObjectCode(message, selectedNodeCode).orEmpty()
                params["title"] = extractAfter(message, listOf("nhiem vu", "task", "viec can lam"))
            }
            normalized.contains("ghi chu") || normalized.contains("note") -> {
                actionName = "ADD_NOTE"
                params["objectCode"] = extractObjectCode(message, selectedNodeCode).orEmpty()
                params["content"] = extractAfter(message, listOf("ghi chu", "note"))
            }
            normalized.contains("khoi luong") || normalized.contains("volume") || normalized.contains("vat tu") -> {
                actionName = "UPDATE_MATERIAL_OR_VOLUME_PROGRESS"
                params["nodeCode"] = extractNodeCode(message, selectedNodeCode).orEmpty()
                params["actualQty"] = extractNumber(message, listOf("actual", "thuc te", "khoi luong", "volume"))?.toString() ?: "0"
                params["plannedQty"] = extractNumber(message, listOf("planned", "ke hoach"))?.toString() ?: ""
                params["materialName"] = extractAfter(message, listOf("hang muc", "vat tu", "material", "khoi luong"))
            }
            normalized.contains("cap nhat") || normalized.contains("progress") || normalized.contains("tien do") -> {
                actionName = "UPDATE_CONSTRUCTION_PROGRESS"
                params["nodeCode"] = extractNodeCode(message, selectedNodeCode).orEmpty()
                params["planned"] = extractNumber(message, listOf("planned", "ke hoach"))?.toString() ?: ""
                params["actual"] = extractNumber(message, listOf("actual", "thuc te"))?.toString() ?: ""
            }
            normalized.contains("nhat ky") || normalized.contains("daily log") -> {
                actionName = "ADD_DAILY_LOG"
                params["nodeCode"] = extractNodeCode(message, selectedNodeCode).orEmpty()
                params["workItem"] = extractAfter(message, listOf("work", "cong viec", "nhat ky"))
                params["manpower"] = extractInt(message, listOf("manpower", "nhan luc", "so nguoi"))?.toString() ?: "1"
                params["note"] = message.trim()
                params["volume"] = extractNumber(message, listOf("volume", "khoi luong", "thuc te", "actual"))?.toString() ?: "0"
                params["categoryName"] = extractAfter(message, listOf("category", "hang muc", "vat tu"))
            }
            normalized.contains("bao cao") || normalized.contains("report") -> {
                actionName = "SAVE_REPORT_DRAFT"
                params["title"] = "Báo cáo giám sát tự động"
            }
            else -> {
                return ChatAssistantResult(
                    answer = buildFallbackAnswer(message, contextSummary),
                    confidence = ChatConfidenceScore(30, 0, 0, 30, false),
                    writeDisposition = WriteDisposition.REJECT
                )
            }
        }

        return processPipeline(
            actionName = actionName,
            params = params,
            message = message,
            cleanAnswer = "Mình đã chuẩn bị sẵn phiếu xử lý.",
            selectedNodeCode = selectedNodeCode,
            normalizationContext = normalizationContext
        )
    }

    private fun processPipeline(
        actionName: String,
        params: Map<String, String>,
        message: String,
        cleanAnswer: String,
        selectedNodeCode: String?,
        normalizationContext: String
    ): ChatAssistantResult {
        val resolvedRefs = NormalizationRefsParser.parse(normalizationContext)

        // 1. Intent Detection
        val intent = runCatching { ChatActionType.valueOf(actionName) }.getOrNull()
        val intentConfidence = if (intent != null) 95 else 0

        if (intent == null) {
            return ChatAssistantResult(
                answer = cleanAnswer.ifBlank { message },
                writeDisposition = WriteDisposition.REJECT,
                confidence = ChatConfidenceScore(intentConfidence, 0, 0, 0, false)
            )
        }

        // 2 & 3. Entity Extraction and Canonicalization
        val (nodeCode, nodeConf) = ChatDictionaryResolver.resolveNode(
            message = message,
            selectedNodeCode = params["nodeCode"] ?: params["objectCode"] ?: params["matchedNodeCode"] ?: selectedNodeCode,
            refs = resolvedRefs
        )

        val (routeCode, routeConf) = ChatDictionaryResolver.resolveRoute(
            message = message,
            selectedRouteCode = params["routeCode"] ?: resolvedRefs.routeCode,
            refs = resolvedRefs
        )

        val (categoryName, categoryUnit, categoryConf) = ChatDictionaryResolver.resolveCategory(
            message = message,
            rawCategoryName = params["categoryName"] ?: params["materialName"] ?: params["workItem"],
            refs = resolvedRefs
        )

        // 4. Scoring & Write Policy
        var isDataComplete = false
        val missingFields = mutableListOf<String>()
        val locationConf = maxOf(nodeConf, routeConf)

        var draftJson = ""
        var pendingAction: ChatPendingAction? = null

        when (intent) {
            ChatActionType.UPDATE_CONSTRUCTION_PROGRESS -> {
                val planned = params["planned"]?.toFloatOrNull()
                val actual = params["actual"]?.toFloatOrNull()
                if (nodeCode != null && planned != null && actual != null) {
                    isDataComplete = true
                } else {
                    if (nodeCode == null) missingFields.add("nodeCode")
                    if (planned == null) missingFields.add("planned")
                    if (actual == null) missingFields.add("actual")
                }
                val draft = ConstructionProgressDraft(nodeCode = nodeCode.orEmpty(), planned = planned ?: 100f, actual = actual ?: 0f)
                draftJson = """{"nodeCode":"${escapeJson(draft.nodeCode)}","planned":${draft.planned},"actual":${draft.actual}}"""
                pendingAction = ChatPendingAction(
                    type = intent,
                    title = "Cập nhật thi công node ${draft.nodeCode}",
                    draftJson = draftJson,
                    constructionProgress = draft
                )
            }
            ChatActionType.UPDATE_MATERIAL_OR_VOLUME_PROGRESS -> {
                val planned = params["plannedQty"]?.toFloatOrNull() ?: params["planned"]?.toFloatOrNull()
                val actual = params["actualQty"]?.toFloatOrNull() ?: params["actual"]?.toFloatOrNull() ?: params["volume"]?.toFloatOrNull()
                val matName = categoryName?.takeIf { it.isNotBlank() } ?: params["materialName"] ?: ""
                
                if (nodeCode != null && matName.isNotBlank() && actual != null) {
                    isDataComplete = true
                } else {
                    if (nodeCode == null) missingFields.add("nodeCode")
                    if (matName.isBlank()) missingFields.add("materialName")
                    if (actual == null) missingFields.add("actualQty")
                }

                val draft = MaterialOrVolumeProgressDraft(
                    nodeCode = nodeCode.orEmpty(),
                    materialName = matName,
                    actualQty = actual ?: 0f,
                    plannedQty = planned,
                    unit = categoryUnit.orEmpty()
                )
                val plannedJson = if (planned == null) "null" else planned.toString()
                draftJson = """{"nodeCode":"${escapeJson(draft.nodeCode)}","materialName":"${escapeJson(draft.materialName)}","actualQty":${draft.actualQty},"plannedQty":$plannedJson,"unit":"${escapeJson(draft.unit)}"}"""
                pendingAction = ChatPendingAction(
                    type = intent,
                    title = "Cập nhật khối lượng ${draft.materialName} node ${draft.nodeCode}",
                    draftJson = draftJson,
                    materialOrVolumeProgress = draft
                )
            }
            ChatActionType.ADD_DAILY_LOG -> {
                val workItem = params["workItem"]?.takeIf { it.isNotBlank() } ?: categoryName ?: "Nhật ký thi công"
                val manpower = params["manpower"]?.toIntOrNull() ?: 1
                val note = params["note"] ?: cleanAnswer
                val weather = params["weather"]?.takeIf { it.isNotBlank() } ?: ChatDictionaryResolver.resolveWeather(message)
                val temp = params["temperature"]?.toDoubleOrNull() ?: 0.0
                val vol = params["volume"]?.toDoubleOrNull() ?: params["actualQty"]?.toDoubleOrNull()

                if (nodeCode != null && workItem.isNotBlank()) {
                    isDataComplete = true
                } else {
                    if (nodeCode == null) missingFields.add("nodeCode")
                    if (workItem.isBlank()) missingFields.add("workItem")
                }

                val draft = DailyLogDraft(
                    workItem = workItem,
                    manpower = manpower,
                    note = note,
                    weather = weather,
                    temperature = temp,
                    nodeCode = nodeCode,
                    dateEpochDay = System.currentTimeMillis() / (24 * 60 * 60 * 1000L),
                    volume = vol ?: 0.0,
                    unit = categoryUnit.orEmpty(),
                    categoryName = categoryName.orEmpty()
                )
                draftJson = buildDailyLogJson(draft)
                pendingAction = ChatPendingAction(
                    type = intent,
                    title = "Thêm nhật ký thi công ${draft.nodeCode ?: ""}",
                    draftJson = draftJson,
                    dailyLog = draft
                )
            }
            ChatActionType.UPDATE_SITE_PHOTO -> {
                val photoId = params["photoId"] ?: ""
                if (photoId.isNotBlank()) {
                    isDataComplete = true
                } else {
                    missingFields.add("photoId")
                }
                val draft = SitePhotoUpdateDraft(
                    photoId = photoId,
                    tagCodesCsv = params["tagCodesCsv"] ?: "",
                    matchedNodeCode = nodeCode,
                    latitude = params["latitude"]?.toDoubleOrNull(),
                    longitude = params["longitude"]?.toDoubleOrNull()
                )
                draftJson = buildSitePhotoJson(draft)
                pendingAction = ChatPendingAction(
                    type = intent,
                    title = "Cập nhật ảnh chụp ${draft.photoId}",
                    draftJson = draftJson,
                    sitePhotoUpdate = draft
                )
            }
            ChatActionType.SAVE_REPORT_DRAFT -> {
                val recActions = params["recommendedActions"]?.split("|")?.map { it.trim() } ?: emptyList()
                val projId = params["projectId"] ?: resolvedRefs.nodeCode ?: ""
                if (projId.isNotBlank()) {
                    isDataComplete = true
                } else {
                    missingFields.add("projectId")
                }
                val draft = ReportDraftDbSaveDraft(
                    projectId = projId,
                    title = params["title"] ?: "Báo cáo giám sát tự động",
                    executiveSummary = params["executiveSummary"] ?: cleanAnswer,
                    riskSection = params["riskSection"] ?: "",
                    recommendedActions = recActions
                )
                val actionsJsonArray = recActions.joinToString(prefix = "[", postfix = "]") { "\"${escapeJson(it)}\"" }
                draftJson = """{"projectId":"${escapeJson(draft.projectId)}","title":"${escapeJson(draft.title)}","executiveSummary":"${escapeJson(draft.executiveSummary)}","riskSection":"${escapeJson(draft.riskSection)}","recommendedActions":$actionsJsonArray}"""
                pendingAction = ChatPendingAction(
                    type = intent,
                    title = "Lưu nháp báo cáo dự án",
                    draftJson = draftJson,
                    reportDraftSave = draft
                )
            }
            ChatActionType.ADD_NOTE -> {
                val content = params["content"] ?: cleanAnswer
                if (nodeCode != null && content.isNotBlank()) {
                    isDataComplete = true
                } else {
                    if (nodeCode == null) missingFields.add("objectCode")
                    if (content.isBlank()) missingFields.add("content")
                }
                val draft = NoteDraft(objectCode = nodeCode.orEmpty(), content = content)
                draftJson = """{"objectCode":"${escapeJson(draft.objectCode)}","content":"${escapeJson(draft.content)}"}"""
                pendingAction = ChatPendingAction(
                    type = intent,
                    title = "Thêm ghi chú cho ${draft.objectCode}",
                    draftJson = draftJson,
                    noteDraft = draft
                )
            }
            ChatActionType.ADD_TASK -> {
                val title = params["title"] ?: cleanAnswer.ifBlank { "Nhiệm vụ mới" }
                if (nodeCode != null && title.isNotBlank()) {
                    isDataComplete = true
                } else {
                    if (nodeCode == null) missingFields.add("objectCode")
                    if (title.isBlank()) missingFields.add("title")
                }
                val desc = params["description"] ?: ""
                val status = runCatching { TaskStatus.valueOf(params["status"] ?: "TODO") }.getOrDefault(TaskStatus.TODO)
                val draft = TaskDraft(objectCode = nodeCode.orEmpty(), title = title, description = desc, status = status)
                draftJson = buildTaskJson(draft)
                pendingAction = ChatPendingAction(
                    type = intent,
                    title = "Thêm nhiệm vụ cho ${draft.objectCode}",
                    draftJson = draftJson,
                    taskDraft = draft
                )
            }
        }

        // Calculate overall confidence with completeness penalty based on intent requirements
        var overallConfidence = when (intent) {
            ChatActionType.SAVE_REPORT_DRAFT -> intentConfidence
            ChatActionType.UPDATE_SITE_PHOTO -> (intentConfidence * 2 + locationConf) / 3
            ChatActionType.UPDATE_CONSTRUCTION_PROGRESS,
            ChatActionType.UPDATE_MATERIAL_OR_VOLUME_PROGRESS,
            ChatActionType.ADD_DAILY_LOG,
            ChatActionType.ADD_NOTE,
            ChatActionType.ADD_TASK -> {
                if (locationConf > 0) {
                    (intentConfidence + locationConf) / 2
                } else {
                    intentConfidence - 15
                }
            }
        }

        if (!isDataComplete) {
            val isCriticalMissing = when (intent) {
                ChatActionType.UPDATE_CONSTRUCTION_PROGRESS -> {
                    nodeCode == null || (params["planned"]?.toFloatOrNull() == null && params["actual"]?.toFloatOrNull() == null)
                }
                ChatActionType.UPDATE_MATERIAL_OR_VOLUME_PROGRESS -> {
                    nodeCode == null || (params["actualQty"]?.toFloatOrNull() ?: params["actual"]?.toFloatOrNull() ?: params["volume"]?.toFloatOrNull()) == null
                }
                ChatActionType.ADD_DAILY_LOG -> {
                    false
                }
                ChatActionType.ADD_NOTE -> {
                    nodeCode == null || (params["content"] ?: cleanAnswer).isBlank()
                }
                ChatActionType.ADD_TASK -> {
                    nodeCode == null || (params["title"] ?: cleanAnswer).isBlank()
                }
                else -> {
                    missingFields.isNotEmpty()
                }
            }
            val penalty = if (isCriticalMissing) 50 else 20
            overallConfidence = (overallConfidence - penalty).coerceAtLeast(0)
        }

        // Determine WriteDisposition
        val writeDisposition = when {
            overallConfidence >= 85 && isDataComplete -> WriteDisposition.AUTO_SAVE
            overallConfidence >= 50 -> WriteDisposition.REQUIRE_CONFIRMATION
            else -> WriteDisposition.REJECT
        }

        val friendlyMissing = missingFields.map {
            when (it) {
                "nodeCode", "objectCode" -> "trạm/node"
                "planned", "plannedQty" -> "kế hoạch"
                "actual", "actualQty", "volume" -> "thực tế/khối lượng"
                "materialName", "workItem" -> "hạng mục"
                "content" -> "nội dung"
                "title" -> "tiêu đề"
                else -> it
            }
        }
        val friendlyMissingStr = friendlyMissing.joinToString(", ")

        val finalAnswer = when (writeDisposition) {
            WriteDisposition.REJECT -> {
                if (friendlyMissing.isNotEmpty()) {
                    "Yêu cầu chưa đủ thông tin chi tiết. Vui lòng cung cấp thêm: $friendlyMissingStr."
                } else {
                    "Mình chưa hiểu rõ yêu cầu. Bạn có thể nói rõ hơn được không?"
                }
            }
            WriteDisposition.REQUIRE_CONFIRMATION -> {
                if (friendlyMissing.isNotEmpty()) {
                    "Mình đã tạo phiếu nháp nhưng còn thiếu thông tin: $friendlyMissingStr. Bạn vui lòng bổ sung trong phiếu xác nhận."
                } else {
                    "Mình đã chuẩn bị phiếu nháp. Bạn vui lòng xác nhận."
                }
            }
            else -> cleanAnswer.ifBlank { "Mình đã chuẩn bị sẵn phiếu xử lý." }
        }

        val finalPendingAction = if (writeDisposition == WriteDisposition.REJECT) null else pendingAction

        val resolvedEntities = buildMap {
            if (nodeCode != null && nodeCode.isNotBlank()) put("node", nodeCode)
            if (routeCode != null && routeCode.isNotBlank()) put("route", routeCode)
            if (categoryName != null && categoryName.isNotBlank()) put("category", categoryName)
            if (categoryUnit != null && categoryUnit.isNotBlank()) put("unit", categoryUnit)
        }

        return ChatAssistantResult(
            answer = finalAnswer,
            suggestedAction = intent.name,
            draftJson = draftJson,
            pendingAction = finalPendingAction,
            confidence = ChatConfidenceScore(
                intentConfidence = intentConfidence,
                locationConfidence = locationConf,
                categoryConfidence = categoryConf,
                overallConfidence = overallConfidence,
                isDataComplete = isDataComplete
            ),
            missingFields = missingFields,
            resolvedEntities = resolvedEntities,
            writeDisposition = writeDisposition
        )
    }


    private fun extractNodeCode(message: String, selectedNodeCode: String?): String? {
        val regex = Regex("""(?:node|tram|trạm)\s*([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
        return regex.find(message)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: selectedNodeCode
    }

    private fun extractObjectCode(message: String, selectedNodeCode: String?): String? {
        val regex = Regex("""(?:object|objectcode|node|tram|trạm|doi tuong|đối tượng)\s*([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
        return regex.find(message)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: selectedNodeCode
    }

    private fun extractNumber(message: String, keys: List<String>): Float? {
        val lower = normalizeText(message)
        keys.forEach { key ->
            val regex = Regex("""$key\s*[:=]?\s*([0-9]+(?:[.,][0-9]+)?)""", RegexOption.IGNORE_CASE)
            regex.find(lower)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toFloatOrNull()?.let { return it }
        }
        return null
    }

    private fun extractInt(message: String, keys: List<String>): Int? {
        val lower = normalizeText(message)
        keys.forEach { key ->
            val regex = Regex("""$key\s*[:=]?\s*([0-9]+)""", RegexOption.IGNORE_CASE)
            regex.find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun extractAfter(message: String, keys: List<String>): String {
        val lower = normalizeText(message)
        keys.forEach { key ->
            val index = lower.indexOf(key)
            if (index >= 0) {
                return message.substring(index + key.length).trim().trim(':', '=', '-', ' ')
            }
        }
        return ""
    }

    private fun normalizeText(value: String): String {
        return ChatDictionaryResolver.normalize(value)
    }

    private fun parseParams(paramsContent: String): Map<String, String> {
        val paramRegex = Regex("""(\w+)\s*=\s*(?:"([^"]*)"|'([^']*)'|(\S+))""")
        val params = mutableMapOf<String, String>()
        paramRegex.findAll(paramsContent).forEach { m ->
            val key = m.groupValues[1]
            val value = m.groupValues[2].takeIf { it.isNotEmpty() }
                ?: m.groupValues[3].takeIf { it.isNotEmpty() }
                ?: m.groupValues[4]
            params[key] = value
        }
        return params
    }

    private fun buildDailyLogJson(draft: DailyLogDraft): String {
        return buildString {
            append("{")
            append("\"workItem\":\"").append(escapeJson(draft.workItem)).append("\",")
            append("\"manpower\":").append(draft.manpower).append(",")
            append("\"note\":\"").append(escapeJson(draft.note)).append("\",")
            append("\"weather\":\"").append(escapeJson(draft.weather)).append("\",")
            append("\"temperature\":").append(draft.temperature).append(",")
            append("\"nodeCode\":")
            if (draft.nodeCode == null) append("null") else append("\"").append(escapeJson(draft.nodeCode)).append("\"")
            append(",")
            append("\"dateEpochDay\":").append(draft.dateEpochDay).append(",")
            append("\"volume\":").append(draft.volume).append(",")
            append("\"unit\":\"").append(escapeJson(draft.unit)).append("\",")
            append("\"categoryName\":\"").append(escapeJson(draft.categoryName)).append("\"")
            append("}")
        }
    }

    private fun buildSitePhotoJson(draft: SitePhotoUpdateDraft): String {
        return """{"photoId":"${escapeJson(draft.photoId)}","tagCodesCsv":"${escapeJson(draft.tagCodesCsv)}","matchedNodeCode":${if (draft.matchedNodeCode == null) "null" else "\"${escapeJson(draft.matchedNodeCode)}\""},"latitude":${draft.latitude ?: "null"},"longitude":${draft.longitude ?: "null"}}"""
    }

    private fun buildTaskJson(draft: TaskDraft): String {
        return """{"objectCode":"${escapeJson(draft.objectCode)}","title":"${escapeJson(draft.title)}","description":"${escapeJson(draft.description)}","status":"${draft.status}"}"""
    }

    private fun buildFallbackAnswer(message: String, contextSummary: String): String {
        return when {
            message.contains("bao cao", ignoreCase = true) || message.contains("báo cáo", ignoreCase = true) ->
                "Mình có thể hỗ trợ tóm tắt báo cáo từ dữ liệu hiện có."
            contextSummary.isNotBlank() ->
                "Mình đã nhận nội dung. Nếu bạn muốn cập nhật thi công, hãy nêu rõ node, kế hoạch và thực tế."
            else -> "Mình đã nhận nội dung. Bạn có thể hỏi về tiến độ, nhật ký hoặc báo cáo."
        }
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }
}
