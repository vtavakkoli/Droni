package at.droni.mission

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ManualTakeoffTest {
    private class Control : ManualFlightControl {
        override var connected = true
        override var takeoffRequested = false
        var now = 0L
        val events = mutableListOf<Pair<Long, String>>()
        override fun neutral() { events += now to "neutral" }
        override fun calibrate() { events += now to "calibrate" }
        override fun takeoff() { takeoffRequested = true; events += now to "takeoff" }
    }

    @Test fun calibrationStartsFirstAndTakeoffWaitsTheFullThreeSeconds() = runBlocking {
        val control = Control()
        ManualTakeoff(control, pause = { duration ->
            assertEquals(3000L, duration)
            assertEquals(listOf(0L to "neutral", 0L to "calibrate"), control.events)
            control.now = 2999L
            assertFalse(control.takeoffRequested)
            control.now++
        }).run()
        assertEquals(listOf(0L to "neutral", 0L to "calibrate", 3000L to "takeoff"), control.events)
    }

    @Test fun cancellationDuringCalibrationNeverQueuesTakeoff() {
        val control = Control()
        assertThrows(CancellationException::class.java) {
            runBlocking {
                ManualTakeoff(control, pause = { throw CancellationException("operator cancelled") }).run()
            }
        }
        assertFalse(control.takeoffRequested)
        assertFalse(control.events.any { it.second == "takeoff" })
    }

    @Test fun disconnectDuringTheWaitPreventsTakeoff() = runBlocking {
        val control = Control()
        ManualTakeoff(control, pause = { control.now += it; control.connected = false }).run()
        assertFalse(control.takeoffRequested)
        assertFalse(control.events.any { it.second == "takeoff" })
    }

    @Test fun supersededRequestCannotTakeOffAfterTheWait() = runBlocking {
        val control = Control()
        var current = true
        ManualTakeoff(control, pause = { control.now += it; current = false }).run { current }
        assertFalse(control.takeoffRequested)
    }

    @Test fun disconnectedOrAlreadyActiveControlsDoNotCalibrateOrWait() = runBlocking {
        for (connected in listOf(false, true)) {
            val control = Control().apply { this.connected = connected; takeoffRequested = connected }
            ManualTakeoff(control, pause = { fail("Must not wait or launch") }).run()
            assertTrue(control.events.isEmpty())
        }
    }

    @Test fun anExistingTakeoffDuringCalibrationIsNotRequestedTwice() = runBlocking {
        val control = Control()
        ManualTakeoff(control, pause = { control.takeoffRequested = true }).run()
        assertTrue(control.takeoffRequested)
        assertFalse(control.events.any { it.second == "takeoff" })
    }
}
