package at.droni.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import at.droni.protocol.ElfieProtocol
import at.droni.protocol.FlightInputState
import at.droni.protocol.DroneFeedback
import at.droni.mission.PathFlightControl
import at.droni.mission.ManualFlightControl
import at.droni.video.ElfieVideo
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Sends ORIGINAL Android Elfie control packets at 40 Hz over an explicit Wi-Fi network.
 * UDP local send success does NOT imply a radio/drone acknowledgement.
 * Written by Dr. Vahid Tavakkoli.
 */
class DroneController(private val context: Context) : PathFlightControl, ManualFlightControl {
    private companion object { const val DEFAULT_SPEED_MODE = 1 }

    @Volatile private var defaultSpeedMode = DEFAULT_SPEED_MODE

    /** Persisted preference is applied only while disarmed and disconnected. */
    fun setDefaultSpeedMode(mode: Int) {
        require(mode in 0..2) { "Speed mode must be 0, 1 or 2" }
        defaultSpeedMode = mode
        if (!connected && !takeoffRequested) {
            speedMode = mode
            change { it.copy(speedMode = mode) }
        }
    }

    private val ui = Handler(Looper.getMainLooper())
    private val axes = FlightInputState(ElfieProtocol.Input(speedMode = DEFAULT_SPEED_MODE))
    private val packetCounter = AtomicInteger(0)
    private val lock = Any()
    private val generation = AtomicInteger(0)
    private val connectionExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Droni-connect").apply { isDaemon = true }
    }
    private val feedback = DroneFeedback()
    private val feedbackUiAt = AtomicLong(0)
    @Volatile private var notificationSocket: DatagramSocket? = null

    @Volatile private var transmitting = false
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var address: InetSocketAddress? = null
    private var worker: ScheduledExecutorService? = null

    override var connected by mutableStateOf(false)
        private set
    var connecting by mutableStateOf(false)
        private set
    var receivedCount by mutableStateOf(0L)
        private set
    var feedbackStatus by mutableStateOf("No UDP replies yet · flight ACK unverified")
        private set
    var linkStatus by mutableStateOf("Connect to the drone Wi-Fi, then tap Connect.")
        private set
    var videoStatus by mutableStateOf("WAITING")
        private set
    var recording by mutableStateOf(false)
        private set
    var recordingStatus by mutableStateOf("Not recording")
        private set
    var sentCount by mutableIntStateOf(0)
        private set
    override var speedMode by mutableIntStateOf(DEFAULT_SPEED_MODE)
        private set
    var headless by mutableStateOf(false)
        private set
    var reverseDirection by mutableStateOf(false)
        private set
    override var takeoffRequested by mutableStateOf(false)
        private set

    val video = ElfieVideo(context,
        { text -> ui.post { videoStatus = text } },
        { active, message -> ui.post {
            recording = active
            recordingStatus = message
        } }
    )

    fun toggleRecording() {
        if (!connected) return
        if (recording) video.endRecording() else video.beginRecording()
    }

    /** Select Wi-Fi even if Android routes default internet traffic through mobile data. */
    private fun wifiNetwork(): Network? {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        manager.activeNetwork?.let { network ->
            if (manager.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) return network
        }

        // The drone's Wi-Fi has no internet; a default-network-only query can miss it.
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val selected = AtomicReference<Network?>()
        val available = CountDownLatch(1)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (selected.compareAndSet(null, network)) available.countDown()
            }
        }
        manager.registerNetworkCallback(request, callback)
        try {
            available.await(2, TimeUnit.SECONDS)
            return selected.get()
        } finally {
            manager.unregisterNetworkCallback(callback)
        }
    }

    @Volatile private var lastErrorMsg: String? = null

    override fun connect(host: String) {
        if (connected || connecting || connectionExecutor.isShutdown) return
        val attempt = generation.incrementAndGet()
        connecting = true
        linkStatus = "Connecting to drone Wi-Fi…"
        feedback.reset()
        receivedCount = 0
        feedbackStatus = "Listening for drone replies · flight ACK unverified"
        connectionExecutor.execute {
            var pending: DatagramSocket? = null
            try {
                val ip = InetAddress.getByName(host.trim())
                require(ip is Inet4Address) { "Enter an IPv4 address" }
                val target = InetSocketAddress(ip, ElfieProtocol.CONTROL_PORT)
                val network = wifiNetwork() ?: error("No Wi-Fi network. Join the Elfie Wi-Fi first.")
                if (attempt != generation.get()) return@execute
                val udp = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(0))
                    soTimeout = 250
                }
                pending = udp
                network.bindSocket(udp)
                synchronized(lock) {
                    if (attempt != generation.get()) return@synchronized
                    socket = udp
                    address = target
                    axes.reset(ElfieProtocol.Input(speedMode = speedMode, reverseDirection = reverseDirection))
                    packetCounter.set(0)
                    lastErrorMsg = null
                    transmitting = true
                    worker = Executors.newSingleThreadScheduledExecutor { task ->
                        Thread(task, "Droni-UDP-40Hz").apply { isDaemon = true }
                    }.also { it.scheduleWithFixedDelay({ sendTick(attempt) }, 0, ElfieProtocol.PERIOD_MS, TimeUnit.MILLISECONDS) }
                    listenForReplies(udp, ip, attempt)
                    // The original app also listens on local UDP 8081. Failure to bind this
                    // optional port does not turn a usable control socket into a failed connection.
                    val extra = DatagramSocket(null)
                    try {
                        extra.bind(InetSocketAddress(8081))
                        extra.soTimeout = 250
                        network.bindSocket(extra)
                        notificationSocket = extra
                        listenForReplies(extra, ip, attempt)
                    } catch (_: Exception) {
                        extra.close()
                        ui.post { if (attempt == generation.get())
                            feedbackStatus = "Control replies monitored · local 8081 unavailable · ACK unverified" }
                    }
                    video.start(network, ip.hostAddress ?: host.trim())
                    // Original Android read-only identity/version queries; never treat their replies as flight ACKs.
                    for (query in listOf(byteArrayOf(0x0f), byteArrayOf(0x28))) {
                        try { udp.send(DatagramPacket(query, query.size, target)) } catch (_: Exception) { }
                    }
                    ui.post {
                        if (attempt == generation.get() && transmitting) {
                            connected = true
                            connecting = false
                            sentCount = 0
                            linkStatus = "Control socket active · checking incoming UDP replies"
                        }
                    }
                    pending = null
                }
            } catch (error: Exception) {
                if (attempt == generation.get()) {
                    synchronized(lock) {
                        transmitting = false
                        worker?.shutdownNow(); worker = null
                        socket?.close(); socket = null
                        notificationSocket?.close(); notificationSocket = null
                    }
                    ui.post {
                        if (attempt == generation.get()) {
                            connecting = false
                            connected = false
                            linkStatus = "Connection failed: ${error.message}"
                        }
                    }
                }
            } finally { pending?.close() }
        }
    }

    private fun listenForReplies(udp: DatagramSocket, expectedPeer: InetAddress, attempt: Int) {
        Thread({
            val bytes = ByteArray(4096)
            while (attempt == generation.get() && !udp.isClosed) {
                val packet = DatagramPacket(bytes, bytes.size)
                try {
                    udp.receive(packet)
                    if (packet.address != expectedPeer || attempt != generation.get()) continue
                    feedback.record(packet.data.copyOfRange(packet.offset, packet.offset + packet.length),
                        packet.port, udp.localPort)
                    val total = feedback.count()
                    val now = SystemClock.elapsedRealtime()
                    val previous = feedbackUiAt.get()
                    if (total == 1L || (now - previous >= 250 && feedbackUiAt.compareAndSet(previous, now))) {
                        ui.post {
                            if (attempt == generation.get()) {
                                receivedCount = total
                                feedbackStatus = "RX $total UDP replies · flight ACK unverified"
                            }
                        }
                    }
                } catch (_: SocketTimeoutException) { /* remain responsive to cancellation */ }
                catch (_: Exception) { break }
            }
        }, "Droni-replies-${udp.localPort}").apply { isDaemon = true; start() }
    }

    override fun responseCount(): Long = feedback.count()

    override fun checkResponses(phase: String, since: Long): String = feedback.check(phase, since).also {
        receivedCount = feedback.count()
        feedbackStatus = it
    }

    fun feedbackReport(): String = "Drone: ${address?.address?.hostAddress ?: "not connected"}\n" + feedback.report()

    override fun cancelPendingConnection() {
        if (connecting && !connected) disconnect()
    }

    override fun setFlightSpeed(mode: Int) {
        require(mode in 0..2)
        check(connected && !takeoffRequested) { "Set launch speed while connected and disarmed" }
        speedMode = mode
        change { it.copy(speedMode = mode) }
    }

    override fun climbPulse(ascent: Float, durationMs: Long) {
        check(connected && takeoffRequested) { "A takeoff request is required before climb input" }
        axes.climbPulse(SystemClock.elapsedRealtime(), ascent, durationMs)
    }

    override fun pathPulse(roll: Float, pitch: Float, durationMs: Long) {
        check(connected && takeoffRequested) { "A takeoff request is required before path movement" }
        axes.pathPulse(SystemClock.elapsedRealtime(), roll, pitch, durationMs)
    }

    /** Called only from the 40 Hz executor, never from the UI thread. */
    private fun sendTick(attempt: Int) {
        if (!transmitting || attempt != generation.get()) return
        try {
            val now = SystemClock.elapsedRealtime()
            val packetInput = axes.read(now)
            val payload = ElfieProtocol.encode(packetInput)
            val udp = socket ?: return
            val to = address ?: return
            udp.send(DatagramPacket(payload, payload.size, to))
            lastErrorMsg = null
            val count = packetCounter.incrementAndGet()
            if (count % 10 == 0) ui.post {
                if (transmitting && attempt == generation.get()) {
                    sentCount = count
                    linkStatus = "UDP packets sent: $count • not flight acknowledgements"
                }
            }
        } catch (error: Exception) {
            if (attempt != generation.get()) return
            val msg = error.message ?: "send error"
            if (msg != lastErrorMsg) {
                lastErrorMsg = msg
                ui.post { linkStatus = "UDP send failed: $msg. Controls neutralized." }
            }
            neutral()
            // Keep retrying neutral packets; do NOT assert successful flight state.
        }
    }

    private fun change(transform: (ElfieProtocol.Input) -> ElfieProtocol.Input) {
        axes.update(SystemClock.elapsedRealtime(), transform)
    }

    /** X = yaw, Y = ascent/descent (positive is upwards). */
    fun setLeft(yaw: Float, ascent: Float) {
        if (!connected) return
        change { current ->
            if (current.takeoff || current.land || current.emergencyStop) current
            else current.copy(yaw = yaw.coerceIn(-1f, 1f),
                throttle = ascent.coerceIn(-1f, 1f))
        }
    }

    /** X = roll, Y = pitch (positive is forwards). */
    fun setRight(roll: Float, pitch: Float) {
        if (!connected) return
        change { current ->
            if (current.takeoff || current.land || current.emergencyStop) current
            else current.copy(roll = roll.coerceIn(-1f, 1f),
                pitch = pitch.coerceIn(-1f, 1f))
        }
    }

    /** Prevent stale stick deflection when a pointer, Activity, or network is lost. */
    override fun neutral() = change { it.neutralAxes() }

    private fun pulse(transform: (ElfieProtocol.Input) -> ElfieProtocol.Input) {
        axes.pulse(SystemClock.elapsedRealtime(), transform)
    }

    override fun takeoff() {
        if (!connected || takeoffRequested) return
        takeoffRequested = true
        pulse { it.copy(altitudeHold = true, takeoff = true, land = false,
            emergencyStop = false) }
        linkStatus = "TAKEOFF requested, altitude hold selected (not acknowledged)"
    }

    override fun land() {
        if (!connected) return
        takeoffRequested = false
        pulse { it.copy(land = true, takeoff = false, emergencyStop = false) }
        linkStatus = "LAND requested (not acknowledged)"
    }

    /** Immediately transmits stop flag; motors can lose lift and aircraft may fall. */
    fun emergencyStop() {
        if (!connected) return
        takeoffRequested = false
        pulse { it.copy(emergencyStop = true, takeoff = false, land = false) }
        linkStatus = "EMERGENCY MOTOR STOP requested — aircraft may fall"
    }

    /** Calibration only while disarmed and stationary. */
    override fun calibrate() {
        if (!connected || takeoffRequested) return
        pulse { it.copy(calibrate = true) }
        linkStatus = "Calibration requested (stationary drone, not acknowledged)"
    }

    fun toggleHeadless() {
        if (!connected) return
        headless = !headless
        change { it.copy(headless = headless) }
    }

    /** Only change axis reversal while disarmed. */
    fun toggleReverse() {
        if (!connected || takeoffRequested) return
        reverseDirection = !reverseDirection
        change { it.copy(reverseDirection = reverseDirection) }
    }

    fun cycleSpeed() {
        if (!connected) return
        speedMode = (speedMode + 1) % 3
        change { it.copy(speedMode = speedMode) }
    }

    /** A lifecycle shutdown is NOT a safe-landing acknowledgement. */
    fun disconnect() {
        generation.incrementAndGet()
        connecting = false
        synchronized(lock) {
            transmitting = false
            worker?.shutdownNow()
            worker = null
            val udp = socket
            val to = address
            if (udp != null && to != null) {
                try {
                    val neutralPacket = ElfieProtocol.encode(
                        ElfieProtocol.Input(altitudeHold = true))
                    repeat(3) {
                        udp.send(DatagramPacket(neutralPacket, neutralPacket.size, to))
                    }
                } catch (_: Exception) { /* no guarantee of delivery */ }
                udp.close()
            }
            socket = null
            notificationSocket?.close()
            notificationSocket = null
            address = null
            axes.reset(ElfieProtocol.Input(speedMode = defaultSpeedMode, reverseDirection = reverseDirection))
        }
        video.stop()
        takeoffRequested = false
        speedMode = defaultSpeedMode
        headless = false
        connected = false
        linkStatus = "Disconnected. Neutral attempted; landing NOT guaranteed."
    }

    fun close() {
        disconnect()
        connectionExecutor.shutdownNow()
    }
}
