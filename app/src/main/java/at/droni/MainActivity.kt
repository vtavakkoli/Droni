package at.droni

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import at.droni.net.DroneController
import at.droni.protocol.ElfieProtocol
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

private val Navy = Color(0xFF0A1220)
private val Panel = Color(0xFF16253A)
private val Accent = Color(0xFF47C5F7)
private val Pale = Color(0xFFE8F1FA)
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

    Box(Modifier.fillMaxSize().background(Navy)) {
        // Keep the FPV feed full-bleed; shade only its edges for readable controls.
        CameraSurface(drone, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Navy.copy(alpha = 0.55f),
                    0.30f to Color.Transparent,
                    0.62f to Color.Transparent,
                    1f to Navy.copy(alpha = 0.48f)
                )
            )
        )

        // Respect the notch, status bar, camera cutout and landscape navigation bar.
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val compact = maxWidth < 690.dp
            val stickSize = minOf(142.dp, maxWidth * 0.195f, maxHeight * 0.40f)
            val consoleWidth = minOf(376.dp, maxWidth - stickSize * 2 - 44.dp)

            if (!drone.connected) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Navy.copy(alpha = 0.86f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 24.dp, vertical = 13.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("CAMERA STANDBY", color = Pale, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Join JJRC Elfie Wi-Fi and connect", color = Pale.copy(alpha = 0.72f), fontSize = 10.sp)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                DeckHeader(
                    drone = drone,
                    host = host,
                    onHostChange = { host = it.take(32) },
                    compact = compact
                )

                // The central view stays open for video; controls live along the bottom.
                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StickDock(
                        modifier = Modifier.width(stickSize + 8.dp),
                        label = "01  YAW / ALT",
                        hint = "TURN  ·  CLIMB",
                        size = stickSize,
                        enabled = drone.connected,
                        onMove = drone::setLeft
                    )
                    FlightConsole(
                        drone = drone,
                        modifier = Modifier.width(consoleWidth)
                    )
                    StickDock(
                        modifier = Modifier.width(stickSize + 8.dp),
                        label = "02  ROLL / PITCH",
                        hint = "STRAFE  ·  MOVE",
                        size = stickSize,
                        enabled = drone.connected,
                        onMove = drone::setRight
                    )
                }

                Spacer(Modifier.height(8.dp))
                DeckStatus(drone)
            }
        }
    }
}

