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
import at.droni.video.ElfieVideo
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sends ORIGINAL Android Elfie control packets at 40 Hz over an explicit Wi-Fi network.
 * UDP local send success does NOT imply a radio/drone acknowledgement.
 * Written by Dr. Vahid Tavakkoli.
 */
class DroneController(private val context: Context) {
    private companion object { const val DEFAULT_SPEED_MODE = 1 }

    private val ui = Handler(Looper.getMainLooper())
    private val axes = AtomicReference(ElfieProtocol.Input(speedMode = DEFAULT_SPEED_MODE))
    private val packetCounter = AtomicInteger(0)
    private val lock = Any()

    @Volatile private var transmitting = false
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var address: InetSocketAddress? = null
    @Volatile private var commandExpires = 0L
    private var worker: ScheduledExecutorService? = null

    var connected by mutableStateOf(false)
        private set
    var linkStatus by mutableStateOf("Connect to the drone Wi-Fi, then tap Connect.")
        private set
    var videoStatus by mutableStateOf("WAITING")
        private set
    var sentCount by mutableIntStateOf(0)
        private set
    var speedMode by mutableIntStateOf(DEFAULT_SPEED_MODE)
        private set
    var headless by mutableStateOf(false)
        private set
    var reverseDirection by mutableStateOf(false)
        private set
    var takeoffRequested by mutableStateOf(false)
        private set

    val video = ElfieVideo { text -> ui.post { videoStatus = text } }

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

    fun connect(host: String) {
        if (connected) return
        linkStatus = "Connecting..."
        Executors.newSingleThreadExecutor().execute {
            val target = try {
                val ip = InetAddress.getByName(host.trim())
                require(ip is Inet4Address) { "Enter an IPv4 address" }
                InetSocketAddress(ip, ElfieProtocol.CONTROL_PORT)
            } catch (error: Exception) {
                ui.post { linkStatus = "Invalid drone IPv4 address: ${error.message}" }
                return@execute
            }
            val network = wifiNetwork()
            if (network == null) {
                ui.post { linkStatus = "No Wi-Fi network. Join the JJRC Elfie Wi-Fi in Android Settings." }
                return@execute
            }
            try {
                val udp = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(0))
                }
                try {
                    network.bindSocket(udp)
                } catch (error: Exception) {
                    udp.close()
                    throw error
                }
                synchronized(lock) {
                    socket = udp
                    address = target
                    axes.set(ElfieProtocol.Input(speedMode = speedMode, reverseDirection = reverseDirection))
                    packetCounter.set(0)
                    sentCount = 0
                    commandExpires = 0L
                    lastErrorMsg = null
                    transmitting = true
                    val scheduler = Executors.newSingleThreadScheduledExecutor { task ->
                        Thread(task, "Droni-UDP-40Hz").apply { isDaemon = true }
                    }
                    worker = scheduler
                    // Fixed delay prevents a backlog of rapid control packets after Android
                    // pauses or caches the process; resume with one tick at a time.
                    scheduler.scheduleWithFixedDelay(::sendTick, 0, ElfieProtocol.PERIOD_MS,
                        TimeUnit.MILLISECONDS)
                }
                ui.post {
                    connected = true
                    linkStatus = "UDP transmitting on drone Wi-Fi • no drone ACK"
                }
                video.start(network, target.address.hostAddress ?: host.trim())
            } catch (error: Exception) {
                synchronized(lock) {
                    transmitting = false
                    worker?.shutdownNow()
                    worker = null
                    socket?.close()
                    socket = null
                }
                ui.post {
                    connected = false
                    linkStatus = "UDP Wi-Fi setup failed: ${error.message}"
                }
            }
        }
    }

    /** Called only from the 40 Hz executor, never from the UI thread. */
    private fun sendTick() {
        if (!transmitting) return
        try {
            val now = SystemClock.elapsedRealtime()
            val packetInput = axes.updateAndGet { prior ->
                if (now > commandExpires &&
                    (prior.takeoff || prior.land || prior.emergencyStop || prior.calibrate)) {
                    prior.copy(takeoff = false, land = false, emergencyStop = false,
                        calibrate = false)
                } else prior
            }
            val payload = ElfieProtocol.encode(packetInput)
            val udp = socket ?: return
            val to = address ?: return
            udp.send(DatagramPacket(payload, payload.size, to))
            lastErrorMsg = null
            val count = packetCounter.incrementAndGet()
            if (count % 10 == 0) ui.post {
                if (transmitting) {
                    sentCount = count
                    linkStatus = "UDP packets sent: $count • not flight acknowledgements"
                }
            }
        } catch (error: Exception) {
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
        axes.updateAndGet { transform(it) }
    }

    /** X = yaw, Y = ascent/descent (positive is upwards). */
    fun setLeft(x: Float, y: Float) {
        if (!connected) return
        change { current ->
            if (current.takeoff || current.land || current.emergencyStop) current
            else current.copy(yaw = x.coerceIn(-1f, 1f),
                throttle = y.coerceIn(-1f, 1f))
        }
    }

    /** X = roll, Y = pitch (positive is forwards). */
    fun setRight(x: Float, y: Float) {
        if (!connected) return
        change { current ->
            if (current.takeoff || current.land || current.emergencyStop) current
            else current.copy(roll = x.coerceIn(-1f, 1f),
                pitch = y.coerceIn(-1f, 1f))
        }
    }

    /** Prevent stale stick deflection when a pointer, Activity, or network is lost. */
    fun neutral() = change { it.neutralAxes() }

    private fun pulse(transform: (ElfieProtocol.Input) -> ElfieProtocol.Input) {
        neutral()
        commandExpires = SystemClock.elapsedRealtime() + 1000L
        change(transform)
    }

    fun takeoff() {
        if (!connected || takeoffRequested) return
        takeoffRequested = true
        pulse { it.copy(altitudeHold = true, takeoff = true, land = false,
            emergencyStop = false) }
        linkStatus = "TAKEOFF requested, altitude hold selected (not acknowledged)"
    }

    fun land() {
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
    fun calibrate() {
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
        video.stop()
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
            address = null
            axes.set(ElfieProtocol.Input(speedMode = DEFAULT_SPEED_MODE, reverseDirection = reverseDirection))
            commandExpires = 0L
        }
        takeoffRequested = false
        speedMode = DEFAULT_SPEED_MODE
        headless = false
        connected = false
        linkStatus = "Disconnected. Neutral attempted; landing NOT guaranteed."
    }

    fun close() = disconnect()
}
