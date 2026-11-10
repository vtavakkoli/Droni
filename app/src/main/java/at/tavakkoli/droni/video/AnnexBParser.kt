package at.tavakkoli.droni.video

/** Bounded parser for H.264 Annex-B NAL units over arbitrary TCP chunk boundaries. */
class AnnexBParser(
    private val maxBuffer: Int = 1024 * 1024,
    private val onNal: (ByteArray) -> Unit
) {
    private var bytes = ByteArray(65536)
    private var length = 0

    private fun startAt(index: Int): Int =
        when {
            index + 3 < length &&
                bytes[index] == 0.toByte() && bytes[index + 1] == 0.toByte() &&
                bytes[index + 2] == 0.toByte() && bytes[index + 3] == 1.toByte() -> 4
            index + 2 < length &&
                bytes[index] == 0.toByte() && bytes[index + 1] == 0.toByte() &&
                bytes[index + 2] == 1.toByte() -> 3
            else -> 0
        }

    private fun findStart(from: Int): Int {
        for (i in from until length - 2) {
            if (startAt(i) != 0) return i
        }
        return -1
    }

    fun append(data: ByteArray, count: Int = data.size) {
        require(count in 0..data.size)
        // A corrupt stream must never grow memory without bound.
        var actualCount = count
        var dataOffset = 0
        if (actualCount > maxBuffer) {
            dataOffset = actualCount - maxBuffer
            actualCount = maxBuffer
            length = 0
        } else if (length + actualCount > maxBuffer) {
            length = 0
        }
        val needed = length + actualCount
        if (needed > bytes.size) {
            bytes = bytes.copyOf(minOf(maxBuffer, maxOf(needed, bytes.size * 2)))
        }
        data.copyInto(bytes, length, dataOffset, dataOffset + actualCount)
        length += actualCount
        var first = findStart(0)
        if (first == -1) {
            if (length > 3) {
                bytes.copyInto(bytes, 0, length - 3, length)
                length = 3
            }
            return
        }
        while (true) {
            val next = findStart(first + startAt(first))
            if (next == -1) break
            if (next > first + startAt(first)) {
                onNal(bytes.copyOfRange(first, next))
            }
            first = next
        }
        if (first > 0) {
            bytes.copyInto(bytes, 0, first, length)
            length -= first
        }
    }
}
