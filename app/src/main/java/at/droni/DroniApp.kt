package at.droni

import android.content.ContentUris
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.core.content.FileProvider
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.droni.mission.PathPlanner
import at.droni.mission.PathPoint
import at.droni.net.DroneController
import at.droni.settings.DroniSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

private val Background = Color(0xFF091423)
private val Surface = Color(0xFF14283D)
private val Cyan = Color(0xFF4ACBFF)
private val Mint = Color(0xFF5EE7D2)
private val White = Color(0xFFF0F8FF)
private val Muted = Color(0xFFABC4D7)
private val Alert = Color(0xFFFFC377)

/** Root menu and application pages; manual FPV flight deck stays in MainActivity.kt. */
@Composable
fun DroniApp(drone: DroneController) {
    val context = LocalContext.current
    val settings = remember { DroniSettings(context) }
    var page by remember { mutableStateOf("home") }
    var host by remember { mutableStateOf(settings.droneHost) }
    var showGrid by remember { mutableStateOf(settings.gridOverlay) }
    var leaveFlightConfirm by remember { mutableStateOf(false) }

    fun navigateHome() {
        if (page == "fly" && drone.takeoffRequested) {
            drone.neutral()
            leaveFlightConfirm = true
        } else {
            if (page == "fly") drone.neutral()
            page = "home"
        }
    }
    BackHandler(page != "home") { navigateHome() }

    if (leaveFlightConfirm) {
        AlertDialog(
            onDismissRequest = { leaveFlightConfirm = false },
            title = { Text("Flight still active") },
            text = { Text("Leaving the controls hides live video. Droni can request landing, " +
                "but there is no flight acknowledgement. Remain in control until safely landed.") },
            confirmButton = {
                TextButton(onClick = {
                    drone.land()
                    leaveFlightConfirm = false
                    page = "home"
                }) { Text("Request LAND & leave") }
            },
            dismissButton = {
                TextButton(onClick = { leaveFlightConfirm = false }) { Text("Stay in flight deck") }
            }
        )
    }

    when (page) {
        "fly" -> FlightDeck(
            drone = drone, host = host,
            onHostChange = { host = it; settings.droneHost = it },
            onHome = ::navigateHome,
            showGrid = showGrid
        )
        "settings" -> PageFrame("SETTINGS", "Personalize your flight deck", ::navigateHome) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("CONNECTION", color = Mint, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.take(32); settings.droneHost = host },
                    label = { Text("Drone IPv4 / Wi-Fi gateway") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !drone.connected,
                    supportingText = { Text("Default 172.16.10.1 · UDP 8080 / TCP video 8888") }
                )
                HorizontalDivider(color = Muted.copy(alpha = .18f))
                Text("FLIGHT DECK", color = Mint, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Camera grid overlay", color = White)
                        Text("Visual composition guide only; not drone positioning",
                            fontSize = 12.sp, color = Muted)
                    }
                    Switch(checked = showGrid, onCheckedChange = {
                        showGrid = it; settings.gridOverlay = it
                    })
                }
                Text("Default drone speed: 1 / 2 · configured in Flight Control",
                    color = Muted, fontSize = 13.sp)
                Text("Recordings: Movies / Droni · local MP4 video; no cloud upload",
                    color = Muted, fontSize = 13.sp)
                Text("Wi-Fi sensor telemetry has not been verified. Calibrate changes the drone's " +
                    "level reference, not its location or heading. No GPS/autonomy is inferred.",
                    color = Alert, fontSize = 13.sp)
            }
        }
        "path" -> PathScreen(drone, ::navigateHome)
        "movies" -> RecordingsScreen(::navigateHome)
        "about" -> PageFrame("ABOUT DRONI", "An open-source JJRC Elfie companion", ::navigateHome) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DroneMark(Modifier.size(95.dp))
                Text("DRONI", fontSize = 39.sp, fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp, color = White)
                Text("JJRC Elfie Android Flight Controller", color = Muted)
                Text("Version 0.2.0", color = Mint)
                Text("Designed and developed by Dr. Vahid Tavakkoli",
                    color = White, fontWeight = FontWeight.SemiBold)
                Text("Copyright © 2026 Dr. Vahid Tavakkoli. All rights reserved.",
                    color = White, fontSize = 13.sp)
                Text("Source code released under the MIT License.",
                    color = Muted, fontSize = 12.sp)
                Text("github.com/vtavakkoli/Droni", color = Cyan,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/vtavakkoli/Droni")))
                    })
                Text("Flight commands are not acknowledged and this experimental software " +
                    "is not a certified flight-control system.",
                    color = Alert, fontSize = 12.sp)
            }
        }
        else -> HomeScreen(
            connected = drone.connected,
            onOpen = { page = it }
        )
    }
}

