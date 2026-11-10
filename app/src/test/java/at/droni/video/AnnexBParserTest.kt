package at.droni.video

import org.junit.Assert.*
import org.junit.Test

class AnnexBParserTest {
    @Test fun fragmentedStartCodesAndFrames() {
        val result = mutableListOf<ByteArray>()
        val parser = AnnexBParser { result.add(it) }
        parser.append(byteArrayOf(8, 0, 0))
        parser.append(byteArrayOf(0, 1, 0x67, 1, 2, 0, 0))
        parser.append(byteArrayOf(1, 0x68, 3, 0, 0, 0, 1, 0x65, 4))
        assertEquals(2, result.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 1, 0x67, 1, 2), result[0])
        assertArrayEquals(byteArrayOf(0, 0, 1, 0x68, 3), result[1])
    }
    @Test fun discardGarbage() {
        val result = mutableListOf<ByteArray>()
        val parser = AnnexBParser(1024) { result.add(it) }
        parser.append(ByteArray(100) { 7 })
        parser.append(byteArrayOf(0, 0, 1, 0x67, 1, 0, 0, 1, 0x68, 2))
        assertEquals(1, result.size)
    }
    @Test fun handleExceedingMaxBuffer() {
        val result = mutableListOf<ByteArray>()
        val parser = AnnexBParser(64) { result.add(it) }
        val largeData = ByteArray(100) { 5 }
        parser.append(largeData)
        assertEquals(0, result.size)
    }
}
