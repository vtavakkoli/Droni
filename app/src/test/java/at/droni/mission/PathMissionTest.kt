package at.droni.mission

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PathMissionTest {
    private data class Event(val at: Long, val action: String, val x: Float = 0f, val y: Float = 0f)

    private class Flight : PathFlightControl {
        var now = 0L
        override var connected = true
        override var takeoffRequested = false
        val events = mutableListOf<Event>()
        override fun neutral() { events += Event(now, "neutral") }
        override fun calibrate() { events += Event(now, "calibrate") }
        override fun takeoff() { takeoffRequested = true; events += Event(now, "takeoff") }
        override fun setLeft(yaw: Float, ascent: Float) { events += Event(now, "left", yaw, ascent) }
        override fun setRight(roll: Float, pitch: Float) { events += Event(now, "right", roll, pitch) }
        override fun land() { takeoffRequested = false; events += Event(now, "land") }
    }

    private val route = listOf(MissionStep(.16f, 0f), MissionStep(0f, .16f))

    @Test fun calibrationTakeoffClimbAndRouteHaveExactRequestedTiming() = runBlocking {
        val flight = Flight()
        val progress = mutableListOf<Float>()
        PathMission(flight, pause = { flight.now += it }).run(route, onProgress = { progress += it })
        assertEquals(listOf(
            Event(0, "neutral"), Event(0, "calibrate"),
            Event(1000, "takeoff"),
            Event(2000, "left", 0f, 1f), Event(2200, "left", 0f, 0f),
            Event(2400, "right", .16f, 0f), Event(2580, "right", 0f, 0f),
            Event(2780, "right", 0f, .16f), Event(2960, "right", 0f, 0f),
            Event(3160, "neutral"), Event(3160, "land")
        ), flight.events)
        assertEquals(listOf(0f, .5f, 1f), progress)
    }

    @Test fun cancellationAtEveryWaitStopsFutureCommandsAndNeutralizes() {
        // Calibration, takeoff, climb, climb gap, path movement and path gap.
        for (cancelAt in 1..8) {
            val flight = Flight()
            var waits = 0
            assertThrows(CancellationException::class.java) {
                runBlocking {
                    PathMission(flight, pause = {
                        flight.now += it
                        if (++waits == cancelAt) throw CancellationException("Operator cancelled")
                    }).run(route)
                }
            }
            assertEquals(cancelAt, waits)
            if (cancelAt == 1) {
                assertEquals(Event(flight.now, "neutral"), flight.events.last())
                assertFalse(flight.events.any { it.action == "takeoff" || it.action == "land" })
            } else {
                assertEquals(Event(flight.now, "neutral"), flight.events[flight.events.lastIndex - 1])
                assertEquals(Event(flight.now, "land"), flight.events.last())
            }
            if (cancelAt <= 4) assertFalse(flight.events.any { it.action == "right" })
        }
    }

    @Test fun disconnectDuringLaunchOrClimbPreventsFurtherMovement() {
        for (disconnectAt in 1..3) {
            val flight = Flight()
            var waits = 0
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    PathMission(flight, pause = {
                        flight.now += it
                        if (++waits == disconnectAt) flight.connected = false
                    }).run(route)
                }
            }
            assertFalse(flight.events.any { it.action == "right" || it.action == "land" })
            assertEquals("neutral", flight.events.last().action)
            if (disconnectAt == 1) assertFalse(flight.events.any { it.action == "takeoff" })
        }
    }

    @Test fun motorStopDuringClimbIsNotOverriddenByLandingOrRoute() {
        val flight = Flight()
        var waits = 0
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                PathMission(flight, pause = {
                    flight.now += it
                    if (++waits == 3) flight.takeoffRequested = false
                }).run(route)
            }
        }
        assertFalse(flight.events.any { it.action == "right" || it.action == "land" })
        assertEquals("neutral", flight.events.last().action)
    }

    @Test fun invalidOrAlreadyActiveFlightNeverStartsAMission() {
        val invalidRoutes = listOf(emptyList(), listOf(MissionStep(Float.NaN, 0f)),
            listOf(MissionStep(1f, 0f)), listOf(MissionStep(0f, 0f, 5000L)))
        for (invalid in invalidRoutes) {
            val flight = Flight()
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { PathMission(flight, pause = { }).run(invalid) }
            }
            assertTrue(flight.events.isEmpty())
        }
        val active = Flight().apply { takeoffRequested = true }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { PathMission(active, pause = { }).run(route) }
        }
        assertTrue(active.events.isEmpty())
    }
}
