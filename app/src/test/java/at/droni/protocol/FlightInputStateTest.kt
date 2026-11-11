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

    @Test fun fullUpExpiresOnThePacketSenderWithoutAnotherUiUpdate() {
        val state = FlightInputState(ElfieProtocol.Input())
        state.pulse(0) { it.copy(takeoff = true) }
        state.climbPulse(5000, 1f, 300)
        assertEquals(1f, state.read(5299).throttle, 0f)
        assertEquals(0f, state.read(5300).throttle, 0f)
        assertEquals(126, ElfieProtocol.encode(state.read(10000))[2].toInt() and 0xff)
    }

    @Test fun pathMovementExpiresButManualJoystickInputRemainsOperatorControlled() {
        val state = FlightInputState(ElfieProtocol.Input())
        state.pathPulse(1000, .75f, 0f, 180)
        assertEquals(.75f, state.read(1179).roll, 0f)
        assertEquals(0f, state.read(1180).roll, 0f)
        state.update(1200) { it.copy(roll = .4f, throttle = .3f) }
        assertEquals(.4f, state.read(10000).roll, 0f)
        assertEquals(.3f, state.read(10000).throttle, 0f)
        state.update(10001) { it.neutralAxes() }
        assertEquals(0f, state.read(10001).roll, 0f)
    }

    @Test fun emergencyStopOverridesTimedClimbAndKeepsItsOwnDeadline() {
        val state = FlightInputState(ElfieProtocol.Input())
        state.climbPulse(0, 1f, 200)
        state.pulse(100) { it.copy(emergencyStop = true) }
        assertEquals(0f, state.read(200).throttle, 0f)
        assertTrue(state.read(1099).emergencyStop)
        assertFalse(state.read(1100).emergencyStop)
    }
}
