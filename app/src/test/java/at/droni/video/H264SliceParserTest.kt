package at.droni.video

import org.junit.Assert.*
import org.junit.Test

class H264SliceParserTest {
    @Test fun firstMacroblockZero() {
        assertTrue(H264SliceParser.firstMacroblockIsZero(
            byteArrayOf(0, 0, 0, 1, 0x65, 0x80.toByte())
        ))
        assertTrue(H264SliceParser.firstMacroblockIsZero(
            byteArrayOf(0, 0, 1, 0x65, 0x80.toByte())
        ))
        assertFalse(H264SliceParser.firstMacroblockIsZero(
            byteArrayOf(0, 0, 0, 1, 0x41, 0x40)
        ))
    }
}