@Composable
private fun HomeScreen(connected: Boolean, onOpen: (String) -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(
        listOf(Background, Color(0xFF102B41), Background)
    ))) {
        Row(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(26.dp),
            horizontalArrangement = Arrangement.spacedBy(25.dp)
        ) {
            Column(
                modifier = Modifier.weight(.96f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                DroneMark(Modifier.size(100.dp))
                Spacer(Modifier.height(12.dp))
                Text("DRONI", fontSize = 49.sp, fontWeight = FontWeight.Black,
                    letterSpacing = 5.sp, color = White)
                Text("JJRC ELFIE  /  FLIGHT EXPERIENCE", fontSize = 13.sp,
                    color = Mint, letterSpacing = 2.sp)
                Spacer(Modifier.height(14.dp))
                Text("Fly. Explore. Capture.", fontSize = 24.sp, color = White,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Your native Android flight deck, route sketchpad and flight video library.",
                    color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(24.dp))
                Text(if (connected) "●  Wi-Fi link active · UDP commands unacknowledged"
                    else "●  Ready to connect · join drone Wi-Fi first",
                    color = if (connected) Mint else Alert, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("© 2026 Dr. Vahid Tavakkoli", color = Muted, fontSize = 11.sp)
            }
            Column(
                modifier = Modifier.weight(1.06f).fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MenuCard("01", "DRONE FLY", "Live video + joystick control", "fly", onOpen,
                    highlight = true)
                MenuCard("02", "DRAW PATH", "Sketch, simulate, experimental trial", "path", onOpen)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        MenuCard("03", "SETTINGS", "App preferences", "settings", onOpen)
                    }
                    Box(Modifier.weight(1f)) {
                        MenuCard("04", "RECORDINGS", "Saved MP4 videos", "movies", onOpen)
                    }
                }
                MenuCard("05", "ABOUT DRONI", "Project & copyright", "about", onOpen)
            }
        }
    }
}

@Composable
private fun MenuCard(number: String, title: String, subtitle: String,
    destination: String, onOpen: (String) -> Unit, highlight: Boolean = false
) {
    val corner = RoundedCornerShape(17.dp)
    Column(Modifier.fillMaxWidth()
        .background(if (highlight) Color(0xFF18435A) else Surface, corner)
        .border(1.dp, if (highlight) Cyan.copy(alpha = .6f)
            else Muted.copy(alpha = .13f), corner)
        .clickable { onOpen(destination) }
        .padding(horizontal = 20.dp, vertical = 17.dp)
    ) {
        Text(number, fontSize = 11.sp, color = Mint, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(title, fontSize = 19.sp, color = White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 2)
    }
}

@Composable
private fun DroneMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val center = this.center
        val r = size.minDimension / 4.0f
        val rotorRadius = r * .45f
        for (dx in listOf(-1f, 1f)) for (dy in listOf(-1f, 1f)) {
            val c = Offset(center.x + dx * r * 1.15f, center.y + dy * r * 1.15f)
            drawLine(Muted, center, c, 7.dp.toPx())
            drawCircle(Mint, rotorRadius, c, style = Stroke(4.dp.toPx()))
            drawCircle(Cyan.copy(alpha = .2f), rotorRadius * .6f, c)
        }
        drawCircle(White, r * .75f, center)
        drawCircle(Cyan, r * .43f, center)
        drawCircle(Background, r * .2f, center)
    }
}

@Composable
private fun PageFrame(title: String, subtitle: String, onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Background)
        .safeDrawingPadding().padding(horizontal = 24.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹  MENU", color = Cyan) }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
        }
        HorizontalDivider(color = Muted.copy(alpha = .16f))
        Box(Modifier.fillMaxSize().padding(horizontal = 10.dp)) { content() }
    }
}

