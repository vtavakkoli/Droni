package at.droni.protocol

import kotlin.math.roundToInt

/**
 * Original Android JJRC Elfie 11-byte FF 08 flight-control packet, NOT the
 * unrelated 8-byte H37 protocol. Ported from
 * github.com/vtavakkoli/jjrc-elfie/android_protocol.py
 *
 * Written by Dr. Vahid Tavakkoli.
 */
object ElfieProtocol {
    const val CONTROL_PORT = 8080
    const val VIDEO_PORT = 8888
    const val DEFAULT_HOST = "172.16.10.1"
    const val PERIOD_MS = 25L

    private fun clamp(value: Float, low: Float, high: Float): Float =
        if (value.isNaN()) 0f else value.coerceIn(low, high)
    private fun stick(value: Float): Int =
        (63 + 64 * clamp(value, -1f, 1f)).roundToInt().coerceIn(0, 127)

    fun reverseForFirmware(deviceType: String, firmware: String): Boolean =
        deviceType == "UDP_720" && firmware in setOf("V2.3.9", "V2.4.0", "V2.4.1")

    data class Input(
        val roll: Float = 0f,
        val pitch: Float = 0f,
        val throttle: Float = 0f,
        val yaw: Float = 0f,
        val altitudeHold: Boolean = true,
        val speedMode: Int = 0,
        val headless: Boolean = false,
        val calibrate: Boolean = false,
        val emergencyStop: Boolean = false,
        val takeoff: Boolean = false,
        val land: Boolean = false,
        val reverseDirection: Boolean = false,
        val trimYaw: Int = 16,
        val trimPitch: Int = 16,
        val trimRoll: Int = 16
    ) {
        fun neutralAxes(): Input = copy(roll = 0f, pitch = 0f, throttle = 0f, yaw = 0f)
    }

    fun encode(input: Input): ByteArray {
        require(input.speedMode in 0..2) { "Speed must be 0, 1 or 2" }
        require(input.trimYaw in 0..32 && input.trimPitch in 0..32 && input.trimRoll in 0..32)
        require(!(input.takeoff && input.land)) { "Cannot take off and land together" }

        val b2 = (if (input.altitudeHold) stick(input.throttle) else
            (127f * clamp(input.throttle, 0f, 1f)).roundToInt()) * 2 and 0xff
        val leftX = stick(input.yaw)
        val rightY = stick(input.pitch)
        val rightX = stick(input.roll)
        val b3 = if (input.reverseDirection) 127 - leftX else leftX
        val b4 = 127 - rightY
        val b5 = if (input.reverseDirection) 127 - rightX else rightX
        val b6 = input.trimYaw or (if (input.altitudeHold) 0x80 else 0) or
            (if (input.calibrate) 0x40 else 0)
        val b7 = 32 - input.trimPitch
        val b8 = input.trimRoll
        val b9 = input.speedMode or
            (if (input.headless) 0x10 else 0) or
            (if (input.emergencyStop) 0x20 else 0) or
            (if (input.takeoff) 0x40 else 0) or
            (if (input.land) 0x80 else 0)

        val payload = intArrayOf(0xff, 0x08, b2, b3, b4, b5, b6, b7, b8, b9)
        val checksum = (0xff - payload.drop(1).sum()) and 0xff
        return (payload.toList() + checksum).map { it.toByte() }.toByteArray()
    }
}
