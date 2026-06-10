package com.mapsupervision.app.workspace

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProgressHubScreenUiState(
    val isProgressSubTab: Boolean = true,
    val groupMode: String = "NhÃ  tháº§u",
    val filterMode: String = "All",
    val selectedNodeCodeForProgress: String? = null,
    val currentMonth: Int,
    val currentYear: Int,
    val selectedDateMillis: Long,
    val weatherSelected: String = "Náº¯ng",
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
class ProgressHubViewModel @Inject constructor() : ViewModel() {

    private val now = java.util.Calendar.getInstance()
    private val defaultSelectedDateMillis = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val _uiState = MutableStateFlow(
        ProgressHubScreenUiState(
            currentMonth = now.get(java.util.Calendar.MONTH),
            currentYear = now.get(java.util.Calendar.YEAR),
            selectedDateMillis = defaultSelectedDateMillis
        )
    )
    val uiState: StateFlow<ProgressHubScreenUiState> = _uiState.asStateFlow()

    fun setProgressTab(isProgressSubTab: Boolean) = updateState {
        it.copy(isProgressSubTab = isProgressSubTab)
    }

    fun updateGroupMode(groupMode: String) = updateState {
        it.copy(groupMode = groupMode)
    }

    fun updateFilterMode(filterMode: String) = updateState {
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
    }

    fun updateCurrentMonth(month: Int) = updateState {
        it.copy(currentMonth = month)
    }

    fun updateCurrentYear(year: Int) = updateState {
        it.copy(currentYear = year)
    }

    fun updateSelectedDateMillis(selectedDateMillis: Long) = updateState {
        it.copy(selectedDateMillis = selectedDateMillis)
    }

    fun updateWeatherSelection(weatherSelected: String) = updateState {
        it.copy(weatherSelected = weatherSelected)
    }

    fun updateCustomWeather(customWeather: String) = updateState {
        it.copy(customWeather = customWeather)
    }

    fun updateTemperatureInput(temperatureInput: String) = updateState {
        it.copy(temperatureInput = temperatureInput)
    }

    fun updateSelectedNodeCodeForLog(nodeCode: String?) = updateState {
        it.copy(selectedNodeCodeForLog = nodeCode)
    }

    fun updateManpowerInput(manpowerInput: String) = updateState {
        it.copy(manpowerInput = manpowerInput)
    }

    fun updateWorkItemInput(workItemInput: String) = updateState {
        it.copy(workItemInput = workItemInput)
    }

    fun updateNoteInput(noteInput: String) = updateState {
        it.copy(noteInput = noteInput)
    }

    fun updateActualProgressInput(actualProgressInput: String) = updateState {
        it.copy(actualProgressInput = actualProgressInput)
    }

    fun updateActualProgressChecked(actualProgressChecked: Boolean) = updateState {
        it.copy(actualProgressChecked = actualProgressChecked)
    }

    fun updateLogFormError(logFormError: String) = updateState {
        it.copy(logFormError = logFormError)
    }

    fun setNodeDropdownExpanded(expanded: Boolean) = updateState {
        it.copy(nodeDropdownExpanded = expanded)
    }

    fun updateVolumeInput(volumeInput: String) = updateState {
        it.copy(volumeInput = volumeInput)
    }

    fun updateUnitInput(unitInput: String) = updateState {
        it.copy(unitInput = unitInput)
    }

    fun updateSelectedCategoryName(selectedCategoryName: String) = updateState {
        it.copy(selectedCategoryName = selectedCategoryName)
    }

    fun setCategoryDropdownExpanded(expanded: Boolean) = updateState {
        it.copy(categoryDropdownExpanded = expanded)
    }

    fun setShowAddCategoryDialog(show: Boolean) = updateState {
        it.copy(showAddCategoryDialog = show)
    }

    fun updateNewCategoryName(newCategoryName: String) = updateState {
        it.copy(newCategoryName = newCategoryName)
    }

    fun updateNewCategoryUnit(newCategoryUnit: String) = updateState {
        it.copy(newCategoryUnit = newCategoryUnit)
    }

    fun updateProgressSheetPlannedInput(plannedInput: String) = updateState {
        it.copy(progressSheetPlannedInput = plannedInput)
    }

    fun updateProgressSheetActualInput(actualInput: String) = updateState {
        it.copy(progressSheetActualInput = actualInput)
    }

    fun updateProgressSheetValidationError(validationError: String) = updateState {
        it.copy(progressSheetValidationError = validationError)
    }

    fun updateProgressSheetNote(note: String) = updateState {
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
    }

    private fun updateState(transform: (ProgressHubScreenUiState) -> ProgressHubScreenUiState) {
        _uiState.update(transform)
    }
}