@Composable
private fun DeckHeader(
    drone: DroneController,
    host: String,
    onHostChange: (String) -> Unit,
    compact: Boolean
) {
    val corner = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Panel.copy(alpha = 0.96f), corner)
            .border(1.dp, Accent.copy(alpha = 0.18f), corner)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
    ) {
        Column(Modifier.width(if (compact) 76.dp else 92.dp)) {
            Text("DRONI", color = Accent, fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, maxLines = 1)
            Text("JJRC ELFIE", color = Pale.copy(alpha = 0.65f),
                fontSize = 9.sp, maxLines = 1)
        }
        Box(Modifier.width(1.dp).height(30.dp).background(Pale.copy(alpha = 0.16f)))

        Column(Modifier.weight(1f)) {
            Text(
                if (drone.connected) "●  FPV LIVE  /  H.264" else "●  FPV OFFLINE  /  H.264",
                color = if (drone.connected) Color(0xFF5EE2C4) else Warning,
                fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1
            )
            Spacer(Modifier.height(3.dp))
            Text(drone.videoStatus, color = Pale.copy(alpha = 0.78f),
                fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Column(horizontalAlignment = Alignment.Start) {
            if (!compact) {
                Text("DRONE IP", color = Pale.copy(alpha = 0.7f),
                    fontSize = 9.sp, maxLines = 1)
                Spacer(Modifier.height(3.dp))
            }
            BasicTextField(
                value = host,
                onValueChange = onHostChange,
                enabled = !drone.connected,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Pale, fontSize = 12.sp, fontWeight = FontWeight.Medium
                ),
                modifier = Modifier
                    .width(114.dp)
                    .height(36.dp)
                    .background(Navy, RoundedCornerShape(8.dp))
                    .border(1.dp, Pale.copy(alpha = 0.17f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 9.dp)
            )
        }

        if (drone.connected) {
            OutlinedButton(
                onClick = drone::disconnect,
                modifier = Modifier.width(96.dp).height(38.dp),
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.dp, Pale.copy(alpha = 0.40f)),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text("Disconnect", fontSize = 11.sp, maxLines = 1)
            }
        } else {
            Button(
                onClick = { drone.connect(host) },
                modifier = Modifier.width(96.dp).height(38.dp),
                shape = RoundedCornerShape(9.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Text("Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FlightConsole(drone: DroneController, modifier: Modifier = Modifier) {
    val corner = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .background(Panel.copy(alpha = 0.96f), corner)
            .border(1.dp, Accent.copy(alpha = 0.22f), corner)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("FLIGHT CONTROLS", color = Pale, fontSize = 10.sp,
                letterSpacing = 0.8.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                if (drone.connected) "● LINKED" else "● DISCONNECTED",
                color = if (drone.connected) Color(0xFF5EE2C4) else Warning,
                fontSize = 9.sp, fontWeight = FontWeight.SemiBold
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                modifier = Modifier.weight(1f).height(44.dp),
                enabled = drone.connected && !drone.takeoffRequested,
                onClick = drone::takeoff,
                shape = RoundedCornerShape(9.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Navy)
            ) {
                Text("TAKE OFF", fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Button(
                modifier = Modifier.weight(1f).height(44.dp),
                enabled = drone.connected,
                onClick = drone::land,
                shape = RoundedCornerShape(9.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Navy)
            ) {
                Text("LAND", fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Button(
                modifier = Modifier.weight(1.3f).height(44.dp),
                enabled = drone.connected,
                onClick = drone::emergencyStop,
                shape = RoundedCornerShape(9.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Danger, contentColor = Color.White,
                    disabledContainerColor = Danger.copy(alpha = 0.32f)
                )
            ) {
                Text("MOTOR STOP", fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ConsoleOption(
                label = "SPD ${drone.speedMode}/2", enabled = drone.connected,
                onClick = drone::cycleSpeed, modifier = Modifier.weight(1f)
            )
            ConsoleOption(
                label = "HEAD ${if (drone.headless) "ON" else "OFF"}",
                enabled = drone.connected, active = drone.headless,
                onClick = drone::toggleHeadless, modifier = Modifier.weight(1.2f)
            )
            ConsoleOption(
                label = "CALIBRATE",
                enabled = drone.connected && !drone.takeoffRequested,
                onClick = drone::calibrate, modifier = Modifier.weight(1.25f)
            )
            ConsoleOption(
                label = if (drone.reverseDirection) "REVERSE" else "NORMAL",
                enabled = drone.connected && !drone.takeoffRequested,
                active = drone.reverseDirection,
                onClick = drone::toggleReverse, modifier = Modifier.weight(1.15f)
            )
        }
    }
}

@Composable
private fun ConsoleOption(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp, if (active) Accent else Pale.copy(alpha = 0.25f)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (active) Accent else Pale
        ),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
    ) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Clip)
    }
}

@Composable
private fun StickDock(
    label: String,
    hint: String,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onMove: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Pale, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        VirtualJoystick(
            modifier = Modifier.size(size),
            enabled = enabled,
            onMove = onMove
        )
        Spacer(Modifier.height(5.dp))
        Text(hint, color = Pale.copy(alpha = 0.68f),
            fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun DeckStatus(drone: DroneController) {
    val corner = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(Navy.copy(alpha = 0.95f), corner)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(drone.linkStatus, modifier = Modifier.weight(1f),
            fontSize = 10.sp, color = Warning,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("UDP 8080  ·  25 ms", fontSize = 9.sp,
            color = Pale.copy(alpha = 0.63f), maxLines = 1)
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
                .background(Navy.copy(alpha = 0.66f), CircleShape)
                .border(1.5.dp, Accent.copy(alpha = 0.46f), CircleShape)
        ) {
            val radius = min(size.width, size.height) * 0.41f

            // 50% Control Zone Ring (Precision hover/glide)
            drawCircle(Color(0x35FFBF69), radius * 0.50f, style = Stroke(1.dp.toPx()))
            // 75% Control Zone Ring (Medium speed)
            drawCircle(Accent.copy(alpha = 0.28f), radius * 0.75f, style = Stroke(1.dp.toPx()))
            // 100% Control Zone Ring (Max speed)
            drawCircle(Accent.copy(alpha = 0.14f), radius, style = Stroke(1.dp.toPx()))

            // Crosshair axes
            drawLine(Accent.copy(alpha = 0.24f), Offset(center.x - radius, center.y),
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

            drawCircle(knobColor.copy(alpha = 0.18f), radius * 0.37f, center = knob)
            drawCircle(knobColor, radius * 0.27f, center = knob)
            drawCircle(Color.White.copy(alpha = 0.75f), radius * 0.27f, center = knob, style = Stroke(1.5.dp.toPx()))
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