/** Drawn routes are screen-space trajectories. The drone has NO verified position feedback. */
@Composable
private fun PathScreen(drone: DroneController, onBack: () -> Unit) {
    val points = remember { mutableStateListOf<PathPoint>() }
    val plan = remember(points.size) { PathPlanner.plan(points.toList()) }
    val scope = rememberCoroutineScope()
    var simulation by remember { mutableStateOf<Job?>(null) }
    var mission by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var confirmTrial by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Draw one continuous line to describe your route.") }
    val isMission = mission?.isActive == true

    fun abortMission() {
        mission?.cancel()
        mission = null
        drone.neutral()
        if (drone.connected && drone.takeoffRequested) drone.land()
        message = "Path trial aborted; landing requested (NOT confirmed)."
    }
    DisposableEffect(Unit) {
        onDispose {
            simulation?.cancel()
            if (mission?.isActive == true) {
                mission?.cancel()
                drone.neutral()
                if (drone.connected && drone.takeoffRequested) drone.land()
            }
        }
    }
    if (confirmTrial) {
        AlertDialog(
            onDismissRequest = { confirmTrial = false },
            title = { Text("Experimental OPEN-LOOP flight") },
            text = { Text("No verified GPS, camera pose, heading or IMU telemetry. " +
                "The drone cannot follow a true coordinate path or know its endpoint. " +
                "This sends short, approximate low-strength roll/pitch commands, then " +
                "requests landing. Calibration does not fix this. " +
                "Keep direct visual control, test propeller-off first, and abort if drifting.") },
            confirmButton = { TextButton(onClick = {
                confirmTrial = false
                val steps = plan.toList()
                mission = scope.launch {
                    var flightRequested = false
                    try {
                        message = "Calibrating on LEVEL surface… (not acknowledged)"
                        if (!drone.connected || drone.takeoffRequested) error("Drone must be connected and disarmed")
                        drone.calibrate()
                        delay(1800)
                        ensureActive()
                        message = "Takeoff requested. Waiting for stabilization…"
                        drone.takeoff()
                        flightRequested = true
                        delay(3000)
                        for ((index, step) in steps.withIndex()) {
                            ensureActive()
                            if (!drone.connected) error("Wi-Fi link lost")
                            drone.setRight(step.roll, step.pitch)
                            delay(step.durationMs)
                            drone.setRight(0f, 0f)
                            delay(PathPlanner.REST_MS)
                            progress = (index + 1).toFloat() / steps.size
                            message = "Step ${index + 1}/${steps.size}; no position feedback"
                        }
                        message = "Route commands sent. LAND requested; touchdown not verified."
                    } catch (e: Exception) {
                        message = "Mission interrupted: ${e.message ?: "cancelled"}"
                    } finally {
                        drone.neutral()
                        if (flightRequested && drone.connected && drone.takeoffRequested) drone.land()
                        mission = null
                    }
                }
            }) { Text("I understand — attempt") } },
            dismissButton = { TextButton(onClick = {
                confirmTrial = false
            }) { Text("Cancel") } }
        )
    }

    PageFrame("DRAW PATH", "Screen-space path · simulator + supervised experimental commands", {
        if (isMission) abortMission()
        onBack()
    }) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight()
                .background(Surface, RoundedCornerShape(14.dp))
                .border(1.dp, Cyan.copy(alpha = .3f), RoundedCornerShape(14.dp))
            ) {
                Canvas(Modifier.fillMaxSize().pointerInput(isMission) {
                    if (isMission) return@pointerInput
                    detectDragGestures(
                        onDragStart = { p ->
                            points.clear(); progress = 0f
                            points.add(PathPoint(
                                (p.x / size.width).coerceIn(0f, 1f),
                                (p.y / size.height).coerceIn(0f, 1f)))
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            points.add(PathPoint(
                                (change.position.x / size.width).coerceIn(0f, 1f),
                                (change.position.y / size.height).coerceIn(0f, 1f)))
                        },
                        onDragEnd = {
                            message = "Path captured. Preview or simulate before trial."
                        }
                    )
                }) {
                    val stepX = size.width / 10f
                    val stepY = size.height / 6f
                    for (i in 1..9) drawLine(Muted.copy(alpha = .12f),
                        Offset(stepX * i, 0f), Offset(stepX * i, size.height))
                    for (i in 1..5) drawLine(Muted.copy(alpha = .12f),
                        Offset(0f, stepY * i), Offset(size.width, stepY * i))
                    if (points.size >= 2) {
                        val path = Path().apply {
                            moveTo(points[0].x * size.width, points[0].y * size.height)
                            for (p in points.drop(1)) lineTo(p.x * size.width, p.y * size.height)
                        }
                        drawPath(path, Cyan, style = Stroke(4.dp.toPx()))
                        val first = points.first()
                        val last = points.last()
                        drawCircle(Mint, 8.dp.toPx(),
                            Offset(first.x * size.width, first.y * size.height))
                        drawCircle(Alert, 8.dp.toPx(),
                            Offset(last.x * size.width, last.y * size.height))
                        val index = (progress * (points.size - 1)).toInt()
                            .coerceIn(0, points.lastIndex)
                        val marker = points[index]
                        drawCircle(White, 7.dp.toPx(),
                            Offset(marker.x * size.width, marker.y * size.height))
                    }
                }
                if (points.isEmpty()) Text("DRAW YOUR ROUTE HERE",
                    color = Muted, fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center))
                Text("START ● mint    END ● amber    MARKER ● white",
                    color = Muted, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
            }
            Column(Modifier.widthIn(min = 220.dp, max = 305.dp).fillMaxHeight()
                .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("MISSION PREVIEW", color = Mint, fontWeight = FontWeight.Bold)
                Text("${points.size} drawn points · ${plan.size} bounded commands",
                    color = White, fontSize = 12.sp)
                Text("Maximum deflection 16% · movement ≤ 4.32 s",
                    fontSize = 11.sp, color = Muted)
                Text("Drawing distance is NOT meters; no position, speed or altitude telemetry.",
                    fontSize = 12.sp, color = Alert, lineHeight = 16.sp)
                Button(enabled = plan.isNotEmpty() && !isMission, onClick = {
                    simulation?.cancel()
                    simulation = scope.launch {
                        for (i in 0..100) {
                            progress = i / 100f
                            delay(32)
                        }
                        message = "Simulation complete; no packets sent."
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("SIMULATE PATH") }
                OutlinedButton(
                    enabled = !isMission && points.isNotEmpty(),
                    onClick = {
                        simulation?.cancel()
                        points.clear()
                        progress = 0f
                        message = "Drawing cleared."
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text("CLEAR DRAWING") }
                OutlinedButton(enabled = drone.connected && !drone.takeoffRequested &&
                    !isMission, onClick = {
                    drone.calibrate()
                    message = "Calibration requested; not sensor-verified."
                }, modifier = Modifier.fillMaxWidth()) { Text("CALIBRATE (LEVEL)") }
                if (isMission) {
                    Button(onClick = ::abortMission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC94959)),
                        modifier = Modifier.fillMaxWidth()) { Text("ABORT + REQUEST LAND") }
                } else {
                    Button(
                        enabled = drone.connected && !drone.takeoffRequested &&
                            plan.isNotEmpty(),
                        onClick = { confirmTrial = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8C5F22)),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("EXPERIMENTAL PATH TRIAL", fontSize = 12.sp) }
                }
                if (isMission) {
                    OutlinedButton(
                        onClick = {
                            mission?.cancel()
                            drone.emergencyStop()
                            message = "EMERGENCY MOTOR STOP requested — aircraft may fall."
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF7383))
                    ) { Text("EMERGENCY MOTOR STOP", fontSize = 11.sp) }
                }
                Text(message, fontSize = 11.sp, color = White)
                Text("Leaving this screen cancels the trial and requests landing. " +
                    "Neither a land request nor completion verifies physical touchdown.",
                    fontSize = 11.sp, color = Muted)
            }
        }
    }
}

