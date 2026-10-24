package com.daniel.assistant.ui.aliases

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.daniel.assistant.commands.AppEntry
import com.daniel.assistant.ui.apps.AppIcon
import com.daniel.assistant.ui.commands.TriggerRecorderDialog
import com.daniel.assistant.ui.components.GlassCard
import com.daniel.assistant.ui.components.NabraBackground
import com.daniel.assistant.ui.components.NeoTextField
import com.daniel.assistant.ui.components.ScreenHeader
import com.daniel.assistant.ui.rememberContainer
import com.daniel.assistant.ui.theme.Neo
import com.daniel.assistant.voice.FuzzyMatcher
import kotlinx.coroutines.launch

/** Give any app extra spoken names (e.g. يوتيوب → YouTube, كلود → Claude). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AliasesScreen() {
    val c = rememberContainer()
    val scope = rememberCoroutineScope()
    val apps by c.appEntriesFlow.collectAsState(initial = emptyList())
    val aliases by c.repo.aliases.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf<AppEntry?>(null) }
    var recordingFor by remember { mutableStateOf<AppEntry?>(null) }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { a -> a.label.contains(query, true) || a.spokenNames.any { FuzzyMatcher.score(query, it) > 0.6 } }
    }

    NabraBackground {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("أسماء التطبيقات", "قول \"افتح\" + أي اسم من دول")
            Column(Modifier.padding(horizontal = 16.dp)) {
                NeoTextField(query, { query = it }, "ابحث عن تطبيق")
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.packageName }) { app ->
                    val userAliases = aliases.filter { it.packageName == app.packageName }
                    GlassCard(padding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(app.packageName, 42)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.titleMedium)
                                if (app.builtinAliases.isNotEmpty()) {
                                    Text("تلقائي: " + app.builtinAliases.joinToString("، "), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = { recordingFor = app }) { Icon(Icons.Filled.Mic, "سجّل اسم", tint = Neo.Cyan) }
                            IconButton(onClick = { adding = app }) { Icon(Icons.Filled.Add, "أضف اسم", tint = Neo.Cyan) }
                        }
                        if (userAliases.isNotEmpty()) {
                            FlowRow(
                                Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                userAliases.forEach { a ->
                                    Row(
                                        Modifier.clip(CircleShape).background(Neo.Accent.copy(alpha = 0.2f)).padding(start = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(a.alias)
                                        IconButton(onClick = { scope.launch { c.repo.deleteAlias(a) } }, modifier = Modifier.size(32.dp)) {
                                            Icon(Icons.Filled.Close, "مسح", tint = Neo.TextDim, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    adding?.let { app ->
        var text by remember(app) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = null },
            containerColor = Neo.Bg1,
            title = { Text("اسم جديد لـ ${app.label}") },
            text = { NeoTextField(text, { text = it }, "مثلاً: يوتيوب") },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) scope.launch { c.repo.addAlias(app.packageName, app.label, text) }
                    adding = null
                }) { Text("إضافة", color = Neo.Cyan) }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text("إلغاء", color = Neo.TextDim) } },
        )
    }
    recordingFor?.let { app ->
        TriggerRecorderDialog(title = "قول اسم ${app.label}", maxTakes = 2, onDismiss = { recordingFor = null }) { names ->
            scope.launch { names.forEach { c.repo.addAlias(app.packageName, app.label, it) } }
            recordingFor = null
        }
    }
}
