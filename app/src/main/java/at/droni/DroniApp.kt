package at.droni

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.droni.mission.PathMission
import at.droni.mission.PathPlanner
import at.droni.mission.PathPoint
import at.droni.net.DroneController
import at.droni.settings.DroniSettings
import at.droni.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Background = DroniColors.Background
private val Surface = DroniColors.Surface
private val Cyan = DroniColors.Cyan
private val Mint = DroniColors.Mint
private val White = DroniColors.Text
private val Muted = DroniColors.Muted
private val Alert = DroniColors.Amber

/** Navigation and persisted preferences. Hardware command behavior lives in the controller. */
@Composable
fun DroniApp(drone: DroneController) {
    val context = LocalContext.current
    val settings = remember { DroniSettings(context) }
    var page by remember { mutableStateOf("home") }
    var host by remember { mutableStateOf(settings.droneHost) }
    var showGrid by remember { mutableStateOf(settings.gridOverlay) }
    var defaultSpeed by remember { mutableIntStateOf(settings.defaultDroneSpeed) }
    var autoCalibration by remember { mutableStateOf(settings.autoCalibrationBeforeTakeoff) }
    var leaveFlightConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(defaultSpeed) { drone.setDefaultSpeedMode(defaultSpeed) }

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
            text = { Text("Leaving the controls hides live video. A landing request cannot confirm touchdown. Stay in control until safely landed.") },
            confirmButton = {
                TextButton(onClick = {
                    drone.land()
                    leaveFlightConfirm = false
                    page = "home"
                }) { Text("Request land & leave") }
            },
            dismissButton = { TextButton(onClick = { leaveFlightConfirm = false }) { Text("Stay in flight deck") } }
        )
    }
    when (page) {
        "fly" -> FlightDeck(drone, host,
            onHostChange = { host = it; settings.droneHost = it },
            showGrid = showGrid, autoCalibrationBeforeTakeoff = autoCalibration)
        "settings" -> SettingsScreen(host, drone.connected, showGrid, defaultSpeed, autoCalibration,
            onHostChange = { host = it.take(32); settings.droneHost = host },
            onGridChange = { showGrid = it; settings.gridOverlay = it },
            onSpeedChange = { defaultSpeed = it; settings.defaultDroneSpeed = it; drone.setDefaultSpeedMode(it) },
            onCalibrationChange = { autoCalibration = it; settings.autoCalibrationBeforeTakeoff = it },
            onBack = ::navigateHome)
        "path" -> PathScreen(drone, ::navigateHome)
        "movies" -> RecordingsScreen(::navigateHome, onOpenFlight = { page = "fly" })
        "about" -> AboutScreen(::navigateHome)
        else -> HomeScreen(drone.connected, onOpen = { page = it })
    }
}

