package at.droni

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import at.droni.settings.DroniSettings
import at.droni.settings.PathFlightSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises offline navigation and settings persistence without connecting to any aircraft. */
@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun offlineNavigationAndPreferencesSurviveRecreation() {
        ui.activity.getSharedPreferences("droni_settings", 0).edit().clear().commit()
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        ui.onNodeWithText("Open flight deck").assertIsDisplayed()
        screenshot("01-home")
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithContentDescription("Flight deck speed 0").performScrollTo().performClick()
        assertEquals(0, ui.activity.getSharedPreferences("droni_settings", 0).getInt("default_drone_speed", -1))
        ui.onNodeWithContentDescription("Increase Path takeoff wait").performScrollTo().performClick()
        assertEquals(4000L, ui.activity.getSharedPreferences("droni_settings", 0).getLong("path_takeoff_wait", -1))
        screenshot("02-settings")
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        ui.onNodeWithText("Settings").performClick()
        assertEquals(0, ui.activity.getSharedPreferences("droni_settings", 0).getInt("default_drone_speed", -1))
        assertEquals(4000L, ui.activity.getSharedPreferences("droni_settings", 0).getLong("path_takeoff_wait", -1))
        ui.onNodeWithContentDescription("Auto-connect for Draw Path").performScrollTo().performClick()
        home()
        ui.onNodeWithText("Open flight deck").performClick()
        ui.onNodeWithText("TAKE OFF").assertIsNotEnabled()
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithText("Reset Draw Path defaults").performScrollTo().performClick()
        assertEquals(0, ui.activity.getSharedPreferences("droni_settings", 0).getInt("default_drone_speed", -1))
        val preferences = DroniSettings(ui.activity)
        assertEquals(PathFlightSettings(), preferences.pathFlight)
        assertFalse(preferences.autoCalibrationBeforeTakeoff)
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
        // Manual flight keeps its original connect-first behavior, regardless of path settings.
        ui.onNodeWithText("TAKE OFF").assertIsNotEnabled()
        ui.onNodeWithText("SPD 0/2").assertIsDisplayed()
        ui.onNodeWithText("LAND").assertIsNotEnabled()
        ui.onNodeWithText("MOTOR STOP").assertIsNotEnabled()
        screenshot("06-flight-deck")
    }

    @Test fun legacyManualPreferencesSurviveSeparatePathSettings() {
        val prefs = ui.activity.getSharedPreferences("droni_settings", 0)
        prefs.edit().clear().putInt("default_drone_speed", 2)
            .putBoolean("auto_calibration_before_takeoff", true)
            .putBoolean("shared_launch_settings_v1", true)
            .putBoolean("launch_auto_calibration", false)
            .putLong("launch_takeoff_wait", 9000).commit()
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        val settings = DroniSettings(ui.activity)
        assertEquals(2, settings.defaultDroneSpeed)
        assertTrue(settings.autoCalibrationBeforeTakeoff)
        assertEquals(PathFlightSettings(), settings.pathFlight)
        settings.pathFlight = settings.pathFlight.copy(speedMode = 0, takeoffWaitMs = 7000, movementPercent = 25)
        assertEquals(2, settings.defaultDroneSpeed)
        assertTrue(settings.autoCalibrationBeforeTakeoff)
        settings.defaultDroneSpeed = 1
        settings.autoCalibrationBeforeTakeoff = false
        val reloaded = DroniSettings(ui.activity)
        assertEquals(0, reloaded.pathFlight.speedMode)
        assertEquals(7000L, reloaded.pathFlight.takeoffWaitMs)
        assertEquals(25, reloaded.pathFlight.movementPercent)
        assertEquals(1, reloaded.defaultDroneSpeed)
        assertFalse(reloaded.autoCalibrationBeforeTakeoff)
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
