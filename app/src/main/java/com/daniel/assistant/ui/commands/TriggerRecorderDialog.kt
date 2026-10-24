package com.daniel.assistant.ui.commands

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.daniel.assistant.ui.components.GlowOrb
import com.daniel.assistant.ui.components.NeoTextField
import com.daniel.assistant.ui.components.OrbState
import com.daniel.assistant.ui.components.PillButton
import com.daniel.assistant.ui.onboarding.Permissions
import com.daniel.assistant.ui.rememberContainer
import com.daniel.assistant.ui.theme.Neo
import kotlinx.coroutines.launch

/**
 * Records the trigger word 2–3 times with free-form Vosk recognition and
 * collects every recognized variant as an accepted trigger.
 */
@Composable
fun TriggerRecorderDialog(
    title: String = "سجّل كلمة التشغيل",
    maxTakes: Int = 3,
    onDismiss: () -> Unit,
    onDone: (List<String>) -> Unit,
) {
    val c = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val variants = remember { mutableStateListOf<String>() }
    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var current by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun record() {
        error = null; current = null; partial = ""
        scope.launch {
            if (!c.voice.loadModel()) {
                error = "موديل الصوت مش متنزل"; return@launch
            }
            listening = true
            c.feedback.cue()
            val text = c.voice.captureOnce(null, 5_000) { partial = it }
            listening = false
            if (text.isNullOrBlank()) error = "مسمعتش حاجة، جرّب تاني" else current = text
        }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) record() }
    fun start() = if (Permissions.mic(context)) record() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)

    AlertDialog(
        onDismissRequest = { if (!listening) onDismiss() },
        containerColor = Neo.Bg1,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (variants.isEmpty()) "قول الكلمة اللي عايزها" else "قولها تاني (${variants.size}/$maxTakes) عشان أتعرف عليها أحسن",
                    style = MaterialTheme.typography.bodyLarge,
                )
                GlowOrb(if (listening) OrbState.AWAITING else OrbState.IDLE, size = 140.dp) {
                    if (!listening) start()
                }
                if (listening) Text(partial.ifBlank { "بسمعك…" }, color = Neo.Cyan)
                error?.let { Text(it, color = Neo.Danger) }
                current?.let { heard ->
                    var edit by remember(heard) { mutableStateOf(heard) }
                    NeoTextField(edit, { edit = it }, "اللي سمعته (تقدر تعدله)")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("تأكيد", {
                            val v = edit.trim()
                            if (v.isNotBlank() && v !in variants) variants.add(v)
                            current = null
                        })
                        PillButton("إعادة", { record() }, primary = false)
                    }
                }
                if (variants.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("الصيغ المقبولة: " + variants.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = variants.isNotEmpty() && !listening, onClick = { onDone(variants.toList()) }) {
                Text("خلاص", color = if (variants.isNotEmpty()) Neo.Cyan else Neo.TextDim)
            }
        },
        dismissButton = {
            TextButton(enabled = !listening, onClick = onDismiss) { Text("إلغاء", color = Neo.TextDim) }
        },
    )
}
