package at.droni.mission

import at.droni.settings.FlightSequenceSettings
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.math.abs

/** Flight actions and response observations, independently testable without an aircraft. */
interface PathFlightControl {
    val connected: Boolean
    val takeoffRequested: Boolean
    fun connect(host: String)
    fun cancelPendingConnection()
    fun setFlightSpeed(mode: Int)
    fun neutral()
    fun calibrate()
    fun takeoff()
    fun climbPulse(ascent: Float, durationMs: Long)
    fun setRight(roll: Float, pitch: Float)
    fun land()
    fun responseCount(): Long
    fun checkResponses(phase: String, since: Long): String
}

/** Bounded screen-direction commands after the same launch sequence as manual flight. */
internal class PathMission(
    private val control: PathFlightControl,
    private val pause: suspend (Long) -> Unit = { delay(it) }
) {
    suspend fun run(
        steps: List<MissionStep>,
        host: String,
        settings: FlightSequenceSettings,
        onStatus: (String) -> Unit = {},
        onProgress: (Float) -> Unit = {}
    ) {
        require(steps.isNotEmpty() && steps.size <= PathPlanner.MAX_STEPS) { "Draw a bounded route first" }
        require(steps.all {
            it.roll.isFinite() && it.pitch.isFinite() &&
                abs(it.roll) <= PathPlanner.MAX_DEFLECTION + .0001f &&
                abs(it.pitch) <= PathPlanner.MAX_DEFLECTION + .0001f &&
                it.durationMs in 1L..180L
        }) { "Route commands exceed the path limits" }
        check(!control.takeoffRequested) { "Land before starting a new path trial" }
        val launch = FlightLaunch(control, pause)
        var launched = false
        try {
            onProgress(0f)
            launch.run(host, settings, onStatus)
            launched = true
            for ((index, step) in steps.withIndex()) {
                currentCoroutineContext().ensureActive()
                check(control.connected && control.takeoffRequested) { "Flight request cancelled or disconnected" }
                val baseline = control.responseCount()
                onStatus("Path command ${index + 1}/${steps.size} · checking incoming replies")
                control.setRight(step.roll, step.pitch)
                pause(step.durationMs)
                currentCoroutineContext().ensureActive()
                control.neutral()
                check(control.connected && control.takeoffRequested) { "Flight request cancelled or disconnected" }
                pause(settings.commandGapMs)
                currentCoroutineContext().ensureActive()
                check(control.connected && control.takeoffRequested) { "Flight request cancelled or disconnected" }
                onStatus(control.checkResponses("Path ${index + 1}", baseline))
                onProgress((index + 1).toFloat() / steps.size)
            }
            launch.land(settings, onStatus)
        } finally {
            control.neutral()
            if (launched && control.connected && control.takeoffRequested) control.land()
        }
    }
}
