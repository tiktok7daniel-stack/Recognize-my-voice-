package com.daniel.assistant.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.daniel.assistant.service.ListeningService
import com.daniel.assistant.ui.components.GlassCard
import com.daniel.assistant.ui.components.GlowOrb
import com.daniel.assistant.ui.components.IconBubble
import com.daniel.assistant.ui.components.NabraBackground
import com.daniel.assistant.ui.components.NabraLogo
import com.daniel.assistant.ui.components.NeoTextField
import com.daniel.assistant.ui.components.OrbState
import com.daniel.assistant.ui.components.PillButton
import com.daniel.assistant.ui.components.SettingSwitch
import com.daniel.assistant.ui.onboarding.Permissions
import com.daniel.assistant.ui.rememberContainer
import com.daniel.assistant.ui.theme.Neo
import com.daniel.assistant.voice.ModelState
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onOpenPermissions: () -> Unit, onOpenModel: () -> Unit) {
    val c = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by ListeningService.state.collectAsState()
    val heard by c.brain.lastHeard.collectAsState()
    val result by c.brain.lastResult.collectAsState()
    val running by c.executor.running.collectAsState()
    val aiRunning by c.ai.running.collectAsState()
    val model by c.models.state.collectAsState()
    val settings by c.settings.flow.collectAsState(initial = null)
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    @Suppress("UNUSED_VARIABLE") val t = tick
    val micOk = Permissions.mic(context)
    val a11yOk = Permissions.accessibility(context)

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        tick++
        if (granted) ListeningService.start(context)
    }
    fun startService() {
        if (!micOk) micLauncher.launch(Manifest.permission.RECORD_AUDIO) else ListeningService.start(context)
    }

    val orb = when {
        !st.running -> OrbState.OFF
        st.paused -> OrbState.PAUSED
        st.awaitingCommand -> OrbState.AWAITING
        else -> OrbState.IDLE
    }
    val wake = settings?.wakeWord ?: "يا مساعد"

    NabraBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                NabraLogo(40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("نبرة", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (st.grammarMode) "أوفلاين • وضع الأوامر الدقيق" else "أوفلاين • كلام حر",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (model !is ModelState.Ready) {
                Warning(Icons.Filled.CloudDownload, "موديل الصوت مش متنزل", "نزّله عشان المساعد يشتغل", onOpenModel)
            }
            if (!micOk || !a11yOk) {
                Warning(
                    if (!a11yOk) Icons.Filled.Accessibility else Icons.Filled.Warning,
                    if (!a11yOk) "خدمة إمكانية الوصول مقفولة" else "صلاحية الميكروفون ناقصة",
                    "اضغط هنا وكمّل الصلاحيات",
                    onOpenPermissions,
                )
            }

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                GlowOrb(orb) {
                    if (st.running) ListeningService.stop(context) else startService()
                }
                Text(
                    when (orb) {
                        OrbState.OFF -> "المساعد واقف — دوس على الدايرة"
                        OrbState.PAUSED -> "متوقف مؤقتًا"
                        OrbState.AWAITING -> "بسمعك… قول الأمر"
                        OrbState.IDLE -> "قول \"$wake\""
                    },
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                AnimatedVisibility(st.partial.isNotBlank()) {
                    Text(st.partial, color = Neo.Cyan, style = MaterialTheme.typography.bodyMedium)
                }
                AnimatedVisibility(running != null || aiRunning) {
                    Text(
                        if (aiRunning) "الذكاء الاصطناعي شغال… قول \"وقف\" للإلغاء" else "بنفذ: $running",
                        color = Neo.Warning,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (st.running) {
                        PillButton(
                            if (st.paused) "استكمال" else "إيقاف مؤقت",
                            {
                                context.startService(
                                    android.content.Intent(context, ListeningService::class.java)
                                        .setAction(if (st.paused) ListeningService.ACTION_RESUME else ListeningService.ACTION_PAUSE)
                                )
                            },
                            primary = false,
                            icon = if (st.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        )
                        PillButton("إيقاف", { ListeningService.stop(context) }, danger = true, icon = Icons.Filled.Stop)
                    } else {
                        PillButton("ابدأ الاستماع", { startService() }, icon = Icons.Filled.PlayArrow, enabled = model is ModelState.Ready)
                    }
                }
            }

            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Filled.GraphicEq)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("آخر حاجة سمعتها", style = MaterialTheme.typography.bodySmall)
                        Text(heard.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium)
                        if (result.isNotBlank()) Text(result, style = MaterialTheme.typography.bodySmall, color = Neo.Cyan)
                    }
                }
            }

            var typed by remember { mutableStateOf("") }
            GlassCard {
                Text("جرّب أمر بالكتابة", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                NeoTextField(
                    typed, { typed = it }, "مثلاً: افتح يوتيوب • واحد • انزل",
                    trailing = {
                        IconButton(onClick = {
                            val cmd = typed
                            if (cmd.isNotBlank()) scope.launch { c.brain.handle(cmd, grammarMode = false) }
                            typed = ""
                        }) { Icon(Icons.AutoMirrored.Filled.Send, "تنفيذ", tint = Neo.Cyan) }
                    },
                )
            }

            settings?.let { s ->
                GlassCard {
                    SettingSwitch("الرد بالصوت", "يقول \"حاضر\" ويأكد الأوامر", s.ttsEnabled) {
                        scope.launch { c.settings.setTts(it) }
                    }
                    SettingSwitch("وضع الذكاء الاصطناعي", "للأوامر الحرة لما مفيش أمر متسجل يطابق", s.aiEnabled) {
                        scope.launch { c.settings.setAiEnabled(it) }
                    }
                }
            }

            GlassCard {
                Text("جرّب تقول", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "$wake … افتح يوتيوب",
                    "$wake … واحد  (صورتين لكلود + حلهم)",
                    "$wake … انزل / اطلع / رجوع",
                    "$wake … شغّل الكشاف",
                    "$wake … اضغط على بحث",
                ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
            }
            Spacer(Modifier.height(90.dp))
        }
    }
}

@Composable
private fun Warning(icon: ImageVector, title: String, text: String, onClick: () -> Unit) {
    GlassCard(onClick = onClick, glow = Neo.Warning) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, Neo.Warning)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
