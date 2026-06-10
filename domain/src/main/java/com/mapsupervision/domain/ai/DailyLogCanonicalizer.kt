package com.mapsupervision.domain.ai

object DailyLogCanonicalizer {
    fun canonicalize(
        params: Map<String, String>,
        message: String,
        normalizationContext: String,
        selectedNodeCode: String?
    ): DailyLogDraft {
        val refs = NormalizationRefsParser.parse(normalizationContext)
        
        // Use ChatDictionaryResolver for fuzzy node resolution
        val resolvedNode = ChatDictionaryResolver.resolveNode(
            message = message,
            selectedNodeCode = params["nodeCode"] ?: selectedNodeCode,
            refs = refs
        ).first
        
        val workItem = params["workItem"]?.takeIf { it.isNotBlank() } ?: "Nhật ký thi công"
        val manpower = params["manpower"]?.toIntOrNull() ?: 1
        val note = params["note"] ?: message.trim()
        
        // Fuzzy match category
        val (categoryName, unit, _) = ChatDictionaryResolver.resolveCategory(
            message = message,
            rawCategoryName = params["categoryName"],
            refs = refs
        )
        
        val weather = params["weather"]?.takeIf { it.isNotBlank() } ?: ChatDictionaryResolver.resolveWeather(message)
        val temperature = params["temperature"]?.toDoubleOrNull() ?: 0.0
        val volume = params["volume"]?.toDoubleOrNull() ?: 0.0

        return DailyLogDraft(
            workItem = workItem,
            manpower = manpower,
            note = note,
            weather = weather,
            temperature = temperature,
            nodeCode = resolvedNode,
            dateEpochDay = System.currentTimeMillis() / (24 * 60 * 60 * 1000L),
            volume = volume,
            unit = unit.orEmpty(),
            categoryName = categoryName.orEmpty()
        )
    }
}
