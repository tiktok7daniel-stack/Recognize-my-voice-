package com.daniel.assistant.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daniel.assistant.data.AppSettings
import com.daniel.assistant.ui.components.GlassCard
import com.daniel.assistant.ui.components.GlowOrb
import com.daniel.assistant.ui.components.OrbState
import com.daniel.assistant.ui.components.PillButton
import com.daniel.assistant.ui.components.SettingSwitch
import com.daniel.assistant.ui.rememberContainer
import com.daniel.assistant.ui.theme.Neo
import com.daniel.assistant.voice.SpeakerLock
import kotlinx.coroutines.launch

/** Settings card for the voice lock (only the owner's voice triggers commands). */
@Composable
fun VoiceLockSection(settings: AppSettings) {
    val c = rememberContainer()
    val scope = rememberCoroutineScope()
    val status by c.speaker.status.collectAsState()
    val lastSim by c.speaker.lastSimilarity.collectAsState()
    var threshold by remember(settings.voiceLockThreshold) { mutableFloatStateOf(settings.voiceLockThreshold) }
    var enrolling by remember { mutableStateOf(false) }
    val enrolled = settings.voicePrint.isNotBlank()

    GlassCard {
        SettingSwitch(
            "قفل الصوت (بصمة صوتك)",
            "كلمة التنبيه وأوامرك تشتغل بصوتك أنت بس",
            settings.voiceLock,
        ) { v ->
            scope.launch {
                if (v && !c.speaker.isDownloaded() && !c.speaker.download()) return@launch
                c.settings.setVoiceLock(v)
                if (v && !enrolled) enrolling = true
            }
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Neo.Cyan) }
        Text(
            if (enrolled) "✅ بصمتك متسجلة" else "لسه مسجلتش بصمتك",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(6.dp))
        PillButton(
            if (enrolled) "سجّل بصمتك تاني" else "سجّل بصمتك",
            {
                scope.launch {
                    if (c.speaker.isDownloaded() || c.speaker.download()) enrolling = true
                }
            },
            icon = Icons.Filled.Fingerprint,
            primary = !enrolled,
        )
        if (settings.voiceLock && enrolled) {
            Text("درجة التشدد: ${(threshold * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp))
            Slider(
                value = threshold,
                onValueChange = { threshold = it },
                onValueChangeFinished = { scope.launch { c.settings.setVoiceLockThreshold(threshold) } },
                valueRange = 0.3f..0.85f,
                colors = SliderDefaults.colors(thumbColor = Neo.Pill, activeTrackColor = Neo.Accent),
            )
            Text(
                "آخر تطابق: " + (lastSim?.let { "${(it * 100).toInt()}%" } ?: "—") +
                    ". لو مش بيسمعك أنت، قلّل التشدد. لو بيشتغل بصوت حد تاني، زوّده.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            "بيحتاج موديل صغير (13 ميجا) بيتنزل مرة واحدة. البصمة بتتحفظ على موبايلك بس.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
        )
    }

    if (enrolling) EnrollDialog(wakeWord = settings.wakeWord) { enrolling = false }
}

@Composable
private fun EnrollDialog(wakeWord: String, onClose: () -> Unit) {
    val c = rememberContainer()
    val scope = rememberCoroutineScope()
    val vectors = remember { mutableStateListOf<FloatArray>() }
    var listening by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var take by remember { mutableIntStateOf(0) }
    val needed = 3

    fun record() {
        message = null
        scope.launch {
            if (!c.voice.loadModel()) { message = "موديل الصوت مش متنزل"; return@launch }
            c.voice.speakerModel = c.speaker.load()
            if (c.voice.speakerModel == null) { message = "موديل البصمة مش جاهز"; return@launch }
            listening = true
            c.feedback.cue()
            val text = c.voice.captureOnce(null, 5_000)
            listening = false
            val v = c.voice.lastSpeakerVector
            if (text.isNullOrBlank() || v == null) {
                message = "مسمعتش كويس، قولها تاني بصوت واضح"
            } else {
                vectors.add(v); take++
                if (vectors.size >= needed) {
                    SpeakerLock.average(vectors)?.let { c.settings.setVoicePrint(SpeakerLock.encode(it)) }
                    message = "تمام! بصمتك اتسجلت ✅"
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!listening) onClose() },
        containerColor = Neo.Bg1,
        title = { Text("سجّل بصمة صوتك") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("قول \"$wakeWord\" بصوتك العادي — ${vectors.size.coerceAtMost(needed)}/$needed", style = MaterialTheme.typography.bodyLarge)
                GlowOrb(if (listening) OrbState.AWAITING else OrbState.IDLE, size = 140.dp) {
                    if (!listening && vectors.size < needed) record()
                }
                Text(if (listening) "بسمعك…" else if (vectors.size < needed) "دوس على الدايرة وقول الكلمة" else "", color = Neo.Cyan)
                message?.let { Text(it, color = if (vectors.size >= needed) Neo.Success else Neo.Warning) }
            }
        },
        confirmButton = {
            TextButton(enabled = !listening, onClick = onClose) {
                Text(if (vectors.size >= needed) "تمام" else "إلغاء", color = Neo.Cyan)
            }
        },
    )
}
