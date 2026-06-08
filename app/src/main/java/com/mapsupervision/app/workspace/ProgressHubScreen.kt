package com.mapsupervision.app.workspace

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mapsupervision.app.ui.theme.extendedColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressHubScreen(
    activeProjectId: String?,
    constructionProgress: List<com.mapsupervision.domain.model.NodeProgress>,
    dailyLogs: List<com.mapsupervision.domain.model.DailyLog>,
    dashboardState: com.mapsupervision.app.workspace.DashboardState,
    progressUiState: ProgressUiState,
    screenUiState: ProgressHubScreenUiState,
    workCategories: List<com.mapsupervision.domain.model.WorkCategory>,
    photos: List<com.mapsupervision.domain.model.SitePhoto> = emptyList(),
    activeProjectName: String? = null,
    onSetProgressTab: (Boolean) -> Unit,
    onUpdateGroupMode: (String) -> Unit,
    onUpdateFilterMode: (String) -> Unit,
    onSelectProgressNode: (String, String, String) -> Unit,
    onDismissProgressNodeSheet: () -> Unit,
    onUpdateCurrentMonth: (Int) -> Unit,
    onUpdateCurrentYear: (Int) -> Unit,
    onUpdateSelectedDateMillis: (Long) -> Unit,
    onUpdateWeatherSelection: (String) -> Unit,
    onUpdateCustomWeather: (String) -> Unit,
    onUpdateTemperatureInput: (String) -> Unit,
    onUpdateSelectedNodeCodeForLog: (String?) -> Unit,
    onUpdateManpowerInput: (String) -> Unit,
    onUpdateWorkItemInput: (String) -> Unit,
    onUpdateNoteInput: (String) -> Unit,
    onUpdateActualProgressInput: (String) -> Unit,
    onUpdateActualProgressChecked: (Boolean) -> Unit,
    onUpdateLogFormError: (String) -> Unit,
    onSetNodeDropdownExpanded: (Boolean) -> Unit,
    onUpdateVolumeInput: (String) -> Unit,
    onUpdateUnitInput: (String) -> Unit,
    onUpdateSelectedCategoryName: (String) -> Unit,
    onSetCategoryDropdownExpanded: (Boolean) -> Unit,
    onSetShowAddCategoryDialog: (Boolean) -> Unit,
    onUpdateNewCategoryName: (String) -> Unit,
    onUpdateNewCategoryUnit: (String) -> Unit,
    onUpdateProgressSheetPlannedInput: (String) -> Unit,
    onUpdateProgressSheetActualInput: (String) -> Unit,
    onUpdateProgressSheetValidationError: (String) -> Unit,
    onUpdateProgressSheetNote: (String) -> Unit,
    onResetLogForm: () -> Unit,
    onAddConstruction: (String, Float, Float) -> Unit,
    onAddDailyLog: (String, Int, String, String, Double, String?, Long, Double, String, String) -> Unit,
    onAddWorkCategory: (String, String) -> Unit
) {
    var isProgressSubTab by rememberSaveable { mutableStateOf(screenUiState.isProgressSubTab) } // true = Progress (Tiến độ), false = Diary (Nhật ký)
    
    var groupMode by rememberSaveable { mutableStateOf(screenUiState.groupMode) }
    var filterMode by rememberSaveable { mutableStateOf(screenUiState.filterMode) }
    val selectedNodeCodeForProgress = screenUiState.selectedNodeCodeForProgress
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Calendar & Diary state
    val todayCal = remember { Calendar.getInstance() }
    val currentMonth = screenUiState.currentMonth
    val currentYear = screenUiState.currentYear
    val selectedDateMillis = screenUiState.selectedDateMillis
    val selectedDate = remember(selectedDateMillis) {
        Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
    }

    // Weather Form State
    val weatherSelected = screenUiState.weatherSelected
    val customWeather = screenUiState.customWeather
    val temperatureInput = screenUiState.temperatureInput

    // Daily Log Form State
    var selectedNodeCodeForLog by rememberSaveable { mutableStateOf(screenUiState.selectedNodeCodeForLog) }
    var manpowerInput by rememberSaveable { mutableStateOf(screenUiState.manpowerInput) }
    var workItemInput by rememberSaveable { mutableStateOf(screenUiState.workItemInput) }
    var noteInput by rememberSaveable { mutableStateOf(screenUiState.noteInput) }
    var actualProgressInput by rememberSaveable { mutableStateOf(screenUiState.actualProgressInput) }
    var logFormError by rememberSaveable { mutableStateOf(screenUiState.logFormError) }
    val nodeDropdownExpanded = screenUiState.nodeDropdownExpanded
    
    // Custom Work Category & Volume State
    var volumeInput by rememberSaveable { mutableStateOf(screenUiState.volumeInput) }
    var unitInput by rememberSaveable { mutableStateOf(screenUiState.unitInput) }
    var selectedCategoryName by rememberSaveable { mutableStateOf(screenUiState.selectedCategoryName) }
    val categoryDropdownExpanded = screenUiState.categoryDropdownExpanded
    
    LaunchedEffect(screenUiState) {
        isProgressSubTab = screenUiState.isProgressSubTab
        groupMode = screenUiState.groupMode
        filterMode = screenUiState.filterMode
        selectedNodeCodeForLog = screenUiState.selectedNodeCodeForLog
        manpowerInput = screenUiState.manpowerInput
        workItemInput = screenUiState.workItemInput
        noteInput = screenUiState.noteInput
        actualProgressInput = screenUiState.actualProgressInput
        logFormError = screenUiState.logFormError
        volumeInput = screenUiState.volumeInput
        unitInput = screenUiState.unitInput
        selectedCategoryName = screenUiState.selectedCategoryName
    }

    val nonStructuralNodes = progressUiState.nonStructuralNodes
    val nodesMap = progressUiState.nodesByCode
    val activeNodeCodes = progressUiState.activeNodeCodes
    val criticalNodes = progressUiState.criticalNodes
    val progressByCode = progressUiState.progressByNodeCode
    val allDisplayItems = progressUiState.allDisplayItems
    val selectedNodeForProgress = remember(selectedNodeCodeForProgress, progressByCode, activeProjectId) {
        selectedNodeCodeForProgress?.let { nodeCode ->
            progressByCode[nodeCode]
                ?: com.mapsupervision.domain.model.NodeProgress(
                    id = "",
                    projectId = activeProjectId ?: "",
                    nodeCode = nodeCode,
                    planned = 0f,
                    actual = 0f,
                    remain = 0f,
                    delayed = false,
                    updatedAtEpochMs = 0L
                )
        }
    }

    val filteredProgress = remember(allDisplayItems, filterMode) {
        allDisplayItems.filter {
            when (filterMode) {
                "Delayed" -> it.delayed
                "On-time" -> !it.delayed && it.updatedAtEpochMs > 0L
                else -> true
            }
        }
    }

    val groupedProgress = remember(filteredProgress, groupMode, nodesMap) {
        when (groupMode) {
            "Nhà thầu" -> filteredProgress.groupBy { nodesMap[it.nodeCode]?.contractor?.takeIf { c -> c.isNotBlank() } ?: "Chưa phân công" }
            "Vị trí" -> filteredProgress.groupBy { it.nodeCode }
            else -> mapOf("Tất cả hạng mục" to filteredProgress)
        }
    }

    // Task 2: summary stats
    val totalNodes = nonStructuralNodes.size
    val updatedNodes = remember(constructionProgress, activeNodeCodes) {
        constructionProgress.count { it.updatedAtEpochMs > 0L && it.nodeCode.trim().uppercase() in activeNodeCodes }
    }
    val onTrackNodes = remember(constructionProgress, activeNodeCodes) {
        constructionProgress.count { !it.delayed && it.updatedAtEpochMs > 0L && it.nodeCode.trim().uppercase() in activeNodeCodes }
    }
    val delayedNodes = dashboardState.delayedCount
    
    // Average planned/actual assuming out of 100 for percentage
    val avgStats = remember(constructionProgress, activeNodeCodes) {
        val activeProgress = constructionProgress.filter { it.nodeCode.trim().uppercase() in activeNodeCodes }
        val avgPlanned = if (activeProgress.isEmpty()) 0f else activeProgress.map { it.planned }.average().toFloat()
        val avgActual = if (activeProgress.isEmpty()) 0f else activeProgress.map { it.actual }.average().toFloat()
        val planPercent = (avgPlanned / 100f).coerceIn(0f, 1f)
        val actualPercent = (avgActual / 100f).coerceIn(0f, 1f)
        Triple(avgPlanned, avgActual, planPercent to actualPercent)
    }
    val avgPlanned = avgStats.first
    val avgActual = avgStats.second
    val planPercent = avgStats.third.first
    val actualPercent = avgStats.third.second

    // Colors
    val backgroundColor = MaterialTheme.colorScheme.background
    val primaryTextColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val extendedColors = MaterialTheme.extendedColors
    val orangeColor = extendedColors.warning
    val darkOrangeColor = extendedColors.mapAccent
    val redColor = extendedColors.danger
    val lightRedBg = extendedColors.dangerSoft
    val darkBlueColor = MaterialTheme.colorScheme.primary
    val lightBlueColor = extendedColors.info
    val successColor = extendedColors.success
    val trackBlueColor = MaterialTheme.colorScheme.outline

    // Calendar computing helpers
    val monthCalendar = remember(currentMonth, currentYear) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, currentMonth)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val firstDayOfWeek = monthCalendar.get(Calendar.DAY_OF_WEEK)
    val daysInMonth = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val emptyCellsBefore = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - 2

    val calendarCells = remember(currentMonth, currentYear, emptyCellsBefore, daysInMonth) {
        buildList {
            repeat(emptyCellsBefore) { add(null) }
            for (day in 1..daysInMonth) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, currentYear)
                    set(Calendar.MONTH, currentMonth)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                add(cal)
            }
        }
    }

    fun getEpochDay(cal: Calendar): Long {
        val temp = cal.clone() as Calendar
        temp.set(Calendar.HOUR_OF_DAY, 0)
        temp.set(Calendar.MINUTE, 0)
        temp.set(Calendar.SECOND, 0)
        temp.set(Calendar.MILLISECOND, 0)
        return temp.timeInMillis / (24 * 60 * 60 * 1000)
    }

    val selectedEpoch = remember(selectedDate) { getEpochDay(selectedDate) }

    fun hasLogsOnDay(dayCal: Calendar): Boolean {
        val dayEpoch = getEpochDay(dayCal)
        return dayEpoch in progressUiState.logEpochDays
    }

    val logsForSelectedDate = remember(dailyLogs, selectedDate) {
        dailyLogs.filter { log ->
            val logCal = Calendar.getInstance().apply { timeInMillis = log.createdAtEpochMs }
            val logEpoch = getEpochDay(logCal)
            log.dateEpochDay == selectedEpoch || (log.dateEpochDay == 0L && logEpoch == selectedEpoch)
        }.sortedByDescending { it.createdAtEpochMs }
    }

    Scaffold( snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->

        LazyColumn( modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(innerPadding)
                .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (activeProjectId == null) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text( "Vui lòng tạo hoặc chọn một dự án để tiếp tục", color = primaryTextColor, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                        )
                    }
                }
                return@LazyColumn
            }

            item {
                Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text( text = "Tiến độ & Nhật ký", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = primaryTextColor
                        )
                        Text( text = activeProjectName ?: "Dự án hiện tại", fontSize = 13.sp, color = secondaryTextColor
                        )
                    }
                    OutlinedButton( onClick = {
                            coroutineScope.launch {
                                val result = ProgressPdfExporter.export( projectId = activeProjectId ?: "unknown", progress = constructionProgress, nodes = nonStructuralNodes, photos = photos
                                )
                                result.fold( onSuccess = { file ->
                                        try {
                                            val uri = FileProvider.getUriForFile( context, "${context.packageName}.fileprovider", file
                                            )
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "application/pdf")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: ActivityNotFoundException) {
                                            snackbarHostState.showSnackbar("Da luu: ${file.absolutePath}")
                                        }
                                    }, onFailure = { e ->
                                        snackbarHostState.showSnackbar(e.message ?: "Không xác định")
                                    }
                                )
                            }
                        }, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon( imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = primaryTextColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Xuất báo cáo", color = primaryTextColor, fontSize = 14.sp)
                    }
                }
            }

            // Tab Selector
            item {
                Row( modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button( onClick = { isProgressSubTab = true; onSetProgressTab(true) }, modifier = Modifier.weight(1f).height(40.dp), colors = ButtonDefaults.buttonColors( containerColor = if (isProgressSubTab) MaterialTheme.colorScheme.surface else Color.Transparent, contentColor = if (isProgressSubTab) primaryTextColor else secondaryTextColor
                        ), elevation = ButtonDefaults.buttonElevation(defaultElevation = if (isProgressSubTab) 2.dp else 0.dp), shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Outlined.Assessment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tiến độ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Button( onClick = { isProgressSubTab = false; onSetProgressTab(false) }, modifier = Modifier.weight(1f).height(40.dp), colors = ButtonDefaults.buttonColors( containerColor = if (!isProgressSubTab) MaterialTheme.colorScheme.surface else Color.Transparent, contentColor = if (!isProgressSubTab) primaryTextColor else secondaryTextColor
                        ), elevation = ButtonDefaults.buttonElevation(defaultElevation = if (!isProgressSubTab) 2.dp else 0.dp), shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Outlined.EditCalendar, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nhật ký", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            if (isProgressSubTab) {
                // ========================== PROGRESS VIEW ==========================
                item {
                    Card( modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        val statusColor = if (dashboardState.delayedCount > 0) orangeColor else successColor
                        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                            Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(statusColor))
                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text( text = "TRẠNG THÁI TIẾN ĐỘ DỰ ÁN", fontWeight = FontWeight.Bold, color = primaryTextColor, fontSize = 12.sp, letterSpacing = 0.5.sp
                                    )
                                    if (dashboardState.delayedCount > 0) {
                                        Row( modifier = Modifier
                                                .background(lightRedBg, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = redColor, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("${dashboardState.delayedCount} Chậm hạn", color = redColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Row( modifier = Modifier
                                                .background(successColor.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = successColor, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Đúng tiến độ", color = successColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Planned
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Benchmark kế hoạch", color = secondaryTextColor, fontSize = 14.sp)
                                    Text("${String.format("%.1f", avgPlanned)}%", color = secondaryTextColor, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator( progress = { planPercent }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = darkBlueColor, trackColor = MaterialTheme.colorScheme.surface
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                // Actual
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Hoàn thành thực tế", color = darkOrangeColor, fontSize = 14.sp)
                                    Text("${String.format("%.1f", avgActual)}%", color = darkOrangeColor, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator( progress = { actualPercent }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = orangeColor, trackColor = MaterialTheme.colorScheme.surface
                                )
                            }
                        }
                    }
                }

                item {
                    SummaryStatsRow( totalNodes = totalNodes, updatedNodes = updatedNodes, onTrackNodes = onTrackNodes, delayedNodes = delayedNodes, primaryTextColor = primaryTextColor, secondaryTextColor = secondaryTextColor, successColor = successColor, orangeColor = orangeColor
                    )
                }

                item {
                    Card( colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text( "CRITICAL PATH VARIANCES (ĐỘ LỆCH TIẾN ĐỘ)", fontWeight = FontWeight.Bold, color = primaryTextColor, fontSize = 12.sp, letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 12.dp)
                            )
                            if (criticalNodes.isEmpty()) {
                                Text( "Chưa có dữ liệu trễ hạn", color = secondaryTextColor, fontSize = 14.sp
                                )
                            } else {
                                criticalNodes.forEachIndexed { index, node ->
                                    val variance = node.planned - node.actual
                                    val days = estimatedDelayDays(variance)
                                    val label = nodeDisplayName(node.nodeCode, nodesMap)
                                    Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(label, color = primaryTextColor, fontSize = 14.sp)
                                        Text("-$days Ngày", color = darkOrangeColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (index < criticalNodes.lastIndex) {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Column {
                        Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Danh Sach\nHang Muc",
                                fontWeight = FontWeight.Bold,
                                color = primaryTextColor,
                                fontSize = 20.sp,
                                lineHeight = 24.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip( selected = filterMode == "All", onClick = { filterMode = "All"; onUpdateFilterMode("All") }, label = { Text("Tất cả", color = if (filterMode == "All") Color.White else primaryTextColor) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = trackBlueColor, containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)
                                )
                                FilterChip( selected = filterMode == "Delayed", onClick = { filterMode = "Delayed"; onUpdateFilterMode("Delayed") }, label = { Text("Chậm trễ", color = if (filterMode == "Delayed") Color.White else primaryTextColor) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = orangeColor, containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Nhóm theo: ", color = secondaryTextColor, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            listOf("Không nhóm", "Nhà thầu", "Vị trí").forEach { mode ->
                                FilterChip( selected = groupMode == mode, onClick = { groupMode = mode; onUpdateGroupMode(mode) }, label = { Text(mode, fontSize = 12.sp) }, modifier = Modifier.padding(end = 4.dp), shape = RoundedCornerShape(16.dp)
                                )
                            }
                        }
                    }
                }

                if (nonStructuralNodes.isEmpty()) {
                    items(5) { index ->
                        ShimmerItem( modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .padding(vertical = 4.dp)
                        )
                    }
                } else {
                    groupedProgress.forEach { (groupName, items) ->
                        item(key = "group_header_$groupName") {
                            Text( text = groupName.uppercase(), fontWeight = FontWeight.Bold, color = secondaryTextColor, fontSize = 12.sp, letterSpacing = 0.5.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        
                        if (items.isEmpty()) {
                            item(key = "group_empty_$groupName") {
                                Text("Không có dữ liệu", color = secondaryTextColor, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                            }
                        } else {
                            items( items = items, key = { it.nodeCode }
                            ) { progress ->
                                val pPlan = remember(progress.planned) { (progress.planned / 100f).coerceIn(0f, 1f) }
                                val pActual = remember(progress.actual) { (progress.actual / 100f).coerceIn(0f, 1f) }
                                InfrastructureItem( title = nodeDisplayName(progress.nodeCode, nodesMap), subtitle = progress.nodeCode, id = progress.id.take(8).uppercase(), supervisor = nodesMap[progress.nodeCode]?.contractor ?: "Chưa phân thầu", planProgress = pPlan, actualProgress = pActual, borderColor = if (progress.delayed) darkOrangeColor else successColor, planColor = if (progress.delayed) darkBlueColor else trackBlueColor, actualColor = if (progress.delayed) darkOrangeColor else lightBlueColor, isWarning = progress.delayed, isNew = progress.id.isBlank(), onClick = {
                                        val existing = progressByCode[progress.nodeCode]
                                            ?: com.mapsupervision.domain.model.NodeProgress( id = "", projectId = activeProjectId ?: "", nodeCode = progress.nodeCode, planned = 0f, actual = 0f, remain = 0f, delayed = false, updatedAtEpochMs = 0L
                                            )
                                        onSelectProgressNode(existing.nodeCode, existing.planned.toString(), existing.actual.toString())
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // ========================== DIARY / CALENDAR VIEW ==========================
                
                // Month Header & Navigation
                item {
                    Card( colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text( text = "Lịch tháng ${currentMonth + 1}, $currentYear", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryTextColor
                                )
                                Row {
                                    IconButton(onClick = {
                                        if (currentMonth == 0) {
                                            onUpdateCurrentMonth(11)
                                            onUpdateCurrentYear(currentYear - 1)
                                        } else {
                                            onUpdateCurrentMonth(currentMonth - 1)
                                        }
                                    }) {
                                        Icon(Icons.Default.ChevronLeft, contentDescription = "Hạng mục: ")
                                    }
                                    IconButton(onClick = {
                                        if (currentMonth == 11) {
                                            onUpdateCurrentMonth(0)
                                            onUpdateCurrentYear(currentYear + 1)
                                        } else {
                                            onUpdateCurrentMonth(currentMonth + 1)
                                        }
                                    }) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Tháng sau")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Day Names Header
                            Row(modifier = Modifier.fillMaxWidth()) {
                                listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN").forEach { dayName ->
                                    Text( text = dayName, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = secondaryTextColor
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Calendar Grid View (rendered as rows of 7)
                            val chunks = calendarCells.chunked(7)
                            chunks.forEach { rowDays ->
                                Row( modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    rowDays.forEach { cellCal ->
                                        if (cellCal == null) {
                                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                        } else {
                                            val isToday = cellCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                                                    cellCal.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH) &&
                                                    cellCal.get(Calendar.DAY_OF_MONTH) == todayCal.get(Calendar.DAY_OF_MONTH)
                                            
                                            val isSelected = getEpochDay(cellCal) == selectedEpoch
                                            val hasLogs = hasLogsOnDay(cellCal)

                                            Box( modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .clip(CircleShape)
                                                    .background( when {
                                                            isSelected -> orangeColor
                                                            isToday -> orangeColor.copy(alpha = 0.2f)
                                                            else -> Color.Transparent
                                                        }
                                                    )
                                                    .clickable { onUpdateSelectedDateMillis(cellCal.timeInMillis) }
                                                    .padding(4.dp), contentAlignment = Alignment.Center
                                            ) {
                                                Column( horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text( text = cellCal.get(Calendar.DAY_OF_MONTH).toString(), fontSize = 14.sp, fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal, color = when {
                                                            isSelected -> Color.White
                                                            isToday -> orangeColor
                                                            else -> primaryTextColor
                                                        }
                                                    )
                                                    if (hasLogs) {
                                                        Box( modifier = Modifier
                                                                .size(4.dp)
                                                                .clip(CircleShape)
                                                                .background(if (isSelected) Color.White else successColor)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    // If last row has less than 7 elements, pad it
                                    if (rowDays.size < 7) {
                                        repeat(7 - rowDays.size) {
                                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Selected Day Info & Weather Widget
                item {
                    val dateFormatted = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedDate.time)
                    Card( colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text( text = "Thông tin ngày: $dateFormatted", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = primaryTextColor
                                )
                                val isSelectedToday = selectedEpoch == getEpochDay(todayCal)
                                if (isSelectedToday) {
                                    Text( "Hôm nay", color = orangeColor, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier
                                            .background(orangeColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Weather section
                            Text("THỜI TIẾT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secondaryTextColor)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Weather condition chips
                            Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Nắng", "Mưa", "Nhiều mây", "Giông bão").forEach { cond ->
                                    val isSelected = weatherSelected == cond
                                    Box( modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) orangeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                                            .border(1.dp, if (isSelected) orangeColor else Color.Transparent, RoundedCornerShape(8.dp))
                                            .clickable { onUpdateWeatherSelection(cond) }
                                            .padding(vertical = 8.dp), contentAlignment = Alignment.Center
                                    ) {
                                        Text( cond, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) orangeColor else primaryTextColor
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField( value = temperatureInput, onValueChange = { onUpdateTemperatureInput(it) }, label = { Text("Nhiệt độ (°C)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true
                                )
                                OutlinedTextField( value = customWeather, onValueChange = { onUpdateCustomWeather(it) }, label = { Text("Mô tả chi tiết (nếu có)") }, modifier = Modifier.weight(2f), singleLine = true
                                )
                            }
                        }
                    }
                }

                // Add log submission form for selected day
                item {
                    Card( colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text( text = "Điền nhật ký & tiến độ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = primaryTextColor
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Associated node selector
                            Text("LIÊN KẾT HẠNG MỤC DỰ ÁN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secondaryTextColor)
                            Spacer(modifier = Modifier.height(6.dp))

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton( onClick = { onSetNodeDropdownExpanded(true) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text( text = selectedNodeCodeForLog?.let { code ->
                                            nodeDisplayName(code, nodesMap) + " ($code)"
                                        } ?: "Chọn hạng mục liên kết (không bắt buộc)", color = if (selectedNodeCodeForLog != null) primaryTextColor else secondaryTextColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Start, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = secondaryTextColor)
                                }
                                DropdownMenu( expanded = nodeDropdownExpanded, onDismissRequest = { onSetNodeDropdownExpanded(false) }, modifier = Modifier.fillMaxWidth(0.9f).background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    DropdownMenuItem( text = { Text("Không liên kết hạng mục", color = secondaryTextColor) }, onClick = {
                                            onUpdateSelectedNodeCodeForLog(null)
                                            onSetNodeDropdownExpanded(false)
                                        }
                                    )
                                    progressUiState.nodeSelectorOptions.forEach { option ->
                                        DropdownMenuItem( text = { Text(option.label, color = primaryTextColor) }, onClick = {
                                                val node = nodesMap.getValue(option.key)
                                                onUpdateSelectedNodeCodeForLog(option.key)
                                                onSetNodeDropdownExpanded(false)
                                                // Pre-fill default work item if empty
                                                if (workItemInput.isBlank()) {
                                                    workItemInput = "Thi công tại ${nodeDisplayName(node.code, nodesMap)}"
                                                    onUpdateWorkItemInput("Thi công tại ${nodeDisplayName(node.code, nodesMap)}")
                                                }
                                                // Pre-fill current progress if any
                                                val existing = progressByCode[option.key]
                                                onUpdateActualProgressInput(existing?.actual?.toString() ?: "")
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Associated Work Category Dropdown
                            Text("HẠNG MỤC CÔNG VIỆC", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secondaryTextColor)
                            Spacer(modifier = Modifier.height(6.dp))

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton( onClick = { onSetCategoryDropdownExpanded(true) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text( text = if (selectedCategoryName.isNotBlank()) selectedCategoryName else "Chọn hạng mục công việc (đổ bê tông, kéo cáp...)", color = if (selectedCategoryName.isNotBlank()) primaryTextColor else secondaryTextColor, modifier = Modifier.weight(1f), textAlign = TextAlign.Start, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = secondaryTextColor)
                                }
                                DropdownMenu( expanded = categoryDropdownExpanded, onDismissRequest = { onSetCategoryDropdownExpanded(false) }, modifier = Modifier.fillMaxWidth(0.9f).background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    DropdownMenuItem( text = { Text("+ Thêm hạng mục mới...", color = orangeColor, fontWeight = FontWeight.Bold) }, onClick = {
                                            onSetCategoryDropdownExpanded(false)
                                            onSetShowAddCategoryDialog(true)
                                        }
                                    )

                                    val materials = selectedNodeCodeForLog
                                        ?.let { progressUiState.materialOptionsByNodeCode[it] }
                                        .orEmpty()
                                    if (materials.isNotEmpty()) {
                                            DropdownMenuItem( text = { Text("Vật tư, thiết bị tại vị trí:", color = orangeColor, fontWeight = FontWeight.Bold, fontSize = 12.sp) }, onClick = {}, enabled = false
                                            )
                                            materials.forEach { material ->
                                                DropdownMenuItem( text = { Text(material.label, color = primaryTextColor) }, onClick = {
                                                        onUpdateSelectedCategoryName(material.key)
                                                        onUpdateUnitInput("")
                                                        onSetCategoryDropdownExpanded(false)
                                                    }
                                                )
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        }

                                    DropdownMenuItem( text = { Text("Hạng mục công việc chung:", color = secondaryTextColor, fontWeight = FontWeight.Bold, fontSize = 12.sp) }, onClick = {}, enabled = false
                                    )

                                    workCategories.forEach { category ->
                                        DropdownMenuItem( text = { Text("${category.name} (${category.unit})", color = primaryTextColor) }, onClick = {
                                                onUpdateSelectedCategoryName(category.name)
                                                onUpdateUnitInput(category.unit)
                                                onSetCategoryDropdownExpanded(false)
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField( value = workItemInput, onValueChange = { onUpdateWorkItemInput(it); onUpdateLogFormError("") }, label = { Text("Nội dung thực hiện trong ngày *") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 5
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField( value = volumeInput, onValueChange = { onUpdateVolumeInput(it) }, label = { Text("Khối lượng thực hiện lũy kế:") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true
                                )
                                OutlinedTextField( value = unitInput, onValueChange = { onUpdateUnitInput(it) }, label = { Text("Đơn vị tính") }, modifier = Modifier.weight(1f), singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField( value = manpowerInput, onValueChange = { onUpdateManpowerInput(it) }, label = { Text("Số nhân công: ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true
                                )
                                
                                if (selectedNodeCodeForLog != null) {
                                    OutlinedTextField( value = actualProgressInput, onValueChange = { onUpdateActualProgressInput(it) }, label = { Text("Tiến độ thực tế (%)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField( value = noteInput, onValueChange = { onUpdateNoteInput(it) }, label = { Text("Báo cáo khó khăn / ghi chú khác") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3
                            )

                            if (logFormError.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(logFormError, color = redColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button( onClick = {
                                    if (workItemInput.isBlank()) {
                                        logFormError = "Vui lòng nhập nội dung thực hiện"
                                        onUpdateLogFormError("Vui lòng nhập nội dung thực hiện")
                                        return@Button
                                    }
                                    val manpower = manpowerInput.toIntOrNull() ?: 0
                                    val temp = temperatureInput.toDoubleOrNull() ?: 30.0
                                    val weatherDesc = customWeather.trim().ifBlank { weatherSelected }
                                    val volume = volumeInput.toDoubleOrNull() ?: 0.0
                                    
                                    onAddDailyLog( workItemInput.trim(), manpower, noteInput.trim(), weatherDesc, temp, selectedNodeCodeForLog, selectedEpoch, volume, unitInput.trim(), selectedCategoryName.trim()
                                    )

                                    // Clear fields after saving
                                    onResetLogForm()
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Đã thêm nhật ký thi công mới")
                                    }
                                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = orangeColor, contentColor = Color.White)
                            ) {
                                Text("Lưu nhật ký & đồng bộ tiến độ", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }



                // Daily Logs list for selected day
                item {
                    val dateFormatted = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(selectedDate.time)
                    Text( text = "NHẬT KÝ NGÀY $dateFormatted", fontWeight = FontWeight.Bold, color = primaryTextColor, fontSize = 12.sp, letterSpacing = 0.5.sp, modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (logsForSelectedDate.isNotEmpty()) {
                    item {
                        val totalManpower = logsForSelectedDate.sumOf { it.manpower }
                        val locations = logsForSelectedDate.mapNotNull { it.nodeCode }.distinct()
                        val volumesByCategory = logsForSelectedDate
                            .filter { it.categoryName.isNotBlank() && it.volume > 0.0 }
                            .groupBy { it.categoryName }
                            .mapValues { entry ->
                                val sum = entry.value.sumOf { it.volume }
                                val unit = entry.value.firstOrNull()?.unit.orEmpty()
                                Pair(sum, unit)
                            }
                        
                        Card( colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.5.dp, orangeColor.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text( "TỔNG HỢP NHẬT KÝ TRONG NGÀY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = orangeColor, letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Tổng nhân công", fontSize = 11.sp, color = secondaryTextColor)
                                        Text("$totalManpower người", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = primaryTextColor)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Vị trí thi công", fontSize = 11.sp, color = secondaryTextColor)
                                        Text( if (locations.isNotEmpty()) locations.joinToString(", ") else "Không liên kết", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = primaryTextColor
                                        )
                                    }
                                }
                                
                                if (volumesByCategory.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Khối lượng thực hiện lũy kế:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = secondaryTextColor)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    volumesByCategory.forEach { (category, value) ->
                                        Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(orangeColor))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(category, fontSize = 13.sp, color = primaryTextColor)
                                            }
                                            Text( "${"%.2f".format(value.first)} ${value.second}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = successColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (logsForSelectedDate.isEmpty()) {
                    item {
                        Card( colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("Chưa có nhật ký hoạt động nào ghi nhận trong ngày này", color = secondaryTextColor, fontSize = 14.sp)
                            }
                        }
                    }
                } else {
                    items(logsForSelectedDate, key = { it.id }) { log ->
                        Card( colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.createdAtEpochMs))
                                    Text( text = "Thời gian: $timeStr", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = secondaryTextColor
                                    )
                                    if (log.weather.isNotBlank()) {
                                        Text( text = "${log.weather} - ${log.temperature.roundToInt()}°C", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = orangeColor, modifier = Modifier
                                                .background(orangeColor.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text( text = log.workItem, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = primaryTextColor
                                )
                                if (log.categoryName.isNotBlank() && log.volume > 0.0) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text( text = "Hạng mục: ${log.categoryName} - Khối lượng: ${"%.2f".format(log.volume)} ${log.unit}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = successColor
                                    )
                                }
                                if (!log.nodeCode.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Link, contentDescription = null, tint = successColor, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text( text = "Liên kết: ${nodeDisplayName(log.nodeCode!!, nodesMap)} (${log.nodeCode})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = successColor
                                        )
                                    }
                                }
                                if (log.note.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box( modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                            .padding(10.dp)
                                    ) {
                                        Text(log.note, color = secondaryTextColor, fontSize = 13.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Số nhân công: ${log.manpower} người", color = secondaryTextColor, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Add Construction progress Sheet (From Progress view item selection click)
        if (selectedNodeForProgress != null) {
            val node = selectedNodeForProgress
            val plannedInput = screenUiState.progressSheetPlannedInput.ifBlank { node.planned.toString() }
            val actualInput = screenUiState.progressSheetActualInput.ifBlank { node.actual.toString() }
            val validationError = screenUiState.progressSheetValidationError
            val progressNote = screenUiState.progressSheetNote

            ModalBottomSheet( onDismissRequest = { onDismissProgressNodeSheet() }, sheetState = bottomSheetState
            ) {
                Column( modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text( text = nodeDisplayName(node.nodeCode, nodesMap), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = primaryTextColor
                    )
                    Text( text = "Mã: ${node.nodeCode}", fontSize = 12.sp, color = secondaryTextColor, modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (node.updatedAtEpochMs > 0L) {
                        Text( "Cập nhật lần cuối: ${formatRelativeTime(node.updatedAtEpochMs)}", fontSize = 12.sp, color = secondaryTextColor, modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    OutlinedTextField( value = plannedInput, onValueChange = { onUpdateProgressSheetPlannedInput(it); onUpdateProgressSheetValidationError("") }, label = { Text("Kế hoạch (%)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField( value = actualInput, onValueChange = { onUpdateProgressSheetActualInput(it); onUpdateProgressSheetValidationError("") }, label = { Text("Thực tế (%)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField( value = progressNote, onValueChange = { onUpdateProgressSheetNote(it) }, label = { Text("Ghi chú (tùy chọn)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3
                    )

                    if (validationError.isNotBlank()) {
                        Text( text = validationError, color = redColor, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton( onClick = { onDismissProgressNodeSheet() }, modifier = Modifier.weight(1f)
                        ) {
                            Text("Hủy")
                        }
                        Button( onClick = {
                                val planned = plannedInput.toFloatOrNull()
                                val actual = actualInput.toFloatOrNull()
                                if (planned == null || actual == null || planned !in 0f..100f || actual !in 0f..100f) {
                                    onUpdateProgressSheetValidationError("Giá trị phải từ 0 đến 100")
                                } else {
                                    onAddConstruction(node.nodeCode, planned, actual)
                                    onDismissProgressNodeSheet()
                                }
                            }, modifier = Modifier.weight(1f)
                        ) {
                            Text("Luu")
                        }
                    }
                }
            }
        }

        if (screenUiState.showAddCategoryDialog) {
            AlertDialog( onDismissRequest = { onSetShowAddCategoryDialog(false) }, title = { Text("HẠNG MỤC CÔNG VIỆC", fontWeight = FontWeight.Bold) }, text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField( value = screenUiState.newCategoryName, onValueChange = { onUpdateNewCategoryName(it) }, label = { Text("Tên hạng mục (ví dụ: đổ bê tông)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        OutlinedTextField( value = screenUiState.newCategoryUnit, onValueChange = { onUpdateNewCategoryUnit(it) }, label = { Text("Đơn vị tính (ví dụ: m3)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                    }
                }, confirmButton = {
                    Button( onClick = {
                            if (screenUiState.newCategoryName.isNotBlank() && screenUiState.newCategoryUnit.isNotBlank()) {
                                onAddWorkCategory(screenUiState.newCategoryName.trim(), screenUiState.newCategoryUnit.trim())
                                selectedCategoryName = screenUiState.newCategoryName.trim()
                                unitInput = screenUiState.newCategoryUnit.trim()
                                onUpdateSelectedCategoryName(selectedCategoryName)
                                onUpdateUnitInput(unitInput)
                                onUpdateNewCategoryName("")
                                onUpdateNewCategoryUnit("")
                                onSetShowAddCategoryDialog(false)
                            }
                        }
                    ) {
                        Text("Thêm")
                    }
                }, dismissButton = {
                    OutlinedButton(onClick = { onSetShowAddCategoryDialog(false) }) {
                        Text("Hủy")
                    }
                }
            )
        }
    }
}

