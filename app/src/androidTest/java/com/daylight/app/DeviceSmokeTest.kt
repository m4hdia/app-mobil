package com.daylight.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class DeviceSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun onboardingAndNavigation() {
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Continue").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        if (compose.onAllNodesWithText("Continue").fetchSemanticsNodes().isNotEmpty()) {
            repeat(3) { compose.onNodeWithText("Continue").performScrollTo().performClick() }
            compose.onNodeWithText("Get started").performScrollTo().performClick()
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Library").performClick()
        compose.onNodeWithText("Words to return to").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Words to return to").assertIsDisplayed()
        compose.onNodeWithText("Forms").performClick()
        compose.onNodeWithText("Rituals, made personal").assertIsDisplayed()
        compose.onNodeWithText("Schedule").performClick()
        compose.onNodeWithText("Gentle nudges").assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Make yourself at home").assertIsDisplayed()
    }
}
