package at.droni.protocol

import org.junit.Assert.*
import org.junit.Test

class FlightInputStateTest {
    @Test fun climbIsAcceptedAtOneSecondWithoutAnInterveningUdpTick() {
        val state = FlightInputState(ElfieProtocol.Input(speedMode = 1))
        state.pulse(0) { it.copy(takeoff = true) }
        assertTrue(state.read(999).takeoff)
        // The flight UI gates axes while command flags are present. Expiry must happen before this transform.
        state.update(1000) {
            if (it.takeoff || it.land || it.emergencyStop) it else it.copy(throttle = 1f)
        }
        val input = state.read(1000)
        assertFalse(input.takeoff)
        assertEquals(1f, input.throttle, 0f)
        assertEquals(0xfe, ElfieProtocol.encode(input)[2].toInt() and 0xff)
    }

    @Test fun takeoffAfterCalibrationDoesNotCarryTheCalibrationFlag() {
        val state = FlightInputState(ElfieProtocol.Input())
        state.pulse(0) { it.copy(calibrate = true) }
        state.pulse(1000) { it.copy(takeoff = true) }
        val takeoff = state.read(1000)
        assertTrue(takeoff.takeoff)
        assertFalse(takeoff.calibrate)
        assertFalse(takeoff.land)
        assertFalse(takeoff.emergencyStop)
    }

    @Test fun replacementPulseOwnsItsDeadlineAndNeutralizesAxes() {
        val state = FlightInputState(ElfieProtocol.Input(roll = .7f, throttle = 1f, speedMode = 2, headless = true))
        state.pulse(0) { it.copy(takeoff = true) }
        state.pulse(900) { it.copy(emergencyStop = true) }
        val stopped = state.read(1000)
        assertTrue(stopped.emergencyStop)
        assertFalse(stopped.takeoff)
        assertEquals(0f, stopped.roll, 0f)
        assertEquals(0f, stopped.throttle, 0f)
        assertEquals(2, stopped.speedMode)
        assertTrue(stopped.headless)
        assertFalse(state.read(1900).emergencyStop)
    }

    @Test fun resetRemovesPreviousCommandsAndDeflections() {
        val state = FlightInputState(ElfieProtocol.Input())
        state.pulse(0) { it.copy(takeoff = true) }
        state.reset(ElfieProtocol.Input(speedMode = 2))
        assertEquals(ElfieProtocol.Input(speedMode = 2), state.read(10))
    }
}
