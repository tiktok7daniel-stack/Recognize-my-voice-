package com.daniel.assistant.ui.commands

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.daniel.assistant.commands.AppEntry
import com.daniel.assistant.commands.Step
import com.daniel.assistant.commands.StepType
import com.daniel.assistant.commands.SysAction
import com.daniel.assistant.ui.apps.AppIcon
import com.daniel.assistant.ui.apps.AppPickerDialog
import com.daniel.assistant.ui.components.IconBubble
import com.daniel.assistant.ui.components.NeoTextField
import com.daniel.assistant.ui.components.PillButton
import com.daniel.assistant.ui.theme.Neo
import kotlin.math.roundToInt

fun StepType.icon(): ImageVector = when (this) {
    StepType.OPEN_APP -> Icons.Filled.Apps
    StepType.ATTACH_PHOTOS -> Icons.Filled.PhotoLibrary
    StepType.TYPE_TEXT -> Icons.Filled.Keyboard
    StepType.PRESS_SEND -> Icons.AutoMirrored.Filled.Send
    StepType.CLICK_TEXT -> Icons.Filled.TouchApp
    StepType.WAIT -> Icons.Filled.Timer
    StepType.BACK -> Icons.AutoMirrored.Filled.ArrowBack
    StepType.HOME -> Icons.Filled.Home
    StepType.SCROLL -> Icons.Filled.SwapVert
    StepType.SYSTEM -> Icons.Filled.Settings
}

/** Picker of action types for a new step. */
@Composable
fun StepTypePicker(onDismiss: () -> Unit, onPick: (StepType) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Neo.Bg1,
        title = { Text("نوع الخطوة") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(StepType.entries) { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(t) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBubble(t.icon(), size = 38)
                        Spacer(Modifier.width(12.dp))
                        Text(t.title, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = Neo.TextDim) } },
    )
}

/** Parameter editor for one step; steps without parameters are returned immediately. */
@Composable
fun StepParamsDialog(type: StepType, initial: Step?, onDismiss: () -> Unit, onSave: (Step) -> Unit) {
    val immediate = when (type) {
        StepType.PRESS_SEND -> Step.PressSend
        StepType.BACK -> Step.Back
        StepType.HOME -> Step.Home
        else -> null
    }
    if (immediate != null) {
        LaunchedEffect(type) { onSave(immediate) }
        return
    }

    var text by remember { mutableStateOf(
        when (initial) {
            is Step.TypeText -> initial.text
            is Step.ClickText -> initial.text
            is Step.System -> initial.arg ?: ""
            is Step.AttachPhotos -> initial.text ?: ""
            else -> ""
        }
    ) }
    var app by remember { mutableStateOf(
        when (initial) {
            is Step.OpenApp -> AppEntry(initial.label, initial.packageName)
            is Step.AttachPhotos -> AppEntry(initial.label, initial.packageName)
            else -> null
        }
    ) }
    var count by remember { mutableFloatStateOf(((initial as? Step.AttachPhotos)?.count ?: 2).toFloat()) }
    var millis by remember { mutableFloatStateOf(((initial as? Step.Wait)?.millis ?: 1500L).toFloat()) }
    var down by remember { mutableStateOf((initial as? Step.Scroll)?.down ?: true) }
    var sys by remember { mutableStateOf((initial as? Step.System)?.let { SysAction.byId(it.actionId) } ?: SysAction.VOLUME_UP) }
    var pickApp by remember { mutableStateOf(false) }

    val result: Step? = when (type) {
        StepType.OPEN_APP -> app?.let { Step.OpenApp(it.packageName, it.label) }
        StepType.ATTACH_PHOTOS -> app?.let { Step.AttachPhotos(count.roundToInt(), it.packageName, it.label, text.takeIf { t -> t.isNotBlank() }) }
        StepType.TYPE_TEXT -> text.takeIf { it.isNotBlank() }?.let { Step.TypeText(it) }
        StepType.CLICK_TEXT -> text.takeIf { it.isNotBlank() }?.let { Step.ClickText(it.trim()) }
        StepType.WAIT -> Step.Wait(millis.roundToInt().toLong())
        StepType.SCROLL -> Step.Scroll(down)
        StepType.SYSTEM -> if (sys.takesArg && text.isBlank()) null else Step.System(sys.id, text.takeIf { sys.takesArg })
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Neo.Bg1,
        title = { Text(type.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (type) {
                    StepType.OPEN_APP, StepType.ATTACH_PHOTOS -> {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { pickApp = true }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (app != null) AppIcon(app!!.packageName) else IconBubble(Icons.Filled.Apps, size = 40)
                            Spacer(Modifier.width(12.dp))
                            Text(app?.label ?: "اختار التطبيق…", style = MaterialTheme.typography.bodyLarge)
                        }
                        if (type == StepType.ATTACH_PHOTOS) {
                            Text("عدد الصور: ${count.roundToInt()}")
                            Slider(
                                value = count, onValueChange = { count = it }, valueRange = 1f..10f, steps = 8,
                                colors = SliderDefaults.colors(thumbColor = Neo.Pill, activeTrackColor = Neo.Accent),
                            )
                            NeoTextField(text, { text = it }, "نص يتبعت مع الصور (اختياري)")
                            Text("بيتبعتوا مباشرة للتطبيق بالمشاركة (أضمن من الضغط على الشاشة).", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    StepType.TYPE_TEXT -> {
                        NeoTextField(text, { text = it }, "النص", singleLine = false)
                        Text("تقدر تكتب متغير زي {رسالة} لو كلمة التشغيل فيها متغيرات.", style = MaterialTheme.typography.bodySmall)
                    }
                    StepType.CLICK_TEXT -> NeoTextField(text, { text = it }, "النص أو الوصف اللي على الزرار")
                    StepType.WAIT -> {
                        Text("${millis.roundToInt()} ms")
                        Slider(
                            value = millis, onValueChange = { millis = (it / 100).roundToInt() * 100f }, valueRange = 100f..10_000f,
                            colors = SliderDefaults.colors(thumbColor = Neo.Pill, activeTrackColor = Neo.Accent),
                        )
                    }
                    StepType.SCROLL -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(down, { down = true }, { Text("انزل") }, colors = chipColors())
                        FilterChip(!down, { down = false }, { Text("اطلع") }, colors = chipColors())
                    }
                    StepType.SYSTEM -> {
                        LazyColumn(Modifier.heightIn(max = 260.dp)) {
                            items(SysAction.entries) { a ->
                                FilterChip(
                                    selected = sys == a, onClick = { sys = a }, label = { Text(a.title) },
                                    colors = chipColors(), modifier = Modifier.padding(vertical = 2.dp),
                                )
                            }
                        }
                        if (sys.takesArg) NeoTextField(text, { text = it }, "النص")
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            PillButton("حفظ", { result?.let(onSave) }, enabled = result != null)
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = Neo.TextDim) } },
    )

    if (pickApp) {
        AppPickerDialog(onDismiss = { pickApp = false }) { app = it; pickApp = false }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Neo.Accent,
    selectedLabelColor = Neo.Text,
    labelColor = Neo.TextDim,
)
