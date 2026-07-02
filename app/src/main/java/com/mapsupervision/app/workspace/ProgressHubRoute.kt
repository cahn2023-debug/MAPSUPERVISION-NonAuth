package com.mapsupervision.app.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mapsupervision.domain.model.WorkPlan

@Composable
fun ProgressHubRoute(
    activeProjectId: String?,
    constructionProgress: List<com.mapsupervision.domain.model.NodeProgress>,
    dailyLogs: List<com.mapsupervision.domain.model.DailyLog>,
    dashboardState: DashboardState,
    progressUiState: ProgressUiState,
    workCategories: List<com.mapsupervision.domain.model.WorkCategory>,
    workPlans: List<WorkPlan>,
    photos: List<com.mapsupervision.domain.model.SitePhoto> = emptyList(),
    activeProjectName: String? = null,
    initialDateMillis: Long? = null,
    onAddConstruction: (String, Float, Float) -> Unit,
    onAddDailyLog: (String, Int, String, String, Double, String?, String?, Long, Double, String, String, String?, Float?) -> Unit,
    onAddWorkCategory: (String, String) -> Unit,
    onAddWorkPlanBatch: suspend (String, List<String>, List<String>, Double, String, String, Long) -> Boolean,
    onFetchWeatherAuto: (String?, String?, (String, Double) -> Unit) -> Unit,
    viewModel: ProgressHubViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(initialDateMillis) {
        if (initialDateMillis != null) {
            viewModel.updateSelectedDateMillis(initialDateMillis)
            viewModel.setSubTab(ProgressHubSubTab.DIARY)
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = initialDateMillis }
            viewModel.updateCurrentMonth(cal.get(java.util.Calendar.MONTH))
            viewModel.updateCurrentYear(cal.get(java.util.Calendar.YEAR))
        }
    }

    LaunchedEffect(uiState.selectedNodeCodeForLog, uiState.selectedRouteCodeForLog, uiState.selectedDateMillis) {
        onFetchWeatherAuto(uiState.selectedNodeCodeForLog, uiState.selectedRouteCodeForLog) { cond, temp ->
            viewModel.updateWeatherSelection(cond)
            viewModel.updateTemperatureInput(temp.toInt().toString())
        }
    }

    ProgressHubScreen(
        activeProjectId = activeProjectId,
        constructionProgress = constructionProgress,
        dailyLogs = dailyLogs,
        workPlans = workPlans,
        dashboardState = dashboardState,
        progressUiState = progressUiState,
        screenUiState = uiState,
        workCategories = workCategories,
        photos = photos,
        activeProjectName = activeProjectName,
        onSetSubTab = viewModel::setSubTab,
        onUpdateGroupMode = viewModel::updateGroupMode,
        onUpdateFilterMode = viewModel::updateFilterMode,
        onSelectProgressNode = viewModel::selectProgressNode,
        onDismissProgressNodeSheet = viewModel::dismissProgressNodeSheet,
        onUpdateCurrentMonth = viewModel::updateCurrentMonth,
        onUpdateCurrentYear = viewModel::updateCurrentYear,
        onUpdateSelectedDateMillis = viewModel::updateSelectedDateMillis,
        onUpdateWeatherSelection = viewModel::updateWeatherSelection,
        onUpdateCustomWeather = viewModel::updateCustomWeather,
        onUpdateTemperatureInput = viewModel::updateTemperatureInput,
        onUpdateSelectedNodeCodeForLog = viewModel::updateSelectedNodeCodeForLog,
        onUpdateSelectedRouteCodeForLog = viewModel::updateSelectedRouteCodeForLog,
        onUpdateManpowerInput = viewModel::updateManpowerInput,
        onUpdateWorkItemInput = viewModel::updateWorkItemInput,
        onUpdateNoteInput = viewModel::updateNoteInput,
        onUpdateActualProgressInput = viewModel::updateActualProgressInput,
        onUpdateActualProgressChecked = viewModel::updateActualProgressChecked,
        onUpdateLogFormError = viewModel::updateLogFormError,
        onSetNodeDropdownExpanded = viewModel::setNodeDropdownExpanded,
        onSetRouteDropdownExpanded = viewModel::setRouteDropdownExpanded,
        onUpdateVolumeInput = viewModel::updateVolumeInput,
        onUpdateUnitInput = viewModel::updateUnitInput,
        onSelectWorkTemplate = viewModel::selectWorkTemplate,
        onUpdateSelectedCategoryName = viewModel::updateSelectedCategoryName,
        onSetCategoryDropdownExpanded = viewModel::setCategoryDropdownExpanded,
        onSetShowAddCategoryDialog = viewModel::setShowAddCategoryDialog,
        onUpdateNewCategoryName = viewModel::updateNewCategoryName,
        onUpdateNewCategoryUnit = viewModel::updateNewCategoryUnit,
        onUpdateProgressSheetPlannedInput = viewModel::updateProgressSheetPlannedInput,
        onUpdateProgressSheetActualInput = viewModel::updateProgressSheetActualInput,
        onUpdateProgressSheetValidationError = viewModel::updateProgressSheetValidationError,
        onUpdateProgressSheetNote = viewModel::updateProgressSheetNote,
        onResetLogForm = viewModel::resetLogForm,
        onAddConstruction = onAddConstruction,
        onAddDailyLog = onAddDailyLog,
        onAddWorkCategory = onAddWorkCategory,
        onEditDailyLog = { log ->
            val nodeProgress = log.nodeCode?.let { code ->
                progressUiState.progressByNodeCode[code]
            }
            val initialActual = nodeProgress?.actual?.let {
                if (it > 0f) it.toString() else ""
            } ?: ""
            viewModel.startEditingDailyLog(log, initialActual)
        },

        // Plan actions
        onAddWorkPlanBatch = onAddWorkPlanBatch,
        onUpdateSelectedPlanWorkName = viewModel::updateSelectedPlanWorkName,
        onUpdatePlanUnitInput = viewModel::updatePlanUnitInput,
        onUpdatePlanQuantityInput = viewModel::updatePlanQuantityInput,
        onUpdatePlanNoteInput = viewModel::updatePlanNoteInput,
        onAddSelectedPlanNodeCode = viewModel::addSelectedPlanNodeCode,
        onRemoveSelectedPlanNodeCode = viewModel::removeSelectedPlanNodeCode,
        onAddSelectedPlanRouteCode = viewModel::addSelectedPlanRouteCode,
        onRemoveSelectedPlanRouteCode = viewModel::removeSelectedPlanRouteCode,
        onSetPlanNodeDropdownExpanded = viewModel::setPlanNodeDropdownExpanded,
        onSetPlanRouteDropdownExpanded = viewModel::setPlanRouteDropdownExpanded,
        onSetPlanWorkDropdownExpanded = viewModel::setPlanWorkDropdownExpanded,
        onSetShowAddPlanWorkDialog = viewModel::setShowAddPlanWorkDialog,
        onUpdateNewPlanWorkName = viewModel::updateNewPlanWorkName,
        onUpdateNewPlanWorkUnit = viewModel::updateNewPlanWorkUnit,
        onUpdatePlanFormError = viewModel::updatePlanFormError,
        onSelectPlanWorkTemplate = viewModel::selectPlanWorkTemplate,
        onResetPlanForm = viewModel::resetPlanForm
    )
}
