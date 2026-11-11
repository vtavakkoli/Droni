package at.droni

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
        ui.onNodeWithContentDescription("Increase Takeoff wait").performScrollTo().performClick()
        assertEquals(6000L, ui.activity.getSharedPreferences("droni_settings", 0).getLong("launch_takeoff_wait", -1))
        screenshot("02-settings")
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        ui.onNodeWithText("Settings").performClick()
        assertEquals(0, ui.activity.getSharedPreferences("droni_settings", 0).getInt("default_drone_speed", -1))
        assertEquals(6000L, ui.activity.getSharedPreferences("droni_settings", 0).getLong("launch_takeoff_wait", -1))
        ui.onNodeWithContentDescription("Auto-connect before launch").performScrollTo().performClick()
        home()
        ui.onNodeWithText("Open flight deck").performClick()
        ui.onNodeWithText("TAKE OFF").assertIsNotEnabled()
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithText("Reset launch defaults").performScrollTo().performClick()
        assertEquals(1, ui.activity.getSharedPreferences("droni_settings", 0).getInt("default_drone_speed", -1))
        assertEquals(5000L, ui.activity.getSharedPreferences("droni_settings", 0).getLong("launch_takeoff_wait", -1))
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
        // Auto-connect makes this action available offline; do not press it during this offline test.
        ui.onNodeWithText("TAKE OFF").assertIsEnabled()
        ui.onNodeWithText("LAND").assertIsNotEnabled()
        ui.onNodeWithText("MOTOR STOP").assertIsNotEnabled()
        screenshot("06-flight-deck")
    }

    private fun home() { ui.onNodeWithContentDescription("Back to home").performClick() }

    private fun screenshot(name: String) {
        ui.waitForIdle()
        // Allow the system compositor to present the frame observed by Compose.
        android.os.SystemClock.sleep(300)
        // The test platform removes app-owned external files when uninstalling the APK.
        // Shell-owned review output survives cleanup and includes native camera surfaces.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (command in listOf("mkdir -p /sdcard/Download/droni-ui", "screencap -p /sdcard/Download/droni-ui/$name.png")) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {
                it.readBytes()
            }
        }
    }
}
