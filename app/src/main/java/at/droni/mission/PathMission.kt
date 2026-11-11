package at.droni.mission

import at.droni.protocol.FlightInputState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.math.abs

/** Flight actions only; a fake implementation can verify the full timeline without an aircraft. */
interface PathFlightControl {
    val connected: Boolean
    val takeoffRequested: Boolean
    fun neutral()
    fun calibrate()
    fun takeoff()
    fun setLeft(yaw: Float, ascent: Float)
    fun setRight(roll: Float, pitch: Float)
    fun land()
}

/** Executes bounded, unacknowledged commands; does not estimate altitude or track coordinates. */
internal class PathMission(
    private val control: PathFlightControl,
    private val pause: suspend (Long) -> Unit = { delay(it) }
) {
    companion object {
        const val CALIBRATION_WAIT_MS = FlightInputState.COMMAND_PULSE_MS
        const val TAKEOFF_WAIT_MS = FlightInputState.COMMAND_PULSE_MS
        const val CLIMB_MS = 200L
        const val CLIMB_INPUT = 1f
    }

    suspend fun run(
        steps: List<MissionStep>,
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
        check(control.connected && !control.takeoffRequested) { "Drone must be connected and disarmed" }
        var flightRequested = false

        fun checkpoint() {
            check(control.connected) { "Wi-Fi link lost" }
            if (flightRequested) check(control.takeoffRequested) { "Flight request cancelled" }
        }
        suspend fun waitFor(durationMs: Long) {
            pause(durationMs)
            currentCoroutineContext().ensureActive()
            checkpoint()
        }

        try {
            currentCoroutineContext().ensureActive()
            control.neutral()
            onProgress(0f)
            onStatus("Calibration requested · waiting 1 s")
            control.calibrate()
            waitFor(CALIBRATION_WAIT_MS)
            check(!control.takeoffRequested) { "Drone is already in a requested flight" }

            onStatus("Takeoff requested · waiting 1 s")
            control.takeoff()
            flightRequested = true
            waitFor(TAKEOFF_WAIT_MS)

            onStatus("Climb input 100% · 200 ms pulse")
            control.setLeft(0f, CLIMB_INPUT)
            waitFor(CLIMB_MS)
            control.setLeft(0f, 0f)
            waitFor(PathPlanner.REST_MS)

            for ((index, step) in steps.withIndex()) {
                currentCoroutineContext().ensureActive()
                checkpoint()
                onStatus("Path command ${index + 1}/${steps.size} · no position feedback")
                control.setRight(step.roll, step.pitch)
                waitFor(step.durationMs)
                control.setRight(0f, 0f)
                waitFor(PathPlanner.REST_MS)
                onProgress((index + 1).toFloat() / steps.size)
            }
            onStatus("Route commands complete · landing requested, not confirmed")
        } finally {
            // Synchronous cleanup also runs if cancelled during calibration, ascent or a path pulse.
            control.neutral()
            if (flightRequested && control.connected && control.takeoffRequested) control.land()
        }
    }
}
