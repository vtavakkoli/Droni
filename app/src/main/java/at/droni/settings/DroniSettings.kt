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
}
