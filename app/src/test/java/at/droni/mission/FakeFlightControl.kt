package at.droni.mission

internal data class FlightEvent(val at: Long, val action: String, val x: Float = 0f, val y: Float = 0f, val duration: Long = 0)

internal class FakeFlightControl : PathFlightControl {
    var now = 0L
    override var connected = false
    override var takeoffRequested = false
    override var speedMode = 1
    var connectSucceeds = true
    var pending = false
    var replies = 0L
    val events = mutableListOf<FlightEvent>()
    val checkedPhases = mutableListOf<String>()
    fun advance(ms: Long) {
        now += ms
        if (pending && connectSucceeds) { connected = true; pending = false }
    }
    override fun connect(host: String) { pending = true; events += FlightEvent(now, "connect") }
    override fun cancelPendingConnection() {
        if (pending) { pending = false; events += FlightEvent(now, "cancel connection") }
    }
    override fun setFlightSpeed(mode: Int) { speedMode = mode; events += FlightEvent(now, "speed", mode.toFloat()) }
    override fun neutral() { events += FlightEvent(now, "neutral") }
    override fun calibrate() { events += FlightEvent(now, "calibrate") }
    override fun takeoff() { takeoffRequested = true; events += FlightEvent(now, "takeoff") }
    override fun climbPulse(ascent: Float, durationMs: Long) { events += FlightEvent(now, "climb", ascent, duration = durationMs) }
    override fun pathPulse(roll: Float, pitch: Float, durationMs: Long) {
        events += FlightEvent(now, "right", roll, pitch, durationMs)
    }
    override fun land() { takeoffRequested = false; events += FlightEvent(now, "land") }
    override fun responseCount(): Long = replies
    override fun checkResponses(phase: String, since: Long): String {
        checkedPhases += phase
        return "$phase: ${replies - since} UDP replies · flight ACK unverified"
    }
}
