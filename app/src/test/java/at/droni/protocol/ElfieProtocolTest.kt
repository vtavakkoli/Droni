package at.droni.protocol

import org.junit.Assert.*
import org.junit.Test

/** Python-side original Android protocol regression vectors. */
class ElfieProtocolTest {
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Test fun neutralManual() {
        assertEquals("ff08003f403f1010100009",
            ElfieProtocol.encode(ElfieProtocol.Input(altitudeHold = false)).hex())
    }

    @Test fun neutralAltitudeHold() {
        assertEquals("ff087e3f403f901010000b",
            ElfieProtocol.encode(ElfieProtocol.Input()).hex())
    }

    @Test fun actionsAndChecksum() {
        for ((name, input, bit) in listOf(
            Triple("takeoff", ElfieProtocol.Input(takeoff = true), 0x40),
            Triple("land", ElfieProtocol.Input(land = true), 0x80),
            Triple("stop", ElfieProtocol.Input(emergencyStop = true), 0x20),
            Triple("headless", ElfieProtocol.Input(headless = true), 0x10)
        )) {
            val packet = ElfieProtocol.encode(input)
            assertEquals(name, 11, packet.size)
            assertEquals(name, bit, packet[9].toInt() and 0xff)
            assertEquals(name, 0xff, packet.drop(1).sumOf { it.toInt() and 0xff } and 0xff)
        }
    }

    @Test fun speedModesAndDirection() {
        for (speed in 0..2) {
            val packet = ElfieProtocol.encode(ElfieProtocol.Input(speedMode = speed))
            assertEquals(speed, packet[9].toInt() and 3)
        }
        val neutral = ElfieProtocol.encode(ElfieProtocol.Input())
        assertTrue((ElfieProtocol.encode(ElfieProtocol.Input(pitch = 0.75f))[4].toInt() and 0xff) <
            (neutral[4].toInt() and 0xff))
        assertTrue((ElfieProtocol.encode(ElfieProtocol.Input(roll = 0.75f))[5].toInt() and 0xff) >
            (neutral[5].toInt() and 0xff))
        assertTrue(ElfieProtocol.reverseForFirmware("UDP_720", "V2.4.0"))
        assertFalse(ElfieProtocol.reverseForFirmware("UDP_1080", "V2.4.0"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectConflictingActions() {
        ElfieProtocol.encode(ElfieProtocol.Input(takeoff = true, land = true))
    }

    @Test fun clampSticks() {
        val packet = ElfieProtocol.encode(ElfieProtocol.Input(roll = 900f, throttle = 900f))
        assertEquals(127, packet[5].toInt() and 0xff)
        assertEquals(254, packet[2].toInt() and 0xff)
    }

    @Test fun handleNanInput() {
        val packet = ElfieProtocol.encode(ElfieProtocol.Input(roll = Float.NaN, pitch = Float.NaN))
        assertEquals(11, packet.size)
        val neutral = ElfieProtocol.encode(ElfieProtocol.Input())
        assertEquals(neutral[4], packet[4])
        assertEquals(neutral[5], packet[5])
    }

    @Test fun originalAppPacketFormatterReferenceVectors() {
        // Source-derived from ControlActivity.SendThread in the supplied original app;
        // these are packet formatter fixtures, not captured flight acknowledgements.
        val speedOne = ElfieProtocol.Input(speedMode = 1)
        val fixtures = listOf(
            speedOne to "ff087e3f403f901010010a",
            speedOne.copy(calibrate = true) to "ff087e3f403fd0101001ca",
            speedOne.copy(takeoff = true) to "ff087e3f403f90101041ca",
            speedOne.copy(land = true) to "ff087e3f403f901010818a",
            speedOne.copy(emergencyStop = true) to "ff087e3f403f90101021ea",
            speedOne.copy(headless = true) to "ff087e3f403f90101011fa",
            speedOne.copy(reverseDirection = true) to "ff087e4040409010100108",
            speedOne.copy(speedMode = 2, trimYaw = 12, trimPitch = 20, trimRoll = 8) to "ff087e3f403f8c0c080219",
            speedOne.copy(throttle = .5f, yaw = -.5f, pitch = .5f, roll = .5f) to "ff08be1f205f90101001ea"
        )
        for ((input, expected) in fixtures) assertEquals(input.toString(), expected, ElfieProtocol.encode(input).hex())
    }
}
