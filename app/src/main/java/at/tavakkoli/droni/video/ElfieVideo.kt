package at.tavakkoli.droni.video

import android.media.MediaCodec
import android.media.MediaFormat
import android.net.Network
import android.os.Build
import android.os.SystemClock
import android.view.Surface
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Original Elfie TCP 8888 greeting + raw Annex-B H.264 to Android's hardware
 * MediaCodec decoder. Media3/ExoPlayer do not directly support this proprietary
 * TCP greeting and uncontainerized video elementary stream.
 *
 * Written by Dr. Vahid Tavakkoli.
 */
class ElfieVideo(private val status: (String) -> Unit) {
    companion object {
        private const val HELLO_HEX =
            "495464000000580000009bf89049c926884d4f922b3b33ba7eceacef63f77157ab2f53e3f768ecd9e18547b8c22e21d01bfb6b3de3" +
            "25a27b8fb3acef63f77157ab2f53e3f768ecd9e185eb20be383aab05a8c2a71f2c906d93f72a85e7356effe1b8f5af097f9147f87e"
        private val HELLO: ByteArray = HELLO_HEX.chunked(2)
            .map { it.toInt(16).toByte() }.toByteArray()
        private const val GREETING_BYTES = 106
    }

    private val running = AtomicBoolean(false)
    @Volatile private var displaySurface: Surface? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var receiver: Thread? = null

    fun setSurface(surface: Surface?) {
        displaySurface = surface
        if (surface == null) {
            // Stop decoding to a destroyed Surface; worker will reconnect when ready.
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    fun start(network: Network, host: String) {
        stop()
        running.set(true)
        receiver = Thread({
            while (running.get()) {
                try {
                    val surface = displaySurface
                    if (surface == null || !surface.isValid) {
                        status("WAITING FOR VIDEO SURFACE")
                        Thread.sleep(200)
                        continue
                    }
                    val connection = network.socketFactory.createSocket()
                    socket = connection
                    connection.connect(InetSocketAddress(host, 8888), 3000)
                    connection.soTimeout = 1000
                    connection.getOutputStream().write(HELLO)
                    connection.getOutputStream().flush()
                    val input = DataInputStream(connection.getInputStream())
                    input.readFully(ByteArray(GREETING_BYTES))
                    status("VIDEO CONNECTED • WAITING FOR H.264")
                    decodeStream(input, surface)
                } catch (error: InterruptedException) {
                    break
                } catch (error: Exception) {
                    if (running.get()) status("VIDEO RETRY: ${error.message?.take(65) ?: "read error"}")
                } finally {
                    try { socket?.close() } catch (_: Exception) {}
                    socket = null
                }
                if (running.get()) {
                    try { Thread.sleep(1200) } catch (_: InterruptedException) { break }
                }
            }
            status("VIDEO STOPPED")
        }, "Droni-Elfie-H264").apply {
            isDaemon = true
            start()
        }
    }

    private fun decodeStream(input: DataInputStream, surface: Surface) {
        var codec: MediaCodec? = null
        var sps: ByteArray? = null
        var pps: ByteArray? = null
        var decoded = 0
        var lastReport = SystemClock.elapsedRealtime()
        var lastData = lastReport

        fun drain(decoder: MediaCodec) {
            val info = MediaCodec.BufferInfo()
            repeat(8) {
                val index = decoder.dequeueOutputBuffer(info, 0)
                if (index >= 0) {
                    val hasImage = info.size > 0
                    decoder.releaseOutputBuffer(index, hasImage)
                    if (hasImage) decoded++
                } else return
            }
        }

        try {
            val parser = AnnexBParser { nal ->
                val startLength = if (nal.size >= 4 && nal[2] == 0.toByte()) 4 else 3
                if (nal.size <= startLength) return@AnnexBParser
                val kind = nal[startLength].toInt() and 0x1f
                if (kind == 7) sps = nal
                if (kind == 8) pps = nal

                if (codec == null && sps != null && pps != null) {
                    val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 640, 480)
                    format.setByteBuffer("csd-0", ByteBuffer.wrap(sps!!))
                    format.setByteBuffer("csd-1", ByteBuffer.wrap(pps!!))
                    if (Build.VERSION.SDK_INT >= 30) format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                    val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                    try {
                        decoder.configure(format, surface, null, 0)
                        decoder.start()
                        codec = decoder
                        status("H.264 decoder ready")
                    } catch (error: Exception) {
                        decoder.release()
                        throw error
                    }
                }

                val decoder = codec ?: return@AnnexBParser
                if (kind == 7 || kind == 8 || kind == 9) return@AnnexBParser
                val index = decoder.dequeueInputBuffer(5_000)
                if (index >= 0) {
                    val buffer = decoder.getInputBuffer(index)
                    if (buffer != null && buffer.capacity() >= nal.size) {
                        buffer.clear()
                        buffer.put(nal)
                        decoder.queueInputBuffer(index, 0, nal.size,
                            System.nanoTime() / 1000, 0)
                    } else decoder.queueInputBuffer(index, 0, 0,
                        System.nanoTime() / 1000, 0)
                }
                drain(decoder)
            }

            val chunk = ByteArray(16384)
            while (running.get() && displaySurface === surface && surface.isValid) {
                val count = try { input.read(chunk) } catch (_: SocketTimeoutException) { 0 }
                if (count < 0) throw java.io.EOFException("video connection closed")
                if (count > 0) {
                    lastData = SystemClock.elapsedRealtime()
                    parser.append(chunk, count)
                }
                val now = SystemClock.elapsedRealtime()
                if (now - lastData > 5000L) throw java.io.IOException("camera stream stalled")
                if (now - lastReport >= 1500L) {
                    status(if (decoded > 0) "VIDEO LIVE • decoded ${decoded} frames"
                        else "H.264 stream waiting for frames / SPS-PPS")
                    lastReport = now
                }
            }
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
        }
    }

    fun stop() {
        running.set(false)
        try { socket?.close() } catch (_: Exception) {}
        receiver?.interrupt()
        // The network read is interruptible via the socket close above.
        try { receiver?.join(500) } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        receiver = null
        socket = null
    }
}
