package com.daniel.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.daniel.assistant.ui.theme.Neo

@Composable
fun NabraBackground(content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Neo.Bg1, Neo.Bg0, Color(0xFF07090D))))
    ) {
        // two slowly drifting glows (accent + cyan), like the reference kit
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawCircle(
                Brush.radialGradient(
                    listOf(Neo.Accent.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(w * (0.15f + 0.25f * drift), h * (0.10f + 0.08f * drift)),
                    radius = w * 0.75f,
                ),
                radius = w * 0.75f,
                center = Offset(w * (0.15f + 0.25f * drift), h * (0.10f + 0.08f * drift)),
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(Neo.Cyan.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(w * (0.95f - 0.3f * drift), h * (0.75f - 0.1f * drift)),
                    radius = w * 0.65f,
                ),
                radius = w * 0.65f,
                center = Offset(w * (0.95f - 0.3f * drift), h * (0.75f - 0.1f * drift)),
            )
        }
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    glow: Color? = null,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(if (glow != null) 18.dp else 6.dp, shape, ambientColor = glow ?: Color.Black, spotColor = glow ?: Color.Black)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Neo.GlassStrong, Neo.Glass)))
            .border(BorderStroke(1.dp, (glow ?: Color.White).copy(alpha = if (glow != null) 0.45f else 0.10f)), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val bg = when {
        danger -> Neo.Danger
        primary -> Neo.Accent
        else -> Neo.Pill
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp).shadow(if (primary && enabled) 10.dp else 2.dp, CircleShape, spotColor = bg),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = if (primary || danger) Color.White else Neo.PillText,
            disabledContainerColor = Neo.Bg2,
            disabledContentColor = Neo.TextDim,
        ),
        contentPadding = PaddingValues(horizontal = 22.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatusChip(ok: Boolean, okText: String = "متاح", badText: String = "ناقص") {
    val color by animateColorAsState(if (ok) Neo.Success else Neo.Warning, label = "chip")
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(if (ok) okText else badText, color = color, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp),
        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        color = Neo.TextDim,
    )
}

@Composable
fun SettingSwitch(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Neo.Accent,
                checkedThumbColor = Neo.Pill,
                uncheckedTrackColor = Neo.Bg2,
                uncheckedThumbColor = Neo.TextDim,
                uncheckedBorderColor = Neo.Stroke,
            ),
        )
    }
}

@Composable
fun NeoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        visualTransformation = visualTransformation,
        trailingIcon = trailing,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Neo.Accent,
            unfocusedBorderColor = Neo.Stroke,
            focusedContainerColor = Neo.Glass,
            unfocusedContainerColor = Neo.Glass,
            cursorColor = Neo.Cyan,
            focusedLabelColor = Neo.AccentSoft,
            unfocusedLabelColor = Neo.TextDim,
        ),
    )
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Neo.Text)
            }
        } else Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            if (subtitle != null) Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
        actions()
    }
}

@Composable
fun IconBubble(icon: ImageVector, tint: Color = Neo.Cyan, size: Int = 44) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.28f), tint.copy(alpha = 0.08f))))
            .border(1.dp, tint.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.5).dp))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconBubble(icon, size = 64)
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        Text(text, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
    }
}
