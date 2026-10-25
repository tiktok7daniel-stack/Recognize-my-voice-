package com.daniel.assistant.ui.commands

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.daniel.assistant.commands.CustomCommand
import com.daniel.assistant.ui.components.EmptyState
import com.daniel.assistant.ui.components.GlassCard
import com.daniel.assistant.ui.components.NabraBackground
import com.daniel.assistant.ui.components.ScreenHeader
import com.daniel.assistant.ui.rememberContainer
import com.daniel.assistant.ui.theme.Neo
import kotlinx.coroutines.launch

@Composable
fun CommandsListScreen(onNew: () -> Unit, onEdit: (Long) -> Unit) {
    val c = rememberContainer()
    val scope = rememberCoroutineScope()
    val commands by c.repo.commands.collectAsState(initial = emptyList())
    var toDelete by remember { mutableStateOf<CustomCommand?>(null) }

    NabraBackground {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("أوامري", "كلمة واحدة ← سلسلة خطوات")
            if (commands.isEmpty()) {
                EmptyState(Icons.Filled.RecordVoiceOver, "مفيش أوامر لسه", "دوس \"أمر جديد\" وسجّل كلمتك واختار الخطوات")
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 170.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(commands, key = { it.id }) { cmd ->
                    CommandCard(
                        cmd,
                        onToggle = { scope.launch { c.repo.setEnabled(cmd.id, it) } },
                        onTest = { c.executor.run(cmd) },
                        onEdit = { onEdit(cmd.id) },
                        onDelete = { toDelete = cmd },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNew,
            containerColor = Neo.Accent,
            contentColor = Neo.Text,
            shape = CircleShape,
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("أمر جديد") },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 100.dp),
        )
    }

    toDelete?.let { cmd ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = Neo.Bg1,
            title = { Text("امسح \"${cmd.name}\"؟") },
            text = { Text("الأمر ده هيتمسح نهائيًا.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { c.repo.delete(cmd.id) }
                    toDelete = null
                }) { Text("امسح", color = Neo.Danger) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("إلغاء", color = Neo.TextDim) } },
        )
    }
}

@Composable
private fun CommandCard(
    cmd: CustomCommand,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassCard(glow = if (cmd.enabled) null else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(cmd.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${cmd.steps.size} خطوات" +
                        (cmd.autoTime?.let { " • ⏰ $it" } ?: "") +
                        (if (cmd.autoApp != null) " • 📱 تلقائي" else "") +
                        (if (cmd.autoButton != null) " • 🔘 زرار" else ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = cmd.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = Neo.Accent, checkedThumbColor = Neo.Pill),
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            cmd.triggers.forEach { t ->
                Box(
                    Modifier.padding(end = 6.dp).clip(CircleShape).background(Neo.Accent.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) { Text("🎙 $t", style = MaterialTheme.typography.bodyMedium, color = Neo.Text) }
            }
        }
        cmd.steps.take(4).forEachIndexed { i, s ->
            Text("${i + 1}. ${s.describe()}", style = MaterialTheme.typography.bodySmall)
        }
        if (cmd.steps.size > 4) Text("…", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onTest) {
                Icon(Icons.Filled.PlayArrow, null, tint = Neo.Cyan)
                Spacer(Modifier.width(4.dp))
                Text("تجربة", color = Neo.Cyan)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "تعديل", tint = Neo.Text) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "مسح", tint = Neo.Danger) }
        }
    }
}
