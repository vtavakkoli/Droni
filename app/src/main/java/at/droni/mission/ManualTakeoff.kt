package at.droni.mission

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Only the manual flight deck's calibration and takeoff requests. */
interface ManualFlightControl {
    val connected: Boolean
    val takeoffRequested: Boolean
    fun neutral()
    fun calibrate()
    fun takeoff()
}

/** A one-second calibration command pulse followed by a full three-second settling wait. */
internal class ManualTakeoff(
    private val control: ManualFlightControl,
    private val pause: suspend (Long) -> Unit = { delay(it) }
) {
    companion object { const val CALIBRATION_WAIT_MS = 3_000L }

    suspend fun run(isCurrentRequest: () -> Boolean = { true }) {
        currentCoroutineContext().ensureActive()
        if (!control.connected || control.takeoffRequested || !isCurrentRequest()) return
        control.neutral()
        control.calibrate()
        pause(CALIBRATION_WAIT_MS)
        currentCoroutineContext().ensureActive()
        if (control.connected && !control.takeoffRequested && isCurrentRequest()) control.takeoff()
    }
}
