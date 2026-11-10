package at.tavakkoli.droni

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import at.tavakkoli.droni.net.DroneController
import at.tavakkoli.droni.protocol.ElfieProtocol
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

private val Navy = Color(0xFF0D1728)
private val Panel = Color(0xFF17283F)
private val Accent = Color(0xFF40B8F6)
private val Pale = Color(0xFFDFEBF6)
private val Warning = Color(0xFFFFBF69)
private val Danger = Color(0xFFE84A5F)

/** Native Android version of Dr. Vahid Tavakkoli's JJRC Elfie controller. */
class MainActivity : ComponentActivity() {
    private lateinit var controller: DroneController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = DroneController(applicationContext)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    surface = Panel,
                    background = Navy,
                    onSurface = Pale,
                    onBackground = Pale,
                )
            ) {
                FlightDeck(controller)
            }
        }
    }

    // Never leave stale non-neutral controls active when another app appears.
    override fun onStop() {
        controller.disconnect()
        super.onStop()
    }

    override fun onDestroy() {
        controller.close()
        super.onDestroy()
    }
}

@Composable
private fun FlightDeck(drone: DroneController) {
    var host by remember { mutableStateOf(ElfieProtocol.DEFAULT_HOST) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Full screen FPV live video stream background
        CameraSurface(drone, Modifier.fillMaxSize())

        if (!drone.connected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Connect to drone Wi-Fi for live video stream",
                    color = Color.LightGray,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Top Translucent Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Panel.copy(alpha = 0.85f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text("DRONI", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Accent)
                Text("JJRC ELFIE", fontSize = 9.sp, color = Color.LightGray)
            }
            Text("•", color = Color.Gray, fontSize = 12.sp)
            Column(Modifier.weight(1f)) {
                Text("FPV TCP 8888 • H.264", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(drone.videoStatus, color = Accent, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Drone IPv4:", fontSize = 11.sp, color = Pale)
            BasicTextField(
                value = host, onValueChange = { host = it.take(32) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                modifier = Modifier.width(115.dp).background(Navy.copy(alpha = 0.8f), RoundedCornerShape(6.dp)).padding(6.dp),
                enabled = !drone.connected
            )
            if (drone.connected) {
                OutlinedButton(
                    onClick = { drone.disconnect() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) { Text("Disconnect", fontSize = 11.sp) }
            } else {
                Button(
                    onClick = { drone.connect(host) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) { Text("Connect", fontSize = 11.sp) }
            }
        }

        // Action Controls Panel (Translucent Overlay, Top Center below Header)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
                .widthIn(max = 440.dp)
                .background(Panel.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = drone.connected && !drone.takeoffRequested,
                    onClick = drone::takeoff,
                    contentPadding = PaddingValues(0.dp)
                ) { Text("Take off", fontSize = 10.sp) }
                Button(
                    modifier = Modifier.weight(1f), enabled = drone.connected,
                    onClick = drone::land,
                    contentPadding = PaddingValues(0.dp)
                ) { Text("Land", fontSize = 10.sp) }
                Button(
                    modifier = Modifier.weight(1.2f), enabled = drone.connected,
                    onClick = drone::emergencyStop,
                    colors = ButtonDefaults.buttonColors(containerColor = Danger),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("MOTOR STOP", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f), enabled = drone.connected,
                    onClick = drone::cycleSpeed,
                    contentPadding = PaddingValues(0.dp)
                ) { Text("Speed ${drone.speedMode}/2", fontSize = 10.sp) }
                OutlinedButton(
                    modifier = Modifier.weight(1f), enabled = drone.connected,
                    onClick = drone::toggleHeadless,
                    contentPadding = PaddingValues(0.dp)
                ) { Text("Headless ${if (drone.headless) "ON" else "OFF"}", fontSize = 10.sp) }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = drone.connected && !drone.takeoffRequested,
                    onClick = drone::calibrate,
                    contentPadding = PaddingValues(0.dp)
                ) { Text("Calibrate", fontSize = 10.sp) }
                TextButton(
                    onClick = drone::toggleReverse,
                    enabled = drone.connected && !drone.takeoffRequested,
                    contentPadding = PaddingValues(0.dp)
                ) { Text(if (drone.reverseDirection) "REV ON" else "NORMAL", fontSize = 10.sp) }
            }
        }

        // Transparent Left Virtual Joystick (YAW / ALTITUDE)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 24.dp, start = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("YAW / ALTITUDE", color = Pale.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            VirtualJoystick(
                modifier = Modifier.size(175.dp),
                enabled = drone.connected,
                onMove = drone::setLeft
            )
            Spacer(Modifier.height(2.dp))
            Text("left/right • up/down", fontSize = 9.sp, color = Color.LightGray.copy(alpha = 0.7f))
        }

        // Transparent Right Virtual Joystick (ROLL / PITCH - moved 20dp left)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("ROLL / PITCH", color = Pale.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            VirtualJoystick(
                modifier = Modifier.size(175.dp),
                enabled = drone.connected,
                onMove = drone::setRight
            )
            Spacer(Modifier.height(2.dp))
            Text("strafe • forward/back", fontSize = 9.sp, color = Color.LightGray.copy(alpha = 0.7f))
        }

        // Bottom Status Bar (Translucent Overlay)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Navy.copy(alpha = 0.8f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(drone.linkStatus, fontSize = 10.sp, color = Warning, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Text("UDP 8080 • 40 Hz", color = Color.LightGray, fontSize = 9.sp)
        }
    }
}

