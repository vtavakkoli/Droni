package at.droni.mission

import at.droni.settings.FlightSequenceSettings
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** One startup procedure shared by the flight deck and the path runner. */
internal class FlightLaunch(
    private val control: PathFlightControl,
    private val pause: suspend (Long) -> Unit = { delay(it) }
) {
    private suspend fun waitFor(ms: Long, airborneRequest: Boolean = false) {
        pause(ms)
        currentCoroutineContext().ensureActive()
        check(control.connected) { "Drone connection failed or was lost" }
        if (airborneRequest) check(control.takeoffRequested) { "Flight request cancelled" }
    }

    private suspend fun observe(phase: String, baseline: Long, ms: Long,
                                airborneRequest: Boolean = false, status: (String) -> Unit) {
        try { waitFor(ms, airborneRequest) }
        finally { status(control.checkResponses(phase, baseline)) }
    }

    private suspend fun connection(host: String, settings: FlightSequenceSettings, status: (String) -> Unit) {
        currentCoroutineContext().ensureActive()
        check(!control.takeoffRequested) { "Land before starting a new launch sequence" }
        val baseline = if (control.connected) control.responseCount() else 0L
        if (!control.connected) {
            check(settings.autoConnect) { "Connect to the drone first or enable auto-connect" }
            status("Connecting · waiting ${settings.connectionWaitMs / 1000} s")
            control.connect(host)
            observe("Connection", baseline, settings.connectionWaitMs, status = status)
        } else status(control.checkResponses("Connection", baseline))
        control.setFlightSpeed(settings.speedMode)
        control.neutral()
    }

    private suspend fun calibration(settings: FlightSequenceSettings, status: (String) -> Unit) {
        val baseline = control.responseCount()
        status("Calibration requested · waiting ${settings.calibrationWaitMs / 1000} s")
        control.calibrate()
        observe("Calibration", baseline, settings.calibrationWaitMs, status = status)
        check(!control.takeoffRequested) { "Flight started during calibration" }
    }

    suspend fun calibrateOnly(host: String, settings: FlightSequenceSettings, onStatus: (String) -> Unit) {
        try {
            connection(host, settings, onStatus)
            calibration(settings, onStatus)
        } finally {
            control.neutral()
            control.cancelPendingConnection()
        }
    }

    suspend fun run(host: String, settings: FlightSequenceSettings, onStatus: (String) -> Unit = {}) {
        var requestedHere = false
        var completed = false
        try {
            connection(host, settings, onStatus)
            if (settings.autoCalibrate) calibration(settings, onStatus)
            currentCoroutineContext().ensureActive()
            check(control.connected && !control.takeoffRequested) { "Drone must be connected and disarmed" }
            var baseline = control.responseCount()
            onStatus("Takeoff requested · waiting ${settings.takeoffWaitMs / 1000} s")
            control.takeoff()
            requestedHere = true
            observe("Takeoff", baseline, settings.takeoffWaitMs, airborneRequest = true, status = onStatus)

            baseline = control.responseCount()
            onStatus("Up ${settings.climbPercent}% · ${settings.climbDurationMs} ms")
            control.climbPulse(settings.climbPercent / 100f, settings.climbDurationMs)
            try { waitFor(settings.climbDurationMs, airborneRequest = true) }
            finally {
                control.neutral()
                onStatus(control.checkResponses("Climb", baseline))
            }
            waitFor(settings.commandGapMs, airborneRequest = true)
            completed = true
            onStatus("Launch requests complete · manual controls ready · ACK unverified")
        } finally {
            control.neutral()
            control.cancelPendingConnection()
            if (!completed && requestedHere && control.connected && control.takeoffRequested) control.land()
        }
    }

    suspend fun land(settings: FlightSequenceSettings, onStatus: (String) -> Unit = {}) {
        if (!control.connected) { control.cancelPendingConnection(); control.neutral(); return }
        val baseline = control.responseCount()
        control.neutral()
        control.land()
        onStatus("Landing requested · checking replies for ${settings.landingWaitMs / 1000} s")
        observe("Landing", baseline, settings.landingWaitMs, status = onStatus)
    }
}
