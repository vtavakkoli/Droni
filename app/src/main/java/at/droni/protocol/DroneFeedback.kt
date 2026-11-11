package at.droni.protocol

/** Records observed UDP responses. No flight-command ACK format is verified for this firmware. */
internal class DroneFeedback {
    data class Packet(val sourcePort: Int, val localPort: Int, val prefix: String, val identity: String?)
    private var count = 0L
    private val packets = ArrayDeque<Packet>()
    private val checks = ArrayDeque<String>()

    @Synchronized fun reset() { count = 0; packets.clear(); checks.clear() }
    @Synchronized fun count(): Long = count
    @Synchronized fun record(data: ByteArray, sourcePort: Int, localPort: Int) {
        count++
        val text = data.take(160).toByteArray().toString(Charsets.US_ASCII).trimEnd('\u0000', '\r', '\n')
        val identity = text.takeIf { value ->
            value.all { it.code in 32..126 } &&
                (listOf("VGA", "MJVG", "VSVG", "UDP", "720P", "1080P", "HI2K", "HI4K", "drone")
                    .any { value.startsWith(it) } || value.matches(Regex("V\\d+\\.\\d+\\.\\d+[A-Za-z0-9_-]*")))
        }
        packets.addLast(Packet(sourcePort, localPort,
            data.take(48).joinToString(" ") { "%02x".format(it.toInt() and 0xff) }, identity))
        while (packets.size > 12) packets.removeFirst()
    }

    @Synchronized fun check(phase: String, since: Long): String {
        val received = (count - since).coerceAtLeast(0)
        val result = "$phase: $received UDP replies · flight ACK unverified"
        checks.addLast(result)
        while (checks.size > 32) checks.removeFirst()
        return result
    }

    @Synchronized fun report(): String = buildString {
        appendLine("Droni response checks")
        appendLine("UDP packets received from the selected drone: $count")
        appendLine("A response, identity string, video frame or echoed packet is not a verified flight ACK.")
        checks.forEach { appendLine(it) }
        packets.forEach { packet ->
            appendLine("UDP ${packet.sourcePort} → local ${packet.localPort}: ${packet.identity ?: packet.prefix}")
        }
    }
}
