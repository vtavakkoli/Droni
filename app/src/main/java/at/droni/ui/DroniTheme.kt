package at.droni.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared palette and type scale. Flight overlays deliberately use lighter opacity. */
object DroniColors {
    val Background = Color(0xFF080F1B)
    val Surface = Color(0xFF111E2E)
    val Raised = Color(0xFF192A3C)
    val Cyan = Color(0xFF72D6FF)
    val Mint = Color(0xFF86E8CD)
    val Text = Color(0xFFF1F6FC)
    val Muted = Color(0xFFA1B2C6)
    val Outline = Color(0xFF293B50)
    val Amber = Color(0xFFFFCB86)
    val Red = Color(0xFFFF7185)
}

@Composable
fun DroniTheme(content: @Composable () -> Unit) {
    val base = Typography()
    fun style(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = FontFamily.SansSerif, fontSize = size.sp,
        lineHeight = height.sp, fontWeight = weight
    )
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = DroniColors.Cyan, onPrimary = DroniColors.Background,
            primaryContainer = Color(0xFF183F55), onPrimaryContainer = DroniColors.Text,
            secondary = DroniColors.Mint, onSecondary = DroniColors.Background,
            background = DroniColors.Background, onBackground = DroniColors.Text,
            surface = DroniColors.Surface, onSurface = DroniColors.Text,
            surfaceVariant = DroniColors.Raised, onSurfaceVariant = DroniColors.Muted,
            outline = DroniColors.Outline, error = DroniColors.Red,
            onError = DroniColors.Background
        ),
        typography = base.copy(
            headlineLarge = style(36, 40, FontWeight.Bold),
            headlineMedium = style(28, 34, FontWeight.Bold),
            titleLarge = style(22, 28, FontWeight.SemiBold),
            titleMedium = style(16, 22, FontWeight.SemiBold),
            bodyLarge = style(15, 22), bodyMedium = style(13, 19),
            bodySmall = style(11, 16), labelLarge = style(13, 18, FontWeight.SemiBold),
            labelMedium = style(11, 16, FontWeight.Medium),
            labelSmall = style(10, 14, FontWeight.Medium)
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ), content = content
    )
}
