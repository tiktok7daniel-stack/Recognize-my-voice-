package com.daniel.assistant.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.daniel.assistant.commands.AppEntry
import com.daniel.assistant.ui.components.NeoTextField
import com.daniel.assistant.ui.rememberContainer
import com.daniel.assistant.ui.theme.Neo
import com.daniel.assistant.voice.FuzzyMatcher

@Composable
fun AppIcon(pkg: String, size: Int = 40) {
    val c = rememberContainer()
    val bmp = remember(pkg) { c.appIndex.icon(pkg) }
    if (bmp != null) {
        Image(bmp.asImageBitmap(), null, Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)))
    } else {
        Box(Modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(Neo.Bg2))
    }
}

/** Searchable list of installed apps with icons. */
@Composable
fun AppPickerDialog(onDismiss: () -> Unit, onPick: (AppEntry) -> Unit) {
    val c = rememberContainer()
    val apps by c.appEntriesFlow.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.map { a -> a to (a.spokenNames + a.packageName).maxOf { FuzzyMatcher.score(query, it) } }
            .filter { it.second > 0.45 || it.first.label.contains(query, true) }
            .sortedByDescending { it.second }
            .map { it.first }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Neo.Bg1,
        title = { Text("اختار تطبيق") },
        text = {
            Column {
                NeoTextField(query, { query = it }, "ابحث بالاسم (عربي أو إنجليزي)")
                Spacer(Modifier.padding(4.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(app) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.packageName)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = Neo.TextDim) } },
    )
}
