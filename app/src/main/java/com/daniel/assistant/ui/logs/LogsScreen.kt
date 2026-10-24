package com.daniel.assistant.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.daniel.assistant.ui.components.EmptyState
import com.daniel.assistant.ui.components.GlassCard
import com.daniel.assistant.ui.components.NabraBackground
import com.daniel.assistant.ui.components.ScreenHeader
import com.daniel.assistant.ui.rememberContainer
import com.daniel.assistant.ui.theme.Neo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(onBack: () -> Unit) {
    val c = rememberContainer()
    val logs by c.log.recent.collectAsState(initial = emptyList())
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.US) }
    NabraBackground {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("السجل", "الأوامر اللي اتنفذت والأخطاء", onBack) {
                IconButton(onClick = { c.log.clear() }) { Icon(Icons.Filled.DeleteSweep, "مسح", tint = Neo.TextDim) }
            }
            if (logs.isEmpty()) EmptyState(Icons.Filled.History, "السجل فاضي", "أول ما تنفذ أوامر هتظهر هنا")
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(logs, key = { it.id }) { l ->
                    val color = when (l.level) {
                        "ok" -> Neo.Success
                        "error" -> Neo.Danger
                        else -> Neo.Accent
                    }
                    GlassCard(padding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(l.message, style = MaterialTheme.typography.bodyMedium)
                                Text(fmt.format(Date(l.time)), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
