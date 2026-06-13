package com.mapsupervision.app.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ProgressHubRoute(
    activeProjectId: String?,
    constructionProgress: List<com.mapsupervision.domain.model.NodeProgress>,
    dailyLogs: List<com.mapsupervision.domain.model.DailyLog>,
    dashboardState: DashboardState,
    progressUiState: ProgressUiState,
    workCategories: List<com.mapsupervision.domain.model.WorkCategory>,
    photos: List<com.mapsupervision.domain.model.SitePhoto> = emptyList(),
    activeProjectName: String? = null,
    onAddConstruction: (String, Float, Float) -> Unit,
    onAddDailyLog: (String, Int, String, String, Double, String?, String?, Long, Double, String, String, String?) -> Unit,
    onAddDailyLogBatch: (String, Int, String, String, Double, List<String>, Long, Double, String, String) -> Unit,
    onAddWorkCategory: (String, String) -> Unit,
    onFetchWeatherAuto: (String?, String?, (String, Double) -> Unit) -> Unit,
    viewModel: ProgressHubViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
        dashboardState = dashboardState,
        progressUiState = progressUiState,
        screenUiState = uiState,
        workCategories = workCategories,
        photos = photos,
        activeProjectName = activeProjectName,
        onSetProgressTab = viewModel::setProgressTab,
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
        onAddDailyLogBatch = onAddDailyLogBatch,
        onAddWorkCategory = onAddWorkCategory,
        onEditDailyLog = viewModel::startEditingDailyLog
    )
}
