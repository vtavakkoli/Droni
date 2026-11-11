package at.droni.mission

import at.droni.settings.FlightSequenceSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PathMissionTest {
    private val settings = FlightSequenceSettings()
    private val route = listOf(MissionStep(.16f, 0f), MissionStep(0f, .16f))
    private val host = "172.16.10.1"

    @Test fun disconnectedLaunchUsesTenFiveFiveTimingSpeedOneAndChecksEveryStage() = runBlocking {
        val flight = FakeFlightControl()
        val progress = mutableListOf<Float>()
        PathMission(flight, pause = { flight.advance(it) }).run(route, host, settings, onProgress = { progress += it })
        assertEquals(listOf(
            FlightEvent(0, "connect"), FlightEvent(10000, "speed", 1f), FlightEvent(10000, "calibrate"),
            FlightEvent(15000, "takeoff"), FlightEvent(20000, "climb", 1f, duration = 200),
            FlightEvent(20400, "right", .16f, 0f), FlightEvent(20780, "right", 0f, .16f),
            FlightEvent(21160, "land")
        ), flight.events.filter { it.action != "neutral" })
        assertEquals(26160L, flight.now)
        assertEquals(listOf("Connection", "Calibration", "Takeoff", "Climb", "Path 1", "Path 2", "Landing"), flight.checkedPhases)
        assertEquals(listOf(0f, .5f, 1f), progress)
        assertTrue(flight.events.contains(FlightEvent(20200, "neutral")))
        assertTrue(flight.events.contains(FlightEvent(20580, "neutral")))
        assertTrue(flight.events.contains(FlightEvent(20960, "neutral")))
    }

    @Test fun cancellationAtEveryWaitStopsFutureCommandsAndNeutralizes() {
        for (cancelAt in 1..10) {
            val flight = FakeFlightControl()
            var waits = 0
            assertThrows(CancellationException::class.java) {
                runBlocking {
                    PathMission(flight, pause = {
                        flight.advance(it)
                        if (++waits == cancelAt) throw CancellationException("Operator cancelled")
                    }).run(route, host, settings)
                }
            }
            assertEquals(cancelAt, waits)
            assertFalse(flight.takeoffRequested)
            assertEquals("neutral", flight.events.last().action)
            if (cancelAt <= 2) assertFalse(flight.events.any { it.action == "takeoff" || it.action == "land" })
            if (cancelAt <= 5) assertFalse(flight.events.any { it.action == "right" })
        }
    }

    @Test fun disconnectDuringLaunchOrClimbPreventsFurtherMovement() {
        for (disconnectAt in 1..5) {
            val flight = FakeFlightControl()
            var waits = 0
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    PathMission(flight, pause = {
                        flight.advance(it)
                        if (++waits == disconnectAt) flight.connected = false
                    }).run(route, host, settings)
                }
            }
            assertFalse(flight.events.any { it.action == "right" || it.action == "land" })
            assertEquals("neutral", flight.events.last().action)
        }
    }

    @Test fun motorStopDuringClimbIsNotOverriddenByLandingOrRoute() {
        val flight = FakeFlightControl()
        var waits = 0
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                PathMission(flight, pause = {
                    flight.advance(it)
                    if (++waits == 4) flight.takeoffRequested = false
                }).run(route, host, settings)
            }
        }
        assertFalse(flight.events.any { it.action == "right" || it.action == "land" })
        assertEquals("neutral", flight.events.last().action)
    }

    @Test fun invalidOrAlreadyActiveFlightNeverStartsAMission() {
        val invalidRoutes = listOf(emptyList(), listOf(MissionStep(Float.NaN, 0f)),
            listOf(MissionStep(1f, 0f)), listOf(MissionStep(0f, 0f, 5000L)))
        for (invalid in invalidRoutes) {
            val flight = FakeFlightControl()
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { PathMission(flight, pause = { }).run(invalid, host, settings) }
            }
            assertTrue(flight.events.isEmpty())
        }
        val active = FakeFlightControl().apply { connected = true; takeoffRequested = true }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { PathMission(active, pause = { }).run(route, host, settings) }
        }
        assertTrue(active.events.isEmpty())
    }
}
