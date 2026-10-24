package com.daniel.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

/** "Neo-Tactile" palette: glassy dark surfaces, blue accent, cyan glow. */
object Neo {
    val Bg0 = Color(0xFF0A0D12)
    val Bg1 = Color(0xFF141922)
    val Bg2 = Color(0xFF1C2330)
    val Glass = Color(0x14FFFFFF)
    val GlassStrong = Color(0x22FFFFFF)
    val Stroke = Color(0x1FFFFFFF)
    val Accent = Color(0xFF3D7BFF)
    val AccentSoft = Color(0xFF5B8FFF)
    val Cyan = Color(0xFF4FE3FF)
    val Text = Color(0xFFEEF2F7)
    val TextDim = Color(0xFF8A94A6)
    val Pill = Color(0xFFE6EBF2)
    val PillText = Color(0xFF1A2130)
    val Success = Color(0xFF3DDC97)
    val Danger = Color(0xFFFF5C7A)
    val Warning = Color(0xFFFFB547)
}

private val scheme = darkColorScheme(
    primary = Neo.Accent,
    onPrimary = Color.White,
    secondary = Neo.Cyan,
    onSecondary = Neo.Bg0,
    background = Neo.Bg0,
    onBackground = Neo.Text,
    surface = Neo.Bg1,
    onSurface = Neo.Text,
    surfaceVariant = Neo.Bg2,
    onSurfaceVariant = Neo.TextDim,
    surfaceContainer = Neo.Bg1,
    surfaceContainerHigh = Neo.Bg2,
    surfaceContainerHighest = Neo.Bg2,
    outline = Neo.Stroke,
    error = Neo.Danger,
)

private val typography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Neo.Text),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Neo.Text),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Neo.Text),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Neo.Text),
    bodyLarge = TextStyle(fontSize = 16.sp, color = Neo.Text),
    bodyMedium = TextStyle(fontSize = 14.sp, color = Neo.Text),
    bodySmall = TextStyle(fontSize = 12.sp, color = Neo.TextDim),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun NabraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // The UI is Arabic-first: force RTL even if the phone language is not Arabic.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
