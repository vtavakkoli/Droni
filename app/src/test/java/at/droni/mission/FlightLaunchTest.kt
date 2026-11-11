package at.droni.mission

import at.droni.settings.FlightSequenceSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FlightLaunchTest {
    @Test fun manualTakeoffUsesSharedDefaultsAndDoesNotLandAfterClimb() = runBlocking {
        val flight = FakeFlightControl()
        FlightLaunch(flight, pause = { flight.advance(it) }).run("172.16.10.1", FlightSequenceSettings())
        assertEquals(20400L, flight.now)
        assertEquals(listOf("connect", "speed", "calibrate", "takeoff", "climb"),
            flight.events.filter { it.action != "neutral" }.map { it.action })
        assertEquals(listOf("Connection", "Calibration", "Takeoff", "Climb"), flight.checkedPhases)
        assertTrue(flight.takeoffRequested)
        assertFalse(flight.events.any { it.action == "land" })
    }

    @Test fun existingConnectionSkipsConnectionWaitButStillAppliesConfiguredSpeed() = runBlocking {
        val flight = FakeFlightControl().apply { connected = true }
        FlightLaunch(flight, pause = { flight.advance(it) }).run("172.16.10.1", FlightSequenceSettings())
        assertEquals(10400L, flight.now)
        assertFalse(flight.events.any { it.action == "connect" })
        assertEquals(FlightEvent(0, "speed", 1f), flight.events.first())
    }

    @Test fun changedSettingsControlManualAndPathStartupIdentically() = runBlocking {
        val settings = FlightSequenceSettings(speedMode = 2, connectionWaitMs = 2000,
            autoCalibrate = false, takeoffWaitMs = 3000, climbPercent = 40,
            climbDurationMs = 100, commandGapMs = 50, landingWaitMs = 1000)
        val manual = FakeFlightControl()
        val path = FakeFlightControl()
        FlightLaunch(manual, pause = { manual.advance(it) }).run("172.16.10.1", settings)
        PathMission(path, pause = { path.advance(it) }).run(listOf(MissionStep(.16f, 0f)), "172.16.10.1", settings)
        assertEquals(5150L, manual.now)
        assertEquals(FlightEvent(5000, "climb", .4f, duration = 100), manual.events.first { it.action == "climb" })
        assertEquals(manual.events.filter { it.action != "neutral" },
            path.events.takeWhile { it.action != "right" }.filter { it.action != "neutral" })
        assertFalse(manual.events.any { it.action == "calibrate" })
    }

    @Test fun failedConnectionNeverCalibratesOrTakesOff() {
        val flight = FakeFlightControl().apply { connectSucceeds = false }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { FlightLaunch(flight, pause = { flight.advance(it) }).run("172.16.10.1", FlightSequenceSettings()) }
        }
        assertFalse(flight.pending)
        assertFalse(flight.events.any { it.action in setOf("calibrate", "takeoff", "climb") })
        assertEquals(listOf("Connection"), flight.checkedPhases)
    }

    @Test fun cancellingPendingConnectDoesNotCompleteItLater() {
        val flight = FakeFlightControl()
        assertThrows(CancellationException::class.java) {
            runBlocking { FlightLaunch(flight, pause = { throw CancellationException("cancel") })
                .run("172.16.10.1", FlightSequenceSettings()) }
        }
        flight.advance(10000)
        assertFalse(flight.connected)
        assertFalse(flight.pending)
        assertFalse(flight.events.any { it.action == "takeoff" })
    }

    @Test fun autoConnectOffRequiresAnExistingConnection() {
        val flight = FakeFlightControl()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { FlightLaunch(flight, pause = { flight.advance(it) })
                .run("172.16.10.1", FlightSequenceSettings(autoConnect = false)) }
        }
        assertFalse(flight.events.any { it.action == "connect" || it.action == "takeoff" })
    }
}
