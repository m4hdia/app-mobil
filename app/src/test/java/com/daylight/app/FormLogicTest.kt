package com.daylight.app

import com.daylight.app.data.*
import com.daylight.app.domain.FormLogic
import org.junit.Assert.*
import org.junit.Test

class FormLogicTest {
    private val answer = Answer(responseId = "r", questionId = "q", position = 0, title = "Goals", type = "Checkbox", options = listOf("Read", "Exercise", "Study", "Build", "Reflect"))
    @Test fun uncheckedChecklistCanBeSavedAsMorningPlan() { assertNull(FormLogic.error(listOf(answer))) }
    @Test fun completionIsBasedOnItems() { assertEquals(80, FormLogic.percent(listOf(answer.copy(selected = answer.options.take(4))))) }
    @Test fun requiredCheckboxNeedsSelection() { assertNotNull(FormLogic.error(listOf(answer.copy(required = true)))); assertNull(FormLogic.error(listOf(answer.copy(required = true, selected = listOf("Read"))))) }
    @Test fun requiredTextRejectsWhitespace() { assertNotNull(FormLogic.error(listOf(answer.copy(type = "ShortText", value = "   ", required = true)))) }
    @Test fun invalidNumberRejected() { assertNotNull(FormLogic.error(listOf(answer.copy(type = "Number", value = "NaN")))) }
    @Test fun negativeAndDecimalNumbersSupported() { assertNull(FormLogic.error(listOf(answer.copy(type = "Number", value = "-2.5")))) }
    @Test fun ratingBoundsEnforced() { assertNotNull(FormLogic.error(listOf(answer.copy(type = "Rating", value = "6")))) }
    @Test fun deletedOptionIsNotAccepted() { assertNotNull(FormLogic.error(listOf(answer.copy(selected = listOf("Unknown"))))) }
    @Test fun emptyFormHasZeroProgress() { assertEquals(0, FormLogic.percent(emptyList())) }
    @Test fun notificationContainsCheckedAndUncheckedItems() {
        val text = FormLogic.summary(listOf(answer.copy(selected = listOf("Read"))))
        assertTrue(text.contains("☑ Read")); assertTrue(text.contains("☐ Exercise"))
    }
}
