package com.daniel.assistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daniel.assistant.ui.theme.Neo
import kotlin.math.PI
import kotlin.math.sin

/**
 * The "Nabra" logo: a glowing cyan voice ring around five sound-wave bars.
 * [ringProgress] (0..1) draws the ring, [wavePhase] animates the bars.
 */
@Composable
fun NabraLogo(
    size: Dp,
    modifier: Modifier = Modifier,
    ringProgress: Float = 1f,
    wavePhase: Float = 0f,
    withTile: Boolean = true,
) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        if (withTile) {
            drawRoundRect(
                brush = Brush.linearGradient(listOf(Color(0xFF222B3B), Color(0xFF0B0F16)), Offset.Zero, Offset(s, s)),
                cornerRadius = CornerRadius(s * 0.26f),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.08f),
                cornerRadius = CornerRadius(s * 0.26f),
                style = Stroke(width = s * 0.012f),
            )
        }
        val ringR = s * 0.33f
        val center = Offset(s / 2, s / 2)
        val topLeft = Offset(center.x - ringR, center.y - ringR)
        val arcSize = Size(ringR * 2, ringR * 2)
        val sweep = 300f * ringProgress.coerceIn(0f, 1f)
        val ringBrush = Brush.sweepGradient(listOf(Neo.Cyan, Neo.Accent, Neo.Cyan), center)
        // glow
        drawArc(Neo.Cyan.copy(alpha = 0.18f), -20f, sweep, false, topLeft, arcSize,
            style = Stroke(width = s * 0.12f, cap = StrokeCap.Round))
        drawArc(ringBrush, -20f, sweep, false, topLeft, arcSize,
            style = Stroke(width = s * 0.055f, cap = StrokeCap.Round))
        // dot at ring start
        if (ringProgress > 0.98f) {
            drawCircle(Neo.Cyan, radius = s * 0.035f, center = Offset(center.x + ringR * 0.9397f, center.y - ringR * 0.3420f))
        }
        // sound bars
        val base = floatArrayOf(0.30f, 0.55f, 0.80f, 0.55f, 0.30f)
        val barW = s * 0.055f
        val gap = s * 0.035f
        val total = base.size * barW + (base.size - 1) * gap
        var x = center.x - total / 2
        val barBrush = Brush.verticalGradient(listOf(Neo.Cyan, Neo.Accent))
        for (i in base.indices) {
            val wave = if (wavePhase == 0f) 1f else 0.65f + 0.35f * sin(wavePhase + i * 0.9f).toFloat()
            val h = ringR * 1.05f * base[i] * wave
            drawRoundRect(
                brush = barBrush,
                topLeft = Offset(x, center.y - h / 2),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2),
            )
            x += barW + gap
        }
    }
}

@Composable
fun AnimatedLogo(size: Dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "logo")
    val phase by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "phase")
    NabraLogo(size, modifier, wavePhase = phase + 0.001f)
}

enum class OrbState { OFF, IDLE, AWAITING, PAUSED }

/** Big glowing orb on the home screen showing the listening state. */
@Composable
fun GlowOrb(state: OrbState, size: Dp = 220.dp, level: Float = 0f, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "orb")
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (state == OrbState.AWAITING) 1200 else 4000, easing = LinearEasing)), label = "rot")
    val pulse by t.animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val active = state == OrbState.IDLE || state == OrbState.AWAITING
    val ringColor = when (state) {
        OrbState.AWAITING -> Neo.Cyan
        OrbState.IDLE -> Neo.Accent
        OrbState.PAUSED -> Neo.Warning
        OrbState.OFF -> Neo.TextDim
    }
    Box(Modifier.size(size).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = this.size.minDimension
            val c = Offset(s / 2, s / 2)
            val scale = if (state == OrbState.AWAITING) maxOf(pulse, 0.95f + level * 0.25f) else 1f
            drawCircle(
                Brush.radialGradient(listOf(ringColor.copy(alpha = 0.35f), Color.Transparent), c, s * 0.5f * scale),
                radius = s * 0.5f * scale, center = c,
            )
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF2A3446), Color(0xFF121722)), c, s * 0.34f),
                radius = s * 0.34f, center = c,
            )
            drawCircle(Color.White.copy(alpha = 0.10f), radius = s * 0.34f, center = c, style = Stroke(s * 0.006f))
            val r = s * 0.40f
            if (active) {
                rotate(rot, c) {
                    drawArc(
                        Brush.sweepGradient(listOf(Color.Transparent, ringColor, Color.White), c),
                        0f, 300f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2),
                        style = Stroke(s * 0.03f, cap = StrokeCap.Round),
                    )
                }
            } else {
                drawCircle(ringColor.copy(alpha = 0.5f), radius = r, center = c, style = Stroke(s * 0.012f))
            }
        }
        Icon(
            if (state == OrbState.OFF || state == OrbState.PAUSED) Icons.Filled.MicOff else Icons.Filled.Mic,
            null,
            tint = if (active) Neo.Text else Neo.TextDim,
            modifier = Modifier.size(size * 0.22f),
        )
    }
}
