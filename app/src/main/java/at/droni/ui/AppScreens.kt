package at.droni.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.droni.BuildConfig
import at.droni.settings.FlightSequenceSettings

@Composable
fun HomeScreen(connected: Boolean, onOpen: (String) -> Unit) {
    DroniBackdrop {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val compact = maxHeight < 400.dp
            val gap = if (compact) 14.dp else 22.dp
            Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = if (compact) 12.dp else 20.dp),
                verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    DroneMark(Modifier.size(42.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("DRONI", fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp, fontSize = 20.sp)
                        Text("THE ELFIE COMPANION", color = DroniColors.Muted, letterSpacing = 1.2.sp, fontSize = 8.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    StatusPill(if (connected) "Wi-Fi socket active" else "Ready when you are", connected)
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Canvas(Modifier.size(if (compact) 210.dp else 260.dp).align(Alignment.CenterEnd)) {
                            val r = size.minDimension * .39f
                            drawCircle(DroniColors.Cyan.copy(alpha = .035f), r)
                            drawCircle(DroniColors.Cyan.copy(alpha = .09f), r, style = Stroke(1.dp.toPx()))
                            drawCircle(DroniColors.Cyan.copy(alpha = .05f), r * .74f, style = Stroke(1.dp.toPx()))
                            drawLine(DroniColors.Cyan.copy(alpha = .08f), Offset(center.x - r - 15, center.y),
                                Offset(center.x + r + 15, center.y), 1.dp.toPx())
                        }
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center) {
                            Text("YOUR FLIGHT STARTS HERE", color = DroniColors.Mint,
                                style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp)
                            Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
                            Text("A little drone.", fontSize = if (compact) 30.sp else 38.sp,
                                lineHeight = if (compact) 35.sp else 44.sp, fontWeight = FontWeight.Bold)
                            Text("A bigger view.", color = DroniColors.Cyan, fontSize = if (compact) 30.sp else 38.sp,
                                lineHeight = if (compact) 35.sp else 44.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            Text("Take the controls. Find a new perspective.\nKeep the moments worth watching.",
                                style = MaterialTheme.typography.bodyMedium, color = DroniColors.Muted)
                            Spacer(Modifier.height(if (compact) 16.dp else 24.dp))
                            Button(onClick = { onOpen("fly") }, modifier = Modifier.heightIn(min = 50.dp),
                                shape = RoundedCornerShape(15.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
                                DroniIcon(DroniSymbol.Flight, Modifier.size(20.dp), DroniColors.Background)
                                Spacer(Modifier.width(12.dp))
                                Text("Open flight deck")
                                Spacer(Modifier.width(18.dp))
                                DroniIcon(DroniSymbol.Arrow, Modifier.size(18.dp), DroniColors.Background)
                            }
                        }
                    }
                    Column(Modifier.weight(1.08f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HomeTile("Draw path", "Sketch & simulate", DroniSymbol.Route, DroniColors.Mint,
                                Modifier.weight(1f), compact) { onOpen("path") }
                            HomeTile("Recordings", "Your captured moments", DroniSymbol.Video, DroniColors.Cyan,
                                Modifier.weight(1f), compact) { onOpen("movies") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HomeTile("Settings", "Make it yours", DroniSymbol.Settings, Color(0xFFC5BBFF),
                                Modifier.weight(1f), compact) { onOpen("settings") }
                            HomeTile("About Droni", "The project & creator", DroniSymbol.Info, DroniColors.Amber,
                                Modifier.weight(1f), compact) { onOpen("about") }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    DroniIcon(DroniSymbol.Wifi, Modifier.size(14.dp), DroniColors.Muted)
                    Spacer(Modifier.width(7.dp))
                    Text("Join your Elfie Wi-Fi before connecting", color = DroniColors.Muted,
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Text("© 2026 Dr. Vahid Tavakkoli", color = DroniColors.Muted.copy(alpha = .7f),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun HomeTile(title: String, subtitle: String, icon: DroniSymbol, accent: Color,
                     modifier: Modifier, compact: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp),
        color = DroniColors.Surface, border = BorderStroke(1.dp, DroniColors.Outline.copy(alpha = .8f))) {
        Column(Modifier.fillMaxWidth().heightIn(min = if (compact) 104.dp else 122.dp)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = .055f), Color.Transparent)))
            .padding(if (compact) 14.dp else 18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).background(accent.copy(alpha = .09f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center) { DroniIcon(icon, Modifier.size(20.dp), accent) }
                Spacer(Modifier.weight(1f))
                DroniIcon(DroniSymbol.Arrow, Modifier.size(15.dp), DroniColors.Muted.copy(alpha = .65f))
            }
            Spacer(Modifier.height(if (compact) 9.dp else 14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = DroniColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SettingsScreen(host: String, connected: Boolean, showGrid: Boolean,
                   sequence: FlightSequenceSettings, feedbackStatus: String,
                   onHostChange: (String) -> Unit, onGridChange: (Boolean) -> Unit,
                   onSequenceChange: (FlightSequenceSettings) -> Unit,
                   onShareFeedback: () -> Unit, onBack: () -> Unit) {
    PageFrame("Settings", "One launch sequence for the flight deck and Draw Path", onBack) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AppCard {
                    SectionHeading(DroniSymbol.Wifi, "Connection", "Join the drone Wi-Fi on your phone first")
                    OutlinedTextField(value = host, onValueChange = onHostChange,
                        label = { Text("Drone IP address") }, singleLine = true,
                        enabled = !connected, shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text(if (connected) "Disconnect to change the address" else "Default: 172.16.10.1") })
                    PreferenceToggle("Auto-connect before launch", "Connect automatically when Take Off or a path trial starts.",
                        sequence.autoConnect, { onSequenceChange(sequence.copy(autoConnect = it)) })
                    SettingsStepper("Connection wait", sequence.connectionWaitMs / 1000, "s", 1, 1L..60L) {
                        onSequenceChange(sequence.copy(connectionWaitMs = it * 1000))
                    }
                }
                AppCard {
                    SectionHeading(DroniSymbol.Grid, "Camera", "Frame your next shot")
                    PreferenceToggle("Composition grid", "Show a rule-of-thirds guide over live video.", showGrid, onGridChange)
                }
                AppCard {
                    SectionHeading(DroniSymbol.Info, "Drone response checks", "Checked after every launch and path stage")
                    Text(feedbackStatus, color = DroniColors.Mint, style = MaterialTheme.typography.bodyMedium)
                    Text("Incoming UDP packets are recorded. A reply, identity string or live video does not confirm calibration, takeoff or landing. Flight ACK decoding is unverified.",
                        color = DroniColors.Muted, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onShareFeedback) { Text("Share response log") }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AppCard {
                    SectionHeading(DroniSymbol.Flight, "Launch sequence", "Used by both flight screens")
                    Text("Launch speed", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Low", "Medium", "High").forEachIndexed { index, label ->
                            val isSelected = sequence.speedMode == index
                            OutlinedButton(onClick = { onSequenceChange(sequence.copy(speedMode = index)) },
                                modifier = Modifier.weight(1f).semantics { selected = isSelected },
                                shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
                                border = BorderStroke(1.dp, if (isSelected) DroniColors.Cyan else DroniColors.Outline),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) DroniColors.Cyan.copy(alpha = .12f) else Color.Transparent,
                                    contentColor = if (isSelected) DroniColors.Cyan else DroniColors.Muted)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$index", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    Text(label, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    Text("Default: speed 1. Applied before calibration and takeoff, including on an existing connection.",
                        color = DroniColors.Muted, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(color = DroniColors.Outline)
                    PreferenceToggle("Auto-calibrate before takeoff", "Keep the drone level and still. A command is not a completion acknowledgement.",
                        sequence.autoCalibrate, { onSequenceChange(sequence.copy(autoCalibrate = it)) })
                    SettingsStepper("Calibration wait", sequence.calibrationWaitMs / 1000, "s", 1, 1L..30L) {
                        onSequenceChange(sequence.copy(calibrationWaitMs = it * 1000))
                    }
                    SettingsStepper("Takeoff wait", sequence.takeoffWaitMs / 1000, "s", 1, 1L..30L) {
                        onSequenceChange(sequence.copy(takeoffWaitMs = it * 1000))
                    }
                }
                AppCard {
                    SectionHeading(DroniSymbol.Level, "Initial climb & path", "Timed inputs, without measured altitude")
                    SettingsStepper("Upward input", sequence.climbPercent.toLong(), "%", 5, 0L..100L) {
                        onSequenceChange(sequence.copy(climbPercent = it.toInt()))
                    }
                    SettingsStepper("Upward pulse", sequence.climbDurationMs, "ms", 50, 50L..1000L) {
                        onSequenceChange(sequence.copy(climbDurationMs = it))
                    }
                    SettingsStepper("Neutral command gap", sequence.commandGapMs, "ms", 25, 25L..2000L) {
                        onSequenceChange(sequence.copy(commandGapMs = it))
                    }
                    SettingsStepper("Landing response wait", sequence.landingWaitMs / 1000, "s", 1, 1L..30L) {
                        onSequenceChange(sequence.copy(landingWaitMs = it * 1000))
                    }
                    Text("Defaults: 100% up for 200 ms, then neutral; 200 ms between path commands. The flight deck hands control back after the launch sequence. Draw Path requests landing at the end.",
                        color = DroniColors.Muted, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { onSequenceChange(FlightSequenceSettings()) }) { Text("Reset launch defaults") }
                }
            }
        }
    }
}

@Composable
private fun SettingsStepper(label: String, value: Long, unit: String, step: Long,
                            range: LongRange, onChange: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        OutlinedIconButton(onClick = { onChange((value - step).coerceIn(range)) },
            enabled = value > range.first, modifier = Modifier.size(40.dp)) {
            Text("−", modifier = Modifier.semantics { contentDescription = "Decrease $label" })
        }
        Text("$value $unit", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.widthIn(min = 52.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        OutlinedIconButton(onClick = { onChange((value + step).coerceIn(range)) },
            enabled = value < range.last, modifier = Modifier.size(40.dp)) {
            Text("+", modifier = Modifier.semantics { contentDescription = "Increase $label" })
        }
    }
}

@Composable
private fun SectionHeading(icon: DroniSymbol, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DroniIcon(icon, Modifier.size(22.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = DroniColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PreferenceToggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(detail, color = DroniColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange,
            modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    PageFrame("About Droni", "A small aircraft. A thoughtful companion.", onBack) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            AppCard(Modifier.weight(.85f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    DroneMark(Modifier.size(100.dp))
                    Text("DRONI", fontSize = 32.sp, letterSpacing = 5.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    Text("JJRC Elfie · Android flight companion", color = DroniColors.Muted,
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(14.dp))
                    StatusPill("Version ${BuildConfig.VERSION_NAME}")
                }
            }
            Column(Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AppCard {
                    Text("MADE WITH CURIOSITY", color = DroniColors.Mint,
                        style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp)
                    Text("Dr. Vahid Tavakkoli", style = MaterialTheme.typography.titleLarge)
                    Text("Design & development", color = DroniColors.Muted, style = MaterialTheme.typography.bodyMedium)
                    Text("A native, open-source flight deck for exploring, flying and capturing moments with the JJRC Elfie.",
                        color = DroniColors.Muted, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/vtavakkoli/Droni"))) }
                        catch (_: ActivityNotFoundException) { Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show() }
                    }, shape = RoundedCornerShape(12.dp)) {
                        Text("Explore the project")
                        Spacer(Modifier.width(12.dp))
                        DroniIcon(DroniSymbol.Arrow, Modifier.size(16.dp))
                    }
                }
                Text("© 2026 Dr. Vahid Tavakkoli. Source code under the MIT License.",
                    color = DroniColors.Muted, style = MaterialTheme.typography.bodySmall)
                Text("Flight commands are unacknowledged. This experimental app cannot confirm flight state or landing.",
                    color = DroniColors.Amber, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