private data class MovieItem(val uri: android.net.Uri, val name: String, val size: Long)
@Composable
private fun RecordingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val movies = remember(refresh) {
        val result = mutableListOf<MovieItem>()
        if (Build.VERSION.SDK_INT >= 29) {
            val columns = arrayOf(MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE)
            try {
                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    columns,
                    MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?",
                    arrayOf("%/Droni/%"),
                    MediaStore.Video.Media.DATE_ADDED + " DESC"
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        result += MovieItem(
                            ContentUris.withAppendedId(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                            cursor.getString(1) ?: "Droni movie",
                            cursor.getLong(2)
                        )
                    }
                }
            } catch (_: Exception) { }
        } else {
            val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Droni")
            folder.listFiles { file -> file.extension.equals("mp4", true) }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { result += MovieItem(
                    FileProvider.getUriForFile(context,
                        "${context.packageName}.fileprovider", it),
                    it.name, it.length()
                ) }
        }
        result
    }
    PageFrame("RECORDINGS", "Movies captured from the JJRC Elfie camera", onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LOCAL MP4 MOVIES  ·  ${movies.size}", color = Mint,
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { refresh++ }) { Text("Refresh") }
            }
            if (movies.isEmpty()) {
                Text("No MP4 recordings yet. Open Drone Fly, connect the camera, " +
                    "then tap REC to save a movie.", color = Muted)
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    movies.forEach { movie ->
                        Row(Modifier.fillMaxWidth()
                            .background(Surface, RoundedCornerShape(10.dp))
                            .clickable {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(movie.uri, "video/mp4")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    })
                                } catch (_: Exception) {}
                            }
                            .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("▶", color = Cyan, fontSize = 18.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(movie.name, color = White, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${movie.size / 1024L} KB · local device",
                                    color = Muted, fontSize = 11.sp)
                            }
                            Text("PLAY", color = Mint)
                        }
                    }
                }
            }
        }
    }
}