/** Drawn routes are screen-space trajectories. The drone has NO verified position feedback. */
@Composable
private fun PathScreen(drone: DroneController, onBack: () -> Unit) {
    val points = remember { mutableStateListOf<PathPoint>() }
    val plan by remember { derivedStateOf { PathPlanner.plan(points.toList()) } }
    val scope = rememberCoroutineScope()
    var simulation by remember { mutableStateOf<Job?>(null) }
    var mission by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var confirmTrial by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Draw one continuous line to describe your route.") }
    val isMission = mission != null

    fun abortMission() {
        mission?.cancel()
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
                "Sequence: calibrate, wait 1 s; takeoff, wait 1 s; " +
                "100% climb input for 200 ms, then neutral; follow the drawing with " +
                "200 ms neutral gaps and request landing. Path roll/pitch is limited to 16%. " +
                "Climb duration does not establish an altitude. " +
                "Keep direct visual control, test propeller-off first, and abort if drifting.") },
            confirmButton = { TextButton(onClick = {
                confirmTrial = false
                simulation?.cancel()
                simulation = null
                progress = 0f
                val steps = plan.toList()
                val launchJob = scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        PathMission(drone).run(steps,
                            onStatus = { message = it },
                            onProgress = { progress = it })
                    } catch (cancelled: CancellationException) {
                        message = "Path trial cancelled. Controls neutralized."
                        throw cancelled
                    } catch (error: Exception) {
                        message = "Mission interrupted: ${error.message ?: "unknown error"}"
                    } finally {
                        mission = null
                    }
                }
                // Assign before starting so immediate validation failure cannot leave a completed job active.
                mission = launchJob
                launchJob.start()
            }) { Text("I understand — attempt") } },
            dismissButton = { TextButton(onClick = {
                confirmTrial = false
            }) { Text("Cancel") } }
        )
    }

    PageFrame("Draw path", "Sketch a route. Preview it before you fly.", {
        if (isMission) abortMission()
        onBack()
    }) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            Box(Modifier.weight(1f).fillMaxHeight()
                .background(Surface, RoundedCornerShape(20.dp))
                .border(1.dp, DroniColors.Outline, RoundedCornerShape(20.dp))
            ) {
                Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Route drawing canvas" }.pointerInput(isMission) {
                    if (isMission) return@pointerInput
                    detectDragGestures(
                        onDragStart = { p ->
                            simulation?.cancel()
                            simulation = null
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
                        drawPath(path, Cyan.copy(alpha = .10f), style = Stroke(14.dp.toPx()))
                        drawPath(path, Cyan, style = Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
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
                Text("ROUTE CANVAS", color = Muted, letterSpacing = 1.5.sp,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopStart).padding(18.dp))
                if (points.isEmpty()) Column(Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    DroniIcon(DroniSymbol.Route, Modifier.size(34.dp), Mint)
                    Spacer(Modifier.height(14.dp))
                    Text("Every route starts with a line", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(5.dp))
                    Text("Draw one continuous path with your finger", color = Muted,
                        style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.align(Alignment.BottomStart).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("● Start", color = Mint, style = MaterialTheme.typography.labelSmall)
                    Text("● Finish", color = Alert, style = MaterialTheme.typography.labelSmall)
                    Text("● Preview", color = White, style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.weight(.72f).fillMaxHeight()
                .background(Surface, RoundedCornerShape(20.dp))
                .border(1.dp, DroniColors.Outline, RoundedCornerShape(20.dp))
                .verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusPill(if (isMission) "Trial in progress" else "Offline preview", isMission)
                Text("Mission preview", style = MaterialTheme.typography.titleLarge)
                Text("${points.size} points · ${plan.size} command steps",
                    color = White, fontSize = 12.sp)
                Text("Start: calibrate → 1 s → takeoff → 1 s",
                    fontSize = 11.sp, color = Muted)
                Text("Climb: 100% for 200 ms, then neutral",
                    fontSize = 11.sp, color = Alert)
                Text("Path: ≤ 16% · 200 ms neutral gaps",
                    fontSize = 11.sp, color = Muted)
                Text("A screen sketch, not a measured flight route. Position and altitude are not available.",
                    fontSize = 12.sp, color = Alert, lineHeight = 16.sp)
                Button(enabled = plan.isNotEmpty() && !isMission, onClick = {
                    simulation?.cancel()
                    simulation = scope.launch {
                        for (i in 0..100) {
                            progress = i / 100f
                            delay(32)
                        }
                        message = "Simulation complete; no packets sent."
                        simulation = null
                    }
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    DroniIcon(DroniSymbol.Play, Modifier.size(16.dp), if (plan.isNotEmpty()) Background else Muted)
                    Spacer(Modifier.width(10.dp))
                    Text("Simulate path")
                }
                OutlinedButton(
                    enabled = !isMission && points.isNotEmpty(),
                    onClick = {
                        simulation?.cancel()
                        simulation = null
                        points.clear()
                        progress = 0f
                        message = "Drawing cleared."
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text("Clear drawing") }
                OutlinedButton(enabled = drone.connected && !drone.takeoffRequested &&
                    !isMission, onClick = {
                    drone.calibrate()
                    message = "Calibration requested; not sensor-verified."
                }, modifier = Modifier.fillMaxWidth()) { Text("Calibrate on level surface", fontSize = 12.sp) }
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
                if (progress > 0f) LinearProgressIndicator(progress = { progress },
                    modifier = Modifier.fillMaxWidth(), color = Mint, trackColor = DroniColors.Raised)
                Text(message, fontSize = 11.sp, color = White)
                Text("Leaving this screen cancels the trial and requests landing. " +
                    "Neither a land request nor completion verifies physical touchdown.",
                    fontSize = 11.sp, color = Muted)
            }
        }
    }
}
