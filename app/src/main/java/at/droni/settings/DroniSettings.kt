package at.droni.settings

import android.content.Context
import at.droni.protocol.ElfieProtocol

/** Local settings; no tracking, analytics or user accounts. */
class DroniSettings(context: Context) {
    private val prefs = context.getSharedPreferences("droni_settings", Context.MODE_PRIVATE)

    init {
        // Apply the requested startup defaults once on upgrade, including speed 1 on existing installs.
        if (!prefs.getBoolean("shared_launch_settings_v1", false)) {
            prefs.edit().putInt("default_drone_speed", 1)
                .putBoolean("launch_auto_calibration", true)
                .putBoolean("shared_launch_settings_v1", true).apply()
        }
    }

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

    /** Shared by manual and path launch; the aircraft must be level and stationary. */
    var autoCalibrationBeforeTakeoff: Boolean
        get() = prefs.getBoolean("launch_auto_calibration", true)
        set(value) { prefs.edit().putBoolean("launch_auto_calibration", value).apply() }

    var flightSequence: FlightSequenceSettings
        get() = FlightSequenceSettings(
            speedMode = defaultDroneSpeed,
            autoConnect = prefs.getBoolean("launch_auto_connect", true),
            connectionWaitMs = prefs.getLong("launch_connection_wait", 10_000).coerceIn(1_000, 60_000),
            autoCalibrate = autoCalibrationBeforeTakeoff,
            calibrationWaitMs = prefs.getLong("launch_calibration_wait", 5_000).coerceIn(1_000, 30_000),
            takeoffWaitMs = prefs.getLong("launch_takeoff_wait", 5_000).coerceIn(1_000, 30_000),
            climbPercent = prefs.getInt("launch_climb_percent", 100).coerceIn(0, 100),
            climbDurationMs = prefs.getLong("launch_climb_duration", 200).coerceIn(50, 1_000),
            commandGapMs = prefs.getLong("launch_command_gap", 200).coerceIn(25, 2_000),
            landingWaitMs = prefs.getLong("launch_landing_wait", 5_000).coerceIn(1_000, 30_000)
        )
        set(value) {
            prefs.edit().putInt("default_drone_speed", value.speedMode)
                .putBoolean("launch_auto_connect", value.autoConnect)
                .putLong("launch_connection_wait", value.connectionWaitMs)
                .putBoolean("launch_auto_calibration", value.autoCalibrate)
                .putLong("launch_calibration_wait", value.calibrationWaitMs)
                .putLong("launch_takeoff_wait", value.takeoffWaitMs)
                .putInt("launch_climb_percent", value.climbPercent)
                .putLong("launch_climb_duration", value.climbDurationMs)
                .putLong("launch_command_gap", value.commandGapMs)
                .putLong("launch_landing_wait", value.landingWaitMs).apply()
        }
}
