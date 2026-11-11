package at.droni.mission

import at.droni.settings.PathFlightSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PathLaunchTest {
    @Test fun pathStartupUsesNewDefaultsBeforeTheRoute() = runBlocking {
        val flight = FakeFlightControl()
        PathLaunch(flight, pause = { flight.advance(it) }).run("172.16.10.1", PathFlightSettings())
        assertEquals(11600L, flight.now)
        assertEquals(listOf("connect", "speed", "calibrate", "takeoff", "climb"),
            flight.events.filter { it.action != "neutral" }.map { it.action })
        assertEquals(listOf("Connection", "Calibration", "Takeoff", "Climb"), flight.checkedPhases)
        assertTrue(flight.takeoffRequested)
        assertFalse(flight.events.any { it.action == "land" })
    }

    @Test fun existingConnectionSkipsConnectionWaitButStillAppliesConfiguredSpeed() = runBlocking {
        val flight = FakeFlightControl().apply { connected = true }
        PathLaunch(flight, pause = { flight.advance(it) }).run("172.16.10.1", PathFlightSettings())
        assertEquals(6600L, flight.now)
        assertFalse(flight.events.any { it.action == "connect" })
        assertEquals(FlightEvent(0, "speed", 1f), flight.events.first())
    }

    @Test fun customPathSettingsControlStandaloneStartupAndMissionIdentically() = runBlocking {
        val settings = PathFlightSettings(speedMode = 2, connectionWaitMs = 2000,
            autoCalibrate = false, takeoffWaitMs = 3000, climbPercent = 40,
            climbDurationMs = 100, commandGapMs = 50, landingWaitMs = 1000)
        val startup = FakeFlightControl()
        val path = FakeFlightControl()
        PathLaunch(startup, pause = { startup.advance(it) }).run("172.16.10.1", settings)
        PathMission(path, pause = { path.advance(it) }).run(listOf(MissionStep(.16f, 0f)), "172.16.10.1", settings)
        assertEquals(5150L, startup.now)
        assertEquals(FlightEvent(5000, "climb", .4f, duration = 100), startup.events.first { it.action == "climb" })
        assertEquals(startup.events.filter { it.action != "neutral" },
            path.events.takeWhile { it.action != "right" }.filter { it.action != "neutral" })
        assertFalse(startup.events.any { it.action == "calibrate" })
    }

    @Test fun failedConnectionNeverCalibratesOrTakesOff() {
        val flight = FakeFlightControl().apply { connectSucceeds = false }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { PathLaunch(flight, pause = { flight.advance(it) }).run("172.16.10.1", PathFlightSettings()) }
        }
        assertFalse(flight.pending)
        assertFalse(flight.events.any { it.action in setOf("calibrate", "takeoff", "climb") })
        assertEquals(listOf("Connection"), flight.checkedPhases)
    }

    @Test fun cancellingPendingConnectDoesNotCompleteItLater() {
        val flight = FakeFlightControl()
        assertThrows(CancellationException::class.java) {
            runBlocking { PathLaunch(flight, pause = { throw CancellationException("cancel") })
                .run("172.16.10.1", PathFlightSettings()) }
        }
        flight.advance(10000)
        assertFalse(flight.connected)
        assertFalse(flight.pending)
        assertFalse(flight.events.any { it.action == "takeoff" })
    }

    @Test fun autoConnectOffRequiresAnExistingConnection() {
        val flight = FakeFlightControl()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { PathLaunch(flight, pause = { flight.advance(it) })
                .run("172.16.10.1", PathFlightSettings(autoConnect = false)) }
        }
        assertFalse(flight.events.any { it.action == "connect" || it.action == "takeoff" })
    }
}
