package at.droni.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.droni.R

enum class DroniSymbol { Flight, Route, Video, Settings, Info, Back, Arrow, Wifi, Refresh, Play, Share, Check, Grid, Level, Close }

/** Small, consistent line icons with no font or network dependency. Labels live on their controls. */
@Composable
fun DroniIcon(symbol: DroniSymbol, modifier: Modifier = Modifier.size(22.dp),
              tint: Color = DroniColors.Cyan, description: String? = null) {
    Canvas(modifier.then(if (description == null) Modifier else Modifier.semantics {
        contentDescription = description
    })) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            fun line(x: Float, y: Float, xx: Float, yy: Float) =
                drawLine(tint, Offset(x, y), Offset(xx, yy), 1.7f, StrokeCap.Round)
            fun ring(x: Float, y: Float, r: Float) = drawCircle(tint, r, Offset(x, y), style = Stroke(1.7f))
            fun box(x: Float, y: Float, w: Float, h: Float, r: Float = 3f) =
                drawRoundRect(tint, Offset(x, y), Size(w, h), CornerRadius(r), style = Stroke(1.7f))
            when (symbol) {
                DroniSymbol.Flight -> {
                    line(8f, 8f, 16f, 16f); line(16f, 8f, 8f, 16f)
                    for (x in listOf(6f, 18f)) for (y in listOf(6f, 18f)) ring(x, y, 3.5f)
                    drawCircle(tint, 2.5f, Offset(12f, 12f))
                }
                DroniSymbol.Route -> {
                    ring(5f, 18f, 2.3f); ring(19f, 6f, 2.3f)
                    drawPath(Path().apply { moveTo(5f, 15f); lineTo(5f, 9f)
                        cubicTo(5f, 3f, 15f, 7f, 15f, 12f); cubicTo(15f, 19f, 22f, 18f, 19f, 9f)
                    }, tint, style = Stroke(1.7f, cap = StrokeCap.Round))
                }
                DroniSymbol.Video -> {
                    box(3f, 5f, 18f, 14f)
                    drawPath(Path().apply { moveTo(10f, 9f); lineTo(16f, 12f)
                        lineTo(10f, 15f); close() }, tint)
                }
                DroniSymbol.Settings -> {
                    line(4f, 6f, 20f, 6f); line(4f, 12f, 20f, 12f); line(4f, 18f, 20f, 18f)
                    for ((x, y) in listOf(9f to 6f, 16f to 12f, 7f to 18f)) {
                        drawCircle(DroniColors.Surface, 2.8f, Offset(x, y)); ring(x, y, 2.5f)
                    }
                }
                DroniSymbol.Info -> { ring(12f, 12f, 9f); line(12f, 11f, 12f, 16f)
                    drawCircle(tint, 1f, Offset(12f, 7.5f)) }
                DroniSymbol.Back -> { line(14f, 5f, 7f, 12f); line(7f, 12f, 14f, 19f) }
                DroniSymbol.Arrow -> { line(4f, 12f, 20f, 12f); line(14f, 6f, 20f, 12f); line(20f, 12f, 14f, 18f) }
                DroniSymbol.Wifi -> {
                    drawArc(tint, 220f, 100f, false, Offset(1f, 4f), Size(22f, 20f), style = Stroke(1.7f, cap = StrokeCap.Round))
                    drawArc(tint, 220f, 100f, false, Offset(5f, 9f), Size(14f, 12f), style = Stroke(1.7f, cap = StrokeCap.Round))
                    drawCircle(tint, 1.7f, Offset(12f, 18f))
                }
                DroniSymbol.Refresh -> {
                    drawArc(tint, 30f, 290f, false, Offset(4f, 4f), Size(16f, 16f), style = Stroke(1.7f, cap = StrokeCap.Round))
                    line(20f, 4f, 20f, 10f); line(14f, 10f, 20f, 10f)
                }
                DroniSymbol.Play -> drawPath(Path().apply {
                    moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close()
                }, tint, style = Stroke(1.7f))
                DroniSymbol.Share -> {
                    line(7f, 11f, 17f, 6f); line(7f, 13f, 17f, 18f)
                    ring(5f, 12f, 2.5f); ring(19f, 5f, 2.5f); ring(19f, 19f, 2.5f)
                }
                DroniSymbol.Check -> { line(5f, 12f, 10f, 17f); line(10f, 17f, 20f, 7f) }
                DroniSymbol.Grid -> { box(3f, 3f, 18f, 18f); line(9f, 3f, 9f, 21f)
                    line(15f, 3f, 15f, 21f); line(3f, 9f, 21f, 9f); line(3f, 15f, 21f, 15f) }
                DroniSymbol.Level -> { ring(12f, 12f, 7f); line(1f, 12f, 8f, 12f)
                    line(16f, 12f, 23f, 12f); drawCircle(tint, 2f, Offset(12f, 12f)) }
                DroniSymbol.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            }
        }
    }
}

@Composable
fun DroneMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_droni_foreground), contentDescription = null, modifier = modifier)
}

@Composable
fun DroniBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(DroniColors.Background)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(
                listOf(Color(0xFF143145), Color.Transparent),
                center = Offset(size.width * .25f, size.height * .28f), radius = size.width * .65f))
            val gap = 40.dp.toPx()
            for (i in 0..(size.width / gap).toInt()) for (j in 0..(size.height / gap).toInt()) {
                drawCircle(DroniColors.Cyan.copy(alpha = .07f), .8.dp.toPx(), Offset(i * gap, j * gap))
            }
        }
        content()
    }
}

@Composable
fun StatusPill(text: String, active: Boolean = false, modifier: Modifier = Modifier) {
    val color = if (active) DroniColors.Mint else DroniColors.Muted
    Surface(modifier, color = color.copy(alpha = .08f), shape = CircleShape,
        border = BorderStroke(1.dp, color.copy(alpha = .16f))) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(5.dp).background(color, CircleShape))
            Text(text, color = color, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, color = DroniColors.Surface.copy(alpha = .93f),
        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DroniColors.Outline.copy(alpha = .7f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun PageFrame(title: String, subtitle: String, onBack: () -> Unit,
              content: @Composable () -> Unit) {
    DroniBackdrop {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalIconButton(onClick = onBack, modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = DroniColors.Raised)) {
                    DroniIcon(DroniSymbol.Back, description = "Back to home")
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(subtitle, color = DroniColors.Muted, style = MaterialTheme.typography.bodySmall)
                }
                Text("DRONI", color = DroniColors.Muted, fontWeight = FontWeight.Bold,
                    fontSize = 11.sp, letterSpacing = 2.sp)
            }
            HorizontalDivider(color = DroniColors.Outline.copy(alpha = .65f))
            Box(Modifier.weight(1f)) { content() }
        }
    }
}
