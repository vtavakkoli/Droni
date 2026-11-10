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
import kotlin.math.min
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
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Accent, surface = Panel, background = Navy,
                onSurface = Pale, onBackground = Pale
            )) {
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
    Column(
        modifier = Modifier.fillMaxSize().background(Navy).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text("DRONI", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Accent)
                Text("JJRC ELFIE  •  by Dr. Vahid Tavakkoli",
                    fontSize = 10.sp, color = Color.LightGray)
            }
            Spacer(Modifier.weight(1f))
            Text("Drone IPv4:", fontSize = 12.sp)
            BasicTextField(
                value = host, onValueChange = { host = it.take(32) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                modifier = Modifier.width(135.dp).background(Panel, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                enabled = !drone.connected
            )
            if (drone.connected) {
                OutlinedButton(onClick = { drone.disconnect() }) { Text("Disconnect") }
            } else {
                Button(onClick = { drone.connect(host) }) { Text("Connect") }
            }
        }

        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .background(Color.Black, RoundedCornerShape(14.dp))
            ) {
                CameraSurface(drone, Modifier.fillMaxSize())
                Column(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                    Text("FPV  •  TCP 8888  •  H.264", color = Color.White,
                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(drone.videoStatus, color = Accent, fontSize = 11.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (!drone.connected) {
                    Text("Connect to drone Wi-Fi for live video",
                        modifier = Modifier.align(Alignment.Center).padding(15.dp),
                        color = Color.LightGray, fontSize = 13.sp)
                }
                Text(
                    "No verified battery, GPS, obstacle or altitude telemetry",
                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                    color = Warning, fontSize = 10.sp
                )
            }

            Column(
                modifier = Modifier.width(366.dp).fillMaxHeight()
                    .background(Panel, RoundedCornerShape(14.dp)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("YAW / ALTITUDE", color = Pale, fontSize = 11.sp)
                        VirtualJoystick(Modifier.fillMaxWidth().weight(1f),
                            enabled = drone.connected,
                            onMove = drone::setLeft)
                        Text("left / right  •  up / down", fontSize = 9.sp, color = Color.LightGray)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ROLL / PITCH", color = Pale, fontSize = 11.sp)
                        VirtualJoystick(Modifier.fillMaxWidth().weight(1f),
                            enabled = drone.connected,
                            onMove = drone::setRight)
                        Text("strafe  •  forward / back", fontSize = 9.sp, color = Color.LightGray)
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = drone.connected && !drone.takeoffRequested,
                        onClick = drone::takeoff,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) { Text("Take off", fontSize = 11.sp) }
                    Button(
                        modifier = Modifier.weight(1f), enabled = drone.connected,
                        onClick = drone::land,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) { Text("Land", fontSize = 11.sp) }
                    Button(
                        modifier = Modifier.weight(1.25f), enabled = drone.connected,
                        onClick = drone::emergencyStop,
                        colors = ButtonDefaults.buttonColors(containerColor = Danger),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) { Text("MOTOR STOP", fontSize = 10.sp, color = Color.White) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f), enabled = drone.connected,
                        onClick = drone::cycleSpeed,
                        contentPadding = PaddingValues(4.dp)
                    ) { Text("Speed ${drone.speedMode}/2", fontSize = 11.sp) }
                    OutlinedButton(
                        modifier = Modifier.weight(1f), enabled = drone.connected,
                        onClick = drone::toggleHeadless,
                        contentPadding = PaddingValues(4.dp)
                    ) { Text("Headless ${if (drone.headless) "ON" else "OFF"}", fontSize = 11.sp) }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = drone.connected && !drone.takeoffRequested,
                        onClick = drone::calibrate,
                        contentPadding = PaddingValues(4.dp)
                    ) { Text("Calibrate", fontSize = 11.sp) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Firmware-specific yaw / roll reversal", fontSize = 10.sp,
                        modifier = Modifier.weight(1f), color = Color.LightGray)
                    TextButton(onClick = drone::toggleReverse,
                        enabled = drone.connected && !drone.takeoffRequested) {
                        Text(if (drone.reverseDirection) "REVERSE ON" else "NORMAL",
                            fontSize = 11.sp)
                    }
                }
                Text(
                    "Emergency stop removes lift. Manual control only; never trust video for collision avoidance.",
                    fontSize = 10.sp, color = Warning, maxLines = 2
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(drone.linkStatus, fontSize = 11.sp, color = Warning,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Text("UDP 8080 • 40 Hz • unacknowledged", color = Color.LightGray, fontSize = 10.sp)
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
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int,
                        width: Int, height: Int) = Unit
                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        drone.video.setSurface(null)
                    }
                })
            }
        }
    )
}

/** Two independent multi-touch surfaces; pointer loss resets the stick to zero. */
@Composable
private fun VirtualJoystick(
    modifier: Modifier,
    enabled: Boolean,
    onMove: (Float, Float) -> Unit
) {
    var position by remember { mutableStateOf(Offset.Zero) }
    val currentOnMove by rememberUpdatedState(onMove)
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
                val x = ((point.x - size.width / 2f) / r).coerceIn(-1f, 1f)
                val y = ((size.height / 2f - point.y) / r).coerceIn(-1f, 1f)
                val norm = sqrt(x * x + y * y).coerceAtLeast(1f)
                position = Offset(x / norm, y / norm)
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
    Canvas(
        modifier = modifier.aspectRatio(1f).then(gesture)
            .border(1.dp, Color(0xFF456581), CircleShape)
    ) {
        val radius = min(size.width, size.height) * 0.41f
        drawCircle(Color(0xFF20364E), radius, style = Stroke(3.dp.toPx()))
        drawLine(Color(0xFF456581), Offset(center.x - radius, center.y),
            Offset(center.x + radius, center.y), 1.dp.toPx())
        drawLine(Color(0xFF456581), Offset(center.x, center.y - radius),
            Offset(center.x, center.y + radius), 1.dp.toPx())
        val knob = Offset(center.x + position.x * radius,
            center.y - position.y * radius)
        drawCircle(if (enabled) Accent else Color.DarkGray, radius * 0.26f,
            center = knob)
        drawCircle(Color.White.copy(alpha = 0.35f), radius * 0.26f,
            center = knob, style = Stroke(2.dp.toPx()))
    }
}
