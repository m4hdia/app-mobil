package com.daylight.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.daylight.app.data.Preferences
import com.daylight.app.ui.DaylightApp
import com.daylight.app.ui.DaylightViewModel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = DaylightApplication::class)
class NavigationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun fiveDestinationsAndDarkMode() {
        val app = ApplicationProvider.getApplicationContext<DaylightApplication>()
        runBlocking { app.repository.dao.put(Preferences(onboarded = true)) }
        val vm = DaylightViewModel(app)
        compose.setContent { DaylightApp(vm, null) {} }
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Words to return to").assertIsDisplayed()
        compose.onNodeWithText("Forms").performClick()
        compose.onNodeWithText("Rituals, made personal").assertIsDisplayed()
        compose.onNodeWithText("Schedule").performClick()
        compose.onNodeWithText("Gentle nudges").assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Make yourself at home").assertIsDisplayed()
        compose.onNodeWithText("System").performClick()
        compose.onNodeWithText("Dark").performClick()
        compose.waitUntil(10_000) { vm.state.value.preferences.theme == "Dark" }
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("TODAY'S PROGRESS").assertExists()
    }
}
