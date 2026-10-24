package com.daniel.assistant.ui.commands

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.daniel.assistant.commands.CustomCommand
import com.daniel.assistant.commands.Step
import com.daniel.assistant.commands.StepType
import com.daniel.assistant.commands.type
import com.daniel.assistant.ui.components.GlassCard
import com.daniel.assistant.ui.components.IconBubble
import com.daniel.assistant.ui.components.NabraBackground
import com.daniel.assistant.ui.components.NeoTextField
import com.daniel.assistant.ui.components.PillButton
import com.daniel.assistant.ui.components.ScreenHeader
import com.daniel.assistant.ui.components.SectionTitle
import com.daniel.assistant.ui.rememberContainer
import com.daniel.assistant.ui.theme.Neo
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private data class UiStep(val key: Long, val step: Step)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommandEditorScreen(commandId: Long?, onBack: () -> Unit) {
    val c = rememberContainer()
    val scope = rememberCoroutineScope()
    val settings by c.settings.flow.collectAsState(initial = null)

    var name by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    val triggers = remember { mutableStateListOf<String>() }
    val steps = remember { mutableStateListOf<UiStep>() }
    var nextKey by remember { mutableLongStateOf(1L) }
    var loaded by remember { mutableStateOf(commandId == null) }

    var showRecorder by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var showTypePicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<StepType, Int?>?>(null) } // type + index (null = new)
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(commandId) {
        if (commandId != null) {
            c.repo.get(commandId)?.let { cmd ->
                name = cmd.name
                enabled = cmd.enabled
                triggers.clear(); triggers.addAll(cmd.triggers)
                steps.clear(); cmd.steps.forEach { steps.add(UiStep(nextKey++, it)) }
            }
            loaded = true
        }
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIdx = steps.indexOfFirst { it.key == from.key }
        val toIdx = steps.indexOfFirst { it.key == to.key }
        if (fromIdx >= 0 && toIdx >= 0) steps.add(toIdx, steps.removeAt(fromIdx))
    }

    fun save() {
        val finalName = name.ifBlank { triggers.firstOrNull().orEmpty() }
        error = when {
            triggers.isEmpty() -> "سجّل كلمة التشغيل الأول"
            steps.isEmpty() -> "أضف خطوة واحدة على الأقل"
            else -> null
        }
        if (error != null) return
        scope.launch {
            c.repo.save(
                CustomCommand(
                    id = commandId ?: 0,
                    name = finalName,
                    enabled = enabled,
                    triggers = triggers.toList(),
                    steps = steps.map { it.step },
                )
            )
            onBack()
        }
    }

    NabraBackground {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(if (commandId == null) "أمر جديد" else "تعديل الأمر", onBack = onBack) {
                IconButton(onClick = {
                    if (steps.isNotEmpty()) c.executor.run(CustomCommand(name = name.ifBlank { "تجربة" }, triggers = emptyList(), steps = steps.map { it.step }))
                }) { Icon(Icons.Filled.PlayArrow, "تجربة", tint = Neo.Cyan) }
            }
            if (!loaded) return@Column
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (commandId == null && settings?.editorHintsSeen == false) {
                    item(key = "hint") {
                        GlassCard(glow = Neo.Cyan) {
                            Text("إزاي تعمل الأمر؟", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            listOf(
                                "سجّل كلمة التشغيل بصوتك ٢-٣ مرات (أو اكتبها).",
                                "دوس \"أضف خطوة\" وضيف الخطوات بالترتيب، واسحب ☰ لتغيير الترتيب.",
                                "جرّب بزرار ▶ فوق، وبعدين دوس \"حفظ الأمر\".",
                            ).forEachIndexed { i, t ->
                                Text("${i + 1}. $t", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                            }
                            TextButton(onClick = { scope.launch { c.settings.setEditorHintsSeen(true) } }) {
                                Text("فهمت", color = Neo.Cyan)
                            }
                        }
                    }
                }
                item(key = "name") {
                    NeoTextField(name, { name = it }, "اسم الأمر (اختياري)")
                }
                item(key = "triggers") {
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Icons.Filled.Mic)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("كلمة التشغيل", style = MaterialTheme.typography.titleMedium)
                                Text("سجّلها ٢–٣ مرات عشان الدقة", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            triggers.forEach { t ->
                                Row(
                                    Modifier.clip(CircleShape).background(Neo.Accent.copy(alpha = 0.2f)).padding(start = 12.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(t, style = MaterialTheme.typography.bodyLarge)
                                    IconButton(onClick = { triggers.remove(t) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Filled.Close, "مسح", tint = Neo.TextDim, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("سجّل بصوتك", { showRecorder = true }, icon = Icons.Filled.Mic)
                            PillButton("اكتب يدوي", { showManual = true }, primary = false, icon = Icons.Filled.Edit)
                        }
                    }
                }
                item(key = "steps_title") { SectionTitle("الخطوات (اسحب ☰ لترتيبها)") }
                itemsIndexed(steps, key = { _, s -> s.key }) { index, ui ->
                    ReorderableItem(reorderState, key = ui.key) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 16.dp else 0.dp, label = "drag")
                        GlassCard(
                            modifier = Modifier.shadow(elevation, CircleShape),
                            glow = if (isDragging) Neo.Cyan else null,
                            onClick = { editing = ui.step.type() to index },
                            padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(26.dp).clip(CircleShape).background(Neo.Accent),
                                    contentAlignment = Alignment.Center,
                                ) { Text("${index + 1}", style = MaterialTheme.typography.bodySmall, color = Neo.Text) }
                                Spacer(Modifier.width(10.dp))
                                IconBubble(ui.step.type().icon(), size = 36)
                                Spacer(Modifier.width(10.dp))
                                Text(ui.step.describe(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                IconButton(onClick = { steps.removeAt(steps.indexOfFirst { it.key == ui.key }) }) {
                                    Icon(Icons.Filled.Delete, "مسح", tint = Neo.Danger)
                                }
                                IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                    Icon(Icons.Filled.DragHandle, "اسحب", tint = Neo.TextDim)
                                }
                            }
                        }
                    }
                }
                item(key = "add") {
                    GlassCard(onClick = { showTypePicker = true }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, null, tint = Neo.Cyan)
                            Spacer(Modifier.width(8.dp))
                            Text("أضف خطوة", color = Neo.Cyan, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                item(key = "space") { Spacer(Modifier.height(20.dp)) }
            }
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                error?.let { Text(it, color = Neo.Danger, modifier = Modifier.padding(bottom = 8.dp)) }
                PillButton("حفظ الأمر", { save() }, Modifier.fillMaxWidth())
            }
        }
    }

    if (showRecorder) {
        TriggerRecorderDialog(onDismiss = { showRecorder = false }) { vs ->
            vs.forEach { if (it !in triggers) triggers.add(it) }
            if (name.isBlank()) name = vs.first()
            showRecorder = false
        }
    }
    if (showManual) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManual = false },
            containerColor = Neo.Bg1,
            title = { Text("اكتب كلمة التشغيل") },
            text = { NeoTextField(text, { text = it }, "مثلاً: واحد") },
            confirmButton = {
                TextButton(onClick = {
                    val t = text.trim()
                    if (t.isNotBlank() && t !in triggers) triggers.add(t)
                    showManual = false
                }) { Text("إضافة", color = Neo.Cyan) }
            },
            dismissButton = { TextButton(onClick = { showManual = false }) { Text("إلغاء", color = Neo.TextDim) } },
        )
    }
    if (showTypePicker) {
        StepTypePicker(onDismiss = { showTypePicker = false }) { t ->
            showTypePicker = false
            editing = t to null
        }
    }
    editing?.let { (type, index) ->
        StepParamsDialog(
            type = type,
            initial = index?.let { steps.getOrNull(it)?.step },
            onDismiss = { editing = null },
        ) { step ->
            if (index == null) steps.add(UiStep(nextKey++, step))
            else steps[index] = steps[index].copy(step = step)
            editing = null
        }
    }
}
