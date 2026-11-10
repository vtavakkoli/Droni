package at.droni

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises offline navigation and settings persistence without connecting to any aircraft. */
@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun offlineNavigationAndPreferencesSurviveRecreation() {
        ui.onNodeWithText("Open flight deck").assertIsDisplayed()
        screenshot("01-home")
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithText("Low").performScrollTo().performClick()
        assertEquals(0, ui.activity.getSharedPreferences("droni_settings", 0).getInt("default_drone_speed", -1))
        screenshot("02-settings")
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        ui.onNodeWithText("Settings").performClick()
        assertEquals(0, ui.activity.getSharedPreferences("droni_settings", 0).getInt("default_drone_speed", -1))
        home()
        ui.onNodeWithText("Draw path").performClick()
        ui.onNodeWithText("Simulate path").assertIsNotEnabled()
        ui.onNodeWithContentDescription("Route drawing canvas").performTouchInput {
            swipe(Offset(width * .18f, height * .72f), Offset(width * .78f, height * .28f), 600)
        }
        ui.onNodeWithText("Simulate path").assertIsEnabled()
        screenshot("03-path")
        ui.onNodeWithText("Clear drawing").performScrollTo().performClick()
        ui.onNodeWithText("Simulate path").assertIsNotEnabled()
        home()
        ui.onNodeWithText("Recordings").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Your next memory starts here").fetchSemanticsNodes().isNotEmpty() }
        screenshot("04-recordings")
        home()
        ui.onNodeWithText("About Droni").performClick()
        ui.onNodeWithText("Dr. Vahid Tavakkoli").assertIsDisplayed()
        screenshot("05-about")
        home()
        ui.onNodeWithText("Open flight deck").performClick()
        ui.onNodeWithText("TAKE OFF").assertIsNotEnabled()
        ui.onNodeWithText("LAND").assertIsNotEnabled()
        ui.onNodeWithText("MOTOR STOP").assertIsNotEnabled()
        screenshot("06-flight-deck")
    }

    private fun home() { ui.onNodeWithContentDescription("Back to home").performClick() }

    private fun screenshot(name: String) {
        ui.waitForIdle()
        val folder = File(ui.activity.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
