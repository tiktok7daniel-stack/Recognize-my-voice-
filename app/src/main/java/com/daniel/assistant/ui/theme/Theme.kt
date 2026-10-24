package com.daniel.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.daniel.assistant.R
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

val Tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.SemiBold),
    Font(R.font.tajawal_bold, FontWeight.Bold),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold),
)

private val base = Typography()

private fun TextStyle.t(size: Int? = null, weight: FontWeight? = null, color: Color? = null) = copy(
    fontFamily = Tajawal,
    fontSize = size?.sp ?: fontSize,
    fontWeight = weight ?: fontWeight,
    color = color ?: this.color,
)

private val typography = Typography(
    displayLarge = base.displayLarge.t(weight = FontWeight.ExtraBold),
    displayMedium = base.displayMedium.t(weight = FontWeight.ExtraBold),
    displaySmall = base.displaySmall.t(34, FontWeight.ExtraBold, Neo.Text),
    headlineLarge = base.headlineLarge.t(weight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.t(28, FontWeight.Bold, Neo.Text),
    headlineSmall = base.headlineSmall.t(24, FontWeight.Bold, Neo.Text),
    titleLarge = base.titleLarge.t(20, FontWeight.Bold, Neo.Text),
    titleMedium = base.titleMedium.t(16, FontWeight.Bold, Neo.Text),
    titleSmall = base.titleSmall.t(weight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.t(16, FontWeight.Normal, Neo.Text),
    bodyMedium = base.bodyMedium.t(14, FontWeight.Normal, Neo.Text),
    bodySmall = base.bodySmall.t(12, FontWeight.Normal, Neo.TextDim),
    labelLarge = base.labelLarge.t(15, FontWeight.Bold),
    labelMedium = base.labelMedium.t(weight = FontWeight.Medium),
    labelSmall = base.labelSmall.t(weight = FontWeight.Medium),
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
