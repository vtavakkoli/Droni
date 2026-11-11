package at.droni.settings

import android.content.Context
import at.droni.protocol.ElfieProtocol

/** Local settings; no tracking, analytics or user accounts. */
class DroniSettings(context: Context) {
    private val prefs = context.getSharedPreferences("droni_settings", Context.MODE_PRIVATE)

    var droneHost: String
        get() = prefs.getString("drone_ip", ElfieProtocol.DEFAULT_HOST) ?: ElfieProtocol.DEFAULT_HOST
        set(value) { prefs.edit().putString("drone_ip", value.take(32)).apply() }

    var startScreen: String
        get() = prefs.getString("start_screen", "home") ?: "home"
        set(value) { prefs.edit().putString("start_screen", value).apply() }

    var gridOverlay: Boolean
        get() = prefs.getBoolean("grid_overlay", false)
        set(value) { prefs.edit().putBoolean("grid_overlay", value).apply() }

    /** Persistent preflight speed (0..2); existing installs default to mode 1. */
    var defaultDroneSpeed: Int
        get() = prefs.getInt("default_drone_speed", 1).coerceIn(0, 2)
        set(value) {
            require(value in 0..2) { "Default drone speed must be 0, 1 or 2" }
            prefs.edit().putInt("default_drone_speed", value).apply()
        }

    /** Off by default: only calibrate while stationary and on a level surface. */
    var autoCalibrationBeforeTakeoff: Boolean
        get() = prefs.getBoolean("auto_calibration_before_takeoff", false)
        set(value) { prefs.edit().putBoolean("auto_calibration_before_takeoff", value).apply() }

    /** Separate keys keep Draw Path changes from altering the pre-PR10 flight deck. */
    var pathFlight: PathFlightSettings
        get() = PathFlightSettings(
            speedMode = prefs.getInt("path_speed", 1).coerceIn(0, 2),
            autoConnect = prefs.getBoolean("path_auto_connect", true),
            connectionWaitMs = prefs.getLong("path_connection_wait", 5_000).coerceIn(1_000, 60_000),
            autoCalibrate = prefs.getBoolean("path_auto_calibration", true),
            calibrationWaitMs = prefs.getLong("path_calibration_wait", 3_000).coerceIn(1_000, 30_000),
            takeoffWaitMs = prefs.getLong("path_takeoff_wait", 3_000).coerceIn(1_000, 30_000),
            climbPercent = prefs.getInt("path_climb_percent", 100).coerceIn(0, 100),
            climbDurationMs = prefs.getLong("path_climb_duration", 300).coerceIn(50, 1_000),
            commandGapMs = prefs.getLong("path_command_gap", 300).coerceIn(25, 2_000),
            movementPercent = prefs.getInt("path_movement_percent", 75).coerceIn(1, 75),
            landingWaitMs = prefs.getLong("path_landing_wait", 5_000).coerceIn(1_000, 30_000)
        )
        set(value) {
            prefs.edit().putInt("path_speed", value.speedMode)
                .putBoolean("path_auto_connect", value.autoConnect)
                .putLong("path_connection_wait", value.connectionWaitMs)
                .putBoolean("path_auto_calibration", value.autoCalibrate)
                .putLong("path_calibration_wait", value.calibrationWaitMs)
                .putLong("path_takeoff_wait", value.takeoffWaitMs)
                .putInt("path_climb_percent", value.climbPercent)
                .putLong("path_climb_duration", value.climbDurationMs)
                .putLong("path_command_gap", value.commandGapMs)
                .putInt("path_movement_percent", value.movementPercent)
                .putLong("path_landing_wait", value.landingWaitMs).apply()
        }
}
