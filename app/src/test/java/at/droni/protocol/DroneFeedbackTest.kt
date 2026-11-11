package at.droni.protocol

import org.junit.Assert.*
import org.junit.Test

class DroneFeedbackTest {
    @Test fun identityVideoAndEchoPacketsAreResponsesWithoutBecomingFlightAcknowledgements() {
        val log = DroneFeedback()
        log.record("VGA\u0000".toByteArray(), 8080, 40001)
        log.record(ElfieProtocol.encode(ElfieProtocol.Input(takeoff = true)), 8080, 8081)
        val checked = log.check("Takeoff", 0)
        assertTrue(checked.contains("2 UDP replies"))
        assertTrue(checked.contains("ACK unverified"))
        assertTrue(log.report().contains("VGA"))
        assertFalse(checked.contains("confirmed"))
    }

    @Test fun checksOnlyCountNewPacketsAndReportNoReplyHonestly() {
        val log = DroneFeedback()
        log.record("V2.4.1".toByteArray(), 8080, 40001)
        val before = log.count()
        assertTrue(log.check("Calibration", before).contains("0 UDP replies"))
        log.record(byteArrayOf(0, 1), 8081, 8081)
        assertTrue(log.check("Climb", before).contains("1 UDP replies"))
    }

    @Test fun reportHasBoundedHistoryAndResetStartsANewConnection() {
        val log = DroneFeedback()
        repeat(100) {
            log.record(ByteArray(6000) { 0xff.toByte() }, 8080, 8081)
            log.check("Path $it", 0)
        }
        assertTrue(log.report().length < 10000)
        assertFalse(log.report().contains("Path 0:"))
        log.reset()
        assertEquals(0L, log.count())
        assertFalse(log.report().contains("Path 99:"))
    }
}
