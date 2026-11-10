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
}
