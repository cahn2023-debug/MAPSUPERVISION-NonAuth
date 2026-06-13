package com.mapsupervision.app.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressHubViewModelTest {

    @Test
    fun `select progress node initializes sheet inputs`() {
        val viewModel = ProgressHubViewModel()

        viewModel.selectProgressNode("N-01", "30", "20")

        val state = viewModel.uiState.value
        assertEquals("N-01", state.selectedNodeCodeForProgress)
        assertEquals("30", state.progressSheetPlannedInput)
        assertEquals("20", state.progressSheetActualInput)
    }

    @Test
    fun `dismiss progress node sheet clears sheet state`() {
        val viewModel = ProgressHubViewModel()

        viewModel.selectProgressNode("N-01", "30", "20")
        viewModel.updateProgressSheetValidationError("Invalid")
        viewModel.updateProgressSheetNote("Need follow-up")
        viewModel.dismissProgressNodeSheet()

        val state = viewModel.uiState.value
        assertNull(state.selectedNodeCodeForProgress)
        assertEquals("", state.progressSheetPlannedInput)
        assertEquals("", state.progressSheetActualInput)
        assertEquals("", state.progressSheetValidationError)
        assertEquals("", state.progressSheetNote)
    }

    @Test
    fun `reset log form clears transient inputs`() {
        val viewModel = ProgressHubViewModel()

        viewModel.updateWorkItemInput("Task")
        viewModel.updateNoteInput("Note")
        viewModel.updateSelectedNodeCodeForLog("N-01")
        viewModel.updateSelectedCategoryName("Concrete")
        viewModel.updateUnitInput("m3")
        viewModel.updateVolumeInput("12")
        viewModel.updateManpowerInput("9")
        viewModel.updateLogFormError("Error")
        viewModel.resetLogForm()

        val state = viewModel.uiState.value
        assertEquals("", state.workItemInput)
        assertEquals("", state.noteInput)
        assertNull(state.selectedNodeCodeForLog)
        assertEquals("", state.selectedCategoryName)
        assertEquals("", state.unitInput)
        assertEquals("", state.volumeInput)
        assertEquals("5", state.manpowerInput)
        assertEquals("", state.logFormError)
    }

    @Test
    fun `select work template applies template unit`() {
        val viewModel = ProgressHubViewModel()

        viewModel.updateLogFormError("Error")
        viewModel.setCategoryDropdownExpanded(true)
        viewModel.selectWorkTemplate("Bê tông móng", "m3")

        val state = viewModel.uiState.value
        assertEquals("Bê tông móng", state.selectedCategoryName)
        assertEquals("m3", state.unitInput)
        assertEquals(false, state.categoryDropdownExpanded)
        assertEquals("", state.logFormError)
    }

    @Test
    fun `selecting another work template replaces previous unit`() {
        val viewModel = ProgressHubViewModel()

        viewModel.selectWorkTemplate("Cáp quang", "m")
        viewModel.selectWorkTemplate("Bê tông móng", "m3")

        val state = viewModel.uiState.value
        assertEquals("Bê tông móng", state.selectedCategoryName)
        assertEquals("m3", state.unitInput)
    }

    @Test
    fun `clearing work template resets category and unit`() {
        val viewModel = ProgressHubViewModel()

        viewModel.selectWorkTemplate("Cáp quang", "m")
        viewModel.selectWorkTemplate("", "")

        val state = viewModel.uiState.value
        assertEquals("", state.selectedCategoryName)
        assertEquals("", state.unitInput)
    }
}
