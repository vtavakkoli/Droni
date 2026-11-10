package at.droni.video

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Remux Elfie H.264 into MP4 without decoding and re-encoding the camera.
 * The drone provides no reliable timing/size telemetry; timestamps are based on
 * arrival time and initial format defaults to 640x480, as on the flight deck.
 * Files are finalized only after a frame has been written.
 *
 * Copyright (c) 2026 Dr. Vahid Tavakkoli.
 */
class ElfieMp4Recorder(
    private val context: Context,
    sps: ByteArray,
    pps: ByteArray
) {
    private val name = "Droni_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        .format(Date()) + ".mp4"
    private val isModern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    private val resolver = context.contentResolver
    private var uri: Uri? = null
    private var file: File? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var muxer: MediaMuxer? = null
    private var track = -1
    private var frameCount = 0
    private val startNs = SystemClock.elapsedRealtimeNanos()
    private var lastPtsUs = -1L
    private var closed = false
    private var current = ArrayList<ByteArray>()
    private var containsSlice = false
    private var currentKeyframe = false

    init {
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 640, 480)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(startCode(sps)))
            format.setByteBuffer("csd-1", ByteBuffer.wrap(startCode(pps)))
            if (isModern) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES + "/Droni")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val out = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create MediaStore movie")
                uri = out
                descriptor = context.contentResolver.openFileDescriptor(out, "rw")
                    ?: error("Could not open MP4 for writing")
                muxer = MediaMuxer(descriptor!!.fileDescriptor,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } else {
                val root = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?: error("External app video storage unavailable")
                val folder = File(root, "Droni")
                check(folder.exists() || folder.mkdirs()) { "Cannot make movie directory" }
                val output = File(folder, name)
                file = output
                muxer = MediaMuxer(output.absolutePath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }
            track = muxer!!.addTrack(format)
            muxer!!.start()
        } catch (error: Exception) {
            abort()
            throw error
        }
    }

    /** Network NALs (Annex-B) may arrive in arbitrary TCP pieces. */
    fun push(nal: ByteArray) {
        if (closed) return
        val length = if (nal.size >= 4 && nal[2] == 0.toByte()) 4 else 3
        if (nal.size <= length) return
        val type = nal[length].toInt() and 31
        if (type == 7 || type == 8) return // SPS/PPS already in MediaFormat
        if (type == 9) {
            flushFrame()
            return
        }
        val isSlice = type == 1 || type == 5
        if (isSlice && containsSlice && H264SliceParser.firstMacroblockIsZero(nal)) {
            flushFrame()
        }
        // Do not accumulate non-picture NALs before the first slice.
        if (!isSlice && current.isEmpty()) return
        current.add(startCode(nal))
        if (isSlice) {
            containsSlice = true
            if (type == 5) currentKeyframe = true
        }
        if (current.sumOf { it.size } > 512_000) {
            // A malicious/corrupt frame cannot exhaust app memory.
            current.clear()
            containsSlice = false
            currentKeyframe = false
        }
    }

    private fun flushFrame() {
        if (closed || !containsSlice || current.isEmpty()) {
            current.clear(); containsSlice = false; currentKeyframe = false
            return
        }
        val data = current.fold(ByteArray(0)) { acc, nal -> acc + nal }
        val pts = ((SystemClock.elapsedRealtimeNanos() - startNs) / 1000)
            .coerceAtLeast(lastPtsUs + 1)
        val flags = if (currentKeyframe) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        val info = MediaCodec.BufferInfo().apply { set(0, data.size, pts, flags) }
        muxer!!.writeSampleData(track, ByteBuffer.wrap(data), info)
        frameCount++
        lastPtsUs = pts
        current.clear()
        containsSlice = false
        currentKeyframe = false
    }

    /** Return the new video URI for opening with the Android video player. */
    fun finish(): Uri? {
        if (closed) return uri
        return try {
            flushFrame()
            closed = true
            if (frameCount == 0) {
                abortInternal()
                null
            } else {
                muxer?.stop()
                muxer?.release()
                muxer = null
                descriptor?.close()
                descriptor = null
                if (isModern) {
                    uri?.let {
                        resolver.update(it, ContentValues().apply {
                            put(MediaStore.Video.Media.IS_PENDING, 0)
                        }, null, null)
                    }
                } else {
                    file?.let { output ->
                        MediaScannerConnection.scanFile(context,
                            arrayOf(output.absolutePath), arrayOf("video/mp4"), null)
                    }
                }
                uri ?: file?.let { Uri.fromFile(it) }
            }
        } catch (_: Exception) {
            abortInternal()
            null
        }
    }

    fun abort() {
        if (closed) return
        closed = true
        abortInternal()
    }

    private fun abortInternal() {
        try { muxer?.stop() } catch (_: Exception) {}
        try { muxer?.release() } catch (_: Exception) {}
        muxer = null
        try { descriptor?.close() } catch (_: Exception) {}
        descriptor = null
        try { uri?.let { resolver.delete(it, null, null) } } catch (_: Exception) {}
        try { file?.delete() } catch (_: Exception) {}
    }

    private fun startCode(data: ByteArray): ByteArray {
        if (data.size >= 4 && data[2] == 0.toByte() && data[3] == 1.toByte()) return data
        if (data.size >= 3 && data[2] == 1.toByte()) return byteArrayOf(0) + data
        return byteArrayOf(0, 0, 0, 1) + data
    }
}

/** Decode first_mb_in_slice Exp-Golomb from a H.264 slice (ignoring escape 0x03). */
internal object H264SliceParser {
    fun firstMacroblockIsZero(nal: ByteArray): Boolean {
        val start = if (nal.size > 3 && nal[2] == 0.toByte()) 4 else 3
        if (nal.size <= start + 1) return false
        val rbsp = ArrayList<Int>()
        var zeros = 0
        for (i in start + 1 until minOf(nal.size, start + 20)) {
            val v = nal[i].toInt() and 0xff
            if (zeros >= 2 && v == 3) {
                zeros = 0; continue
            }
            rbsp += v
            zeros = if (v == 0) zeros + 1 else 0
        }
        // Exp-Golomb ue(v)=0 begins with the first bit set.
        return rbsp.isNotEmpty() && (rbsp[0] and 0x80) != 0
    }
}