@Composable
private fun CameraSurface(drone: DroneController, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        drone.video.setSurface(holder.surface)
                    }
                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) = Unit
                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        drone.video.setSurface(null)
                    }
                })
            }
        }
    )
}

/** Virtual joystick with 50%, 75%, 100% rate zones and soft snapping for precise flight control. */
@Composable
private fun VirtualJoystick(
    modifier: Modifier,
    enabled: Boolean,
    onMove: (Float, Float) -> Unit
) {
    var position by remember { mutableStateOf(Offset.Zero) }
    val currentOnMove by rememberUpdatedState(onMove)

    fun snapToZone(value: Float): Float {
        val s = sign(value)
        return when (abs(value)) {
            in 0.47f..0.53f -> s * 0.50f
            in 0.72f..0.78f -> s * 0.75f
            in 0.94f..1.00f -> s * 1.00f
            else -> value
        }
    }

    val gesture = Modifier.pointerInput(enabled) {
        if (!enabled) {
            position = Offset.Zero
            currentOnMove(0f, 0f)
            return@pointerInput
        }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            fun update(point: Offset) {
                val r = min(size.width, size.height) * 0.41f
                if (r <= 0f) return
                var rawX = ((point.x - size.width / 2f) / r).coerceIn(-1f, 1f)
                var rawY = ((size.height / 2f - point.y) / r).coerceIn(-1f, 1f)
                val norm = sqrt((rawX * rawX) + (rawY * rawY)).coerceAtLeast(1f)
                rawX /= norm
                rawY /= norm

                val finalX = snapToZone(rawX)
                val finalY = snapToZone(rawY)

                position = Offset(finalX, finalY)
                currentOnMove(position.x, position.y)
            }
            try {
                update(down.position)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id && it.pressed }
                        ?: break
                    update(change.position)
                    change.consume()
                }
            } finally {
                position = Offset.Zero
                currentOnMove(0f, 0f)
            }
        }
    }

    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(gesture)
                .background(Color(0x3017283F), CircleShape)
                .border(1.5.dp, Color(0x90456581), CircleShape)
        ) {
            val radius = min(size.width, size.height) * 0.41f

            // 50% Control Zone Ring (Precision hover/glide)
            drawCircle(Color(0x50FFBF69), radius * 0.50f, style = Stroke(1.5.dp.toPx()))
            // 75% Control Zone Ring (Medium speed)
            drawCircle(Color(0x5040B8F6), radius * 0.75f, style = Stroke(1.5.dp.toPx()))
            // 100% Control Zone Ring (Max speed)
            drawCircle(Color(0x5020364E), radius, style = Stroke(2.dp.toPx()))

            // Crosshair axes
            drawLine(Color(0x60456581), Offset(center.x - radius, center.y),
                Offset(center.x + radius, center.y), 1.dp.toPx())
            drawLine(Color(0x60456581), Offset(center.x, center.y - radius),
                Offset(center.x, center.y + radius), 1.dp.toPx())

            // Knob representation with dynamic zone feedback color
            val knob = Offset(
                center.x + position.x * radius,
                center.y - position.y * radius
            )
            val currentMagnitude = sqrt(position.x * position.x + position.y * position.y)
            val knobColor = when {
                !enabled -> Color.DarkGray.copy(alpha = 0.5f)
                currentMagnitude >= 0.90f -> Danger.copy(alpha = 0.9f)
                currentMagnitude >= 0.65f -> Accent.copy(alpha = 0.9f)
                currentMagnitude >= 0.40f -> Warning.copy(alpha = 0.9f)
                else -> Accent.copy(alpha = 0.75f)
            }

            drawCircle(knobColor, radius * 0.26f, center = knob)
            drawCircle(Color.White.copy(alpha = 0.6f), radius * 0.26f, center = knob, style = Stroke(2.dp.toPx()))
        }

        // Active Zone Readout Overlay in Center
        val mag = sqrt(position.x * position.x + position.y * position.y)
        if (mag > 0.1f) {
            val zoneText = when {
                mag >= 0.90f -> "100%"
                mag >= 0.65f -> "75%"
                mag >= 0.40f -> "50%"
                else -> "${(mag * 100).toInt()}%"
            }
            Text(
                text = zoneText,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0xB00D1728), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}
