package com.mapsupervision.app.workspace

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProgressHubScreenUiState(
    val isProgressSubTab: Boolean = true,
    val groupMode: String = "Nhà thầu",
    val filterMode: String = "All",
    val selectedNodeCodeForProgress: String? = null,
    val currentMonth: Int,
    val currentYear: Int,
    val selectedDateMillis: Long,
    val weatherSelected: String = "Nắng",
    val customWeather: String = "",
    val temperatureInput: String = "30",
    val selectedNodeCodeForLog: String? = null,
    val manpowerInput: String = "5",
    val workItemInput: String = "",
    val noteInput: String = "",
    val actualProgressInput: String = "",
    val actualProgressChecked: Boolean = false,
    val logFormError: String = "",
    val nodeDropdownExpanded: Boolean = false,
    val volumeInput: String = "",
    val unitInput: String = "",
    val selectedCategoryName: String = "",
    val categoryDropdownExpanded: Boolean = false,
    val showAddCategoryDialog: Boolean = false,
    val newCategoryName: String = "",
    val newCategoryUnit: String = "",
    val progressSheetPlannedInput: String = "",
    val progressSheetActualInput: String = "",
    val progressSheetValidationError: String = "",
    val progressSheetNote: String = ""
)

@HiltViewModel
class ProgressHubViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val now = java.util.Calendar.getInstance()
    private val defaultSelectedDateMillis = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val _uiState = MutableStateFlow(
        ProgressHubScreenUiState(
            isProgressSubTab = savedStateHandle[KEY_IS_PROGRESS_TAB] ?: true,
            groupMode = savedStateHandle[KEY_GROUP_MODE] ?: "Nhà thầu",
            filterMode = savedStateHandle[KEY_FILTER_MODE] ?: "All",
            selectedNodeCodeForProgress = savedStateHandle[KEY_SELECTED_PROGRESS_NODE],
            currentMonth = savedStateHandle[KEY_CURRENT_MONTH] ?: now.get(java.util.Calendar.MONTH),
            currentYear = savedStateHandle[KEY_CURRENT_YEAR] ?: now.get(java.util.Calendar.YEAR),
            selectedDateMillis = savedStateHandle[KEY_SELECTED_DATE_MILLIS] ?: defaultSelectedDateMillis,
            weatherSelected = savedStateHandle[KEY_WEATHER_SELECTED] ?: "Nắng",
            customWeather = savedStateHandle[KEY_CUSTOM_WEATHER] ?: "",
            temperatureInput = savedStateHandle[KEY_TEMPERATURE_INPUT] ?: "30",
            selectedNodeCodeForLog = savedStateHandle[KEY_SELECTED_LOG_NODE],
            manpowerInput = savedStateHandle[KEY_MANPOWER_INPUT] ?: "5",
            workItemInput = savedStateHandle[KEY_WORK_ITEM_INPUT] ?: "",
            noteInput = savedStateHandle[KEY_NOTE_INPUT] ?: "",
            actualProgressInput = savedStateHandle[KEY_ACTUAL_PROGRESS_INPUT] ?: "",
            actualProgressChecked = savedStateHandle[KEY_ACTUAL_PROGRESS_CHECKED] ?: false,
            logFormError = savedStateHandle[KEY_LOG_FORM_ERROR] ?: "",
            nodeDropdownExpanded = false,
            volumeInput = savedStateHandle[KEY_VOLUME_INPUT] ?: "",
            unitInput = savedStateHandle[KEY_UNIT_INPUT] ?: "",
            selectedCategoryName = savedStateHandle[KEY_SELECTED_CATEGORY_NAME] ?: "",
            categoryDropdownExpanded = false,
            showAddCategoryDialog = savedStateHandle[KEY_SHOW_ADD_CATEGORY_DIALOG] ?: false,
            newCategoryName = savedStateHandle[KEY_NEW_CATEGORY_NAME] ?: "",
            newCategoryUnit = savedStateHandle[KEY_NEW_CATEGORY_UNIT] ?: "",
            progressSheetPlannedInput = savedStateHandle[KEY_PROGRESS_PLANNED_INPUT] ?: "",
            progressSheetActualInput = savedStateHandle[KEY_PROGRESS_ACTUAL_INPUT] ?: "",
            progressSheetValidationError = savedStateHandle[KEY_PROGRESS_VALIDATION_ERROR] ?: "",
            progressSheetNote = savedStateHandle[KEY_PROGRESS_NOTE] ?: ""
        )
    )
    val uiState: StateFlow<ProgressHubScreenUiState> = _uiState.asStateFlow()

    fun setProgressTab(isProgressSubTab: Boolean) = persist(KEY_IS_PROGRESS_TAB) {
        it.copy(isProgressSubTab = isProgressSubTab)
    }

    fun updateGroupMode(groupMode: String) = persist(KEY_GROUP_MODE) {
        it.copy(groupMode = groupMode)
    }

    fun updateFilterMode(filterMode: String) = persist(KEY_FILTER_MODE) {
        it.copy(filterMode = filterMode)
    }

    fun selectProgressNode(nodeCode: String, plannedInput: String, actualInput: String) {
        updateState {
            it.copy(
                selectedNodeCodeForProgress = nodeCode,
                progressSheetPlannedInput = plannedInput,
                progressSheetActualInput = actualInput,
                progressSheetValidationError = "",
                progressSheetNote = ""
            )
        }
        savedStateHandle[KEY_SELECTED_PROGRESS_NODE] = nodeCode
        savedStateHandle[KEY_PROGRESS_PLANNED_INPUT] = plannedInput
        savedStateHandle[KEY_PROGRESS_ACTUAL_INPUT] = actualInput
        savedStateHandle[KEY_PROGRESS_VALIDATION_ERROR] = ""
        savedStateHandle[KEY_PROGRESS_NOTE] = ""
    }

    fun dismissProgressNodeSheet() {
        updateState {
            it.copy(
                selectedNodeCodeForProgress = null,
                progressSheetPlannedInput = "",
                progressSheetActualInput = "",
                progressSheetValidationError = "",
                progressSheetNote = ""
            )
        }
        savedStateHandle[KEY_SELECTED_PROGRESS_NODE] = null
        savedStateHandle[KEY_PROGRESS_PLANNED_INPUT] = ""
        savedStateHandle[KEY_PROGRESS_ACTUAL_INPUT] = ""
        savedStateHandle[KEY_PROGRESS_VALIDATION_ERROR] = ""
        savedStateHandle[KEY_PROGRESS_NOTE] = ""
    }

    fun updateCurrentMonth(month: Int) = persist(KEY_CURRENT_MONTH) {
        it.copy(currentMonth = month)
    }

    fun updateCurrentYear(year: Int) = persist(KEY_CURRENT_YEAR) {
        it.copy(currentYear = year)
    }

    fun updateSelectedDateMillis(selectedDateMillis: Long) = persist(KEY_SELECTED_DATE_MILLIS) {
        it.copy(selectedDateMillis = selectedDateMillis)
    }

    fun updateWeatherSelection(weatherSelected: String) = persist(KEY_WEATHER_SELECTED) {
        it.copy(weatherSelected = weatherSelected)
    }

    fun updateCustomWeather(customWeather: String) = persist(KEY_CUSTOM_WEATHER) {
        it.copy(customWeather = customWeather)
    }

    fun updateTemperatureInput(temperatureInput: String) = persist(KEY_TEMPERATURE_INPUT) {
        it.copy(temperatureInput = temperatureInput)
    }

    fun updateSelectedNodeCodeForLog(nodeCode: String?) = persist(KEY_SELECTED_LOG_NODE) {
        it.copy(selectedNodeCodeForLog = nodeCode)
    }

    fun updateManpowerInput(manpowerInput: String) = persist(KEY_MANPOWER_INPUT) {
        it.copy(manpowerInput = manpowerInput)
    }

    fun updateWorkItemInput(workItemInput: String) = persist(KEY_WORK_ITEM_INPUT) {
        it.copy(workItemInput = workItemInput)
    }

    fun updateNoteInput(noteInput: String) = persist(KEY_NOTE_INPUT) {
        it.copy(noteInput = noteInput)
    }

    fun updateActualProgressInput(actualProgressInput: String) = persist(KEY_ACTUAL_PROGRESS_INPUT) {
        it.copy(actualProgressInput = actualProgressInput)
    }

    fun updateActualProgressChecked(actualProgressChecked: Boolean) = persist(KEY_ACTUAL_PROGRESS_CHECKED) {
        it.copy(actualProgressChecked = actualProgressChecked)
    }

    fun updateLogFormError(logFormError: String) = persist(KEY_LOG_FORM_ERROR) {
        it.copy(logFormError = logFormError)
    }

    fun setNodeDropdownExpanded(expanded: Boolean) {
        updateState { it.copy(nodeDropdownExpanded = expanded) }
    }

    fun updateVolumeInput(volumeInput: String) = persist(KEY_VOLUME_INPUT) {
        it.copy(volumeInput = volumeInput)
    }

    fun updateUnitInput(unitInput: String) = persist(KEY_UNIT_INPUT) {
        it.copy(unitInput = unitInput)
    }

    fun updateSelectedCategoryName(selectedCategoryName: String) = persist(KEY_SELECTED_CATEGORY_NAME) {
        it.copy(selectedCategoryName = selectedCategoryName)
    }

    fun setCategoryDropdownExpanded(expanded: Boolean) {
        updateState { it.copy(categoryDropdownExpanded = expanded) }
    }

    fun setShowAddCategoryDialog(show: Boolean) = persist(KEY_SHOW_ADD_CATEGORY_DIALOG) {
        it.copy(showAddCategoryDialog = show)
    }

    fun updateNewCategoryName(newCategoryName: String) = persist(KEY_NEW_CATEGORY_NAME) {
        it.copy(newCategoryName = newCategoryName)
    }

    fun updateNewCategoryUnit(newCategoryUnit: String) = persist(KEY_NEW_CATEGORY_UNIT) {
        it.copy(newCategoryUnit = newCategoryUnit)
    }

    fun updateProgressSheetPlannedInput(plannedInput: String) = persist(KEY_PROGRESS_PLANNED_INPUT) {
        it.copy(progressSheetPlannedInput = plannedInput)
    }

    fun updateProgressSheetActualInput(actualInput: String) = persist(KEY_PROGRESS_ACTUAL_INPUT) {
        it.copy(progressSheetActualInput = actualInput)
    }

    fun updateProgressSheetValidationError(validationError: String) = persist(KEY_PROGRESS_VALIDATION_ERROR) {
        it.copy(progressSheetValidationError = validationError)
    }

    fun updateProgressSheetNote(note: String) = persist(KEY_PROGRESS_NOTE) {
        it.copy(progressSheetNote = note)
    }

    fun resetLogForm() {
        updateState {
            it.copy(
                workItemInput = "",
                noteInput = "",
                manpowerInput = "5",
                actualProgressInput = "",
                volumeInput = "",
                unitInput = "",
                selectedCategoryName = "",
                selectedNodeCodeForLog = null,
                logFormError = ""
            )
        }
        savedStateHandle[KEY_WORK_ITEM_INPUT] = ""
        savedStateHandle[KEY_NOTE_INPUT] = ""
        savedStateHandle[KEY_MANPOWER_INPUT] = "5"
        savedStateHandle[KEY_ACTUAL_PROGRESS_INPUT] = ""
        savedStateHandle[KEY_VOLUME_INPUT] = ""
        savedStateHandle[KEY_UNIT_INPUT] = ""
        savedStateHandle[KEY_SELECTED_CATEGORY_NAME] = ""
        savedStateHandle[KEY_SELECTED_LOG_NODE] = null
        savedStateHandle[KEY_LOG_FORM_ERROR] = ""
    }

    private fun persist(key: String, transform: (ProgressHubScreenUiState) -> ProgressHubScreenUiState) {
        val next = transform(_uiState.value)
        _uiState.value = next
        when (key) {
            KEY_SELECTED_PROGRESS_NODE -> savedStateHandle[key] = next.selectedNodeCodeForProgress
            KEY_CURRENT_MONTH -> savedStateHandle[key] = next.currentMonth
            KEY_CURRENT_YEAR -> savedStateHandle[key] = next.currentYear
            KEY_SELECTED_DATE_MILLIS -> savedStateHandle[key] = next.selectedDateMillis
            KEY_WEATHER_SELECTED -> savedStateHandle[key] = next.weatherSelected
            KEY_CUSTOM_WEATHER -> savedStateHandle[key] = next.customWeather
            KEY_TEMPERATURE_INPUT -> savedStateHandle[key] = next.temperatureInput
            KEY_SELECTED_LOG_NODE -> savedStateHandle[key] = next.selectedNodeCodeForLog
            KEY_MANPOWER_INPUT -> savedStateHandle[key] = next.manpowerInput
            KEY_WORK_ITEM_INPUT -> savedStateHandle[key] = next.workItemInput
            KEY_NOTE_INPUT -> savedStateHandle[key] = next.noteInput
            KEY_ACTUAL_PROGRESS_INPUT -> savedStateHandle[key] = next.actualProgressInput
            KEY_ACTUAL_PROGRESS_CHECKED -> savedStateHandle[key] = next.actualProgressChecked
            KEY_LOG_FORM_ERROR -> savedStateHandle[key] = next.logFormError
            KEY_VOLUME_INPUT -> savedStateHandle[key] = next.volumeInput
            KEY_UNIT_INPUT -> savedStateHandle[key] = next.unitInput
            KEY_SELECTED_CATEGORY_NAME -> savedStateHandle[key] = next.selectedCategoryName
            KEY_SHOW_ADD_CATEGORY_DIALOG -> savedStateHandle[key] = next.showAddCategoryDialog
            KEY_NEW_CATEGORY_NAME -> savedStateHandle[key] = next.newCategoryName
            KEY_NEW_CATEGORY_UNIT -> savedStateHandle[key] = next.newCategoryUnit
            KEY_PROGRESS_PLANNED_INPUT -> savedStateHandle[key] = next.progressSheetPlannedInput
            KEY_PROGRESS_ACTUAL_INPUT -> savedStateHandle[key] = next.progressSheetActualInput
            KEY_PROGRESS_VALIDATION_ERROR -> savedStateHandle[key] = next.progressSheetValidationError
            KEY_PROGRESS_NOTE -> savedStateHandle[key] = next.progressSheetNote
            KEY_IS_PROGRESS_TAB -> savedStateHandle[key] = next.isProgressSubTab
            KEY_GROUP_MODE -> savedStateHandle[key] = next.groupMode
            KEY_FILTER_MODE -> savedStateHandle[key] = next.filterMode
        }
    }

    private fun updateState(transform: (ProgressHubScreenUiState) -> ProgressHubScreenUiState) {
        _uiState.update(transform)
    }

    private companion object {
        const val KEY_IS_PROGRESS_TAB = "progress_hub_is_progress_tab"
        const val KEY_GROUP_MODE = "progress_hub_group_mode"
        const val KEY_FILTER_MODE = "progress_hub_filter_mode"
        const val KEY_SELECTED_PROGRESS_NODE = "progress_hub_selected_progress_node"
        const val KEY_CURRENT_MONTH = "progress_hub_current_month"
        const val KEY_CURRENT_YEAR = "progress_hub_current_year"
        const val KEY_SELECTED_DATE_MILLIS = "progress_hub_selected_date_millis"
        const val KEY_WEATHER_SELECTED = "progress_hub_weather_selected"
        const val KEY_CUSTOM_WEATHER = "progress_hub_custom_weather"
        const val KEY_TEMPERATURE_INPUT = "progress_hub_temperature_input"
        const val KEY_SELECTED_LOG_NODE = "progress_hub_selected_log_node"
        const val KEY_MANPOWER_INPUT = "progress_hub_manpower_input"
        const val KEY_WORK_ITEM_INPUT = "progress_hub_work_item_input"
        const val KEY_NOTE_INPUT = "progress_hub_note_input"
        const val KEY_ACTUAL_PROGRESS_INPUT = "progress_hub_actual_progress_input"
        const val KEY_ACTUAL_PROGRESS_CHECKED = "progress_hub_actual_progress_checked"
        const val KEY_LOG_FORM_ERROR = "progress_hub_log_form_error"
        const val KEY_VOLUME_INPUT = "progress_hub_volume_input"
        const val KEY_UNIT_INPUT = "progress_hub_unit_input"
        const val KEY_SELECTED_CATEGORY_NAME = "progress_hub_selected_category_name"
        const val KEY_SHOW_ADD_CATEGORY_DIALOG = "progress_hub_show_add_category_dialog"
        const val KEY_NEW_CATEGORY_NAME = "progress_hub_new_category_name"
        const val KEY_NEW_CATEGORY_UNIT = "progress_hub_new_category_unit"
        const val KEY_PROGRESS_PLANNED_INPUT = "progress_hub_progress_planned_input"
        const val KEY_PROGRESS_ACTUAL_INPUT = "progress_hub_progress_actual_input"
        const val KEY_PROGRESS_VALIDATION_ERROR = "progress_hub_progress_validation_error"
        const val KEY_PROGRESS_NOTE = "progress_hub_progress_note"
    }
}
