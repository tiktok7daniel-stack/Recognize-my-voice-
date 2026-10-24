package com.daniel.assistant.ui.home

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
fun HomeScreen(
    onOpenPermissions: () -> Unit,
    onOpenModel: () -> Unit,
    onOpenTutorial: () -> Unit,
    onNewCommand: () -> Unit,
    onOpenCommands: () -> Unit,
) {
    val c = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by ListeningService.state.collectAsState()
    val heard by c.brain.lastHeard.collectAsState()
    val result by c.brain.lastResult.collectAsState()
    val brainListening by c.brain.listening.collectAsState()
    val brainPartial by c.brain.partial.collectAsState()
    val level by c.google.level.collectAsState()
    val running by c.executor.running.collectAsState()
    val aiRunning by c.ai.running.collectAsState()
    val model by c.models.state.collectAsState()
    val settings by c.settings.flow.collectAsState(initial = null)
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    @Suppress("UNUSED_VARIABLE") val t = tick
    val micOk = Permissions.mic(context)
    val a11yOk = Permissions.accessibility(context)
    val modelOk = model is ModelState.Ready
    val googleEngine = settings?.commandEngine == "google" && c.google.isAvailable()

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        tick++
        if (granted) pendingAction?.invoke()
        pendingAction = null
    }
    fun withMic(action: () -> Unit) {
        if (micOk) action() else {
            pendingAction = action
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val orb = when {
        brainListening || st.awaitingCommand -> OrbState.AWAITING
        !st.running -> OrbState.OFF
        st.paused -> OrbState.PAUSED
        else -> OrbState.IDLE
    }
    val wake = settings?.wakeWord ?: "يا مساعد"
    val partial = brainPartial.ifBlank { st.partial }

    NabraBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---------- header ----------
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                NabraLogo(44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Nabra", style = MaterialTheme.typography.headlineMedium)
                    Text("مساعدك الصوتي الشخصي", style = MaterialTheme.typography.bodySmall)
                }
                EnginePill(
                    if (googleEngine) "دقة عالية" else "أوفلاين",
                    if (googleEngine) Neo.Cyan else Neo.Success,
                )
            }

            // ---------- status chips ----------
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusDot("الميكروفون", micOk, onOpenPermissions)
                StatusDot("إمكانية الوصول", a11yOk, onOpenPermissions)
                StatusDot("موديل الصوت", modelOk, onOpenModel)
                StatusDot(if (st.running) "شغال" else "واقف", st.running) {}
            }

            if (!modelOk) {
                Warning(Icons.Filled.CloudDownload, "موديل الصوت مش متنزل", "نزّله عشان كلمة التنبيه تشتغل", onOpenModel)
            }
            if (!micOk || !a11yOk) {
                Warning(
                    if (!a11yOk) Icons.Filled.Accessibility else Icons.Filled.Warning,
                    if (!a11yOk) "خدمة إمكانية الوصول مقفولة" else "صلاحية الميكروفون ناقصة",
                    if (!a11yOk) "من غيرها مش هقدر أكتب أو أدوس — اضغط وكمّل" else "اضغط هنا وكمّل الصلاحيات",
                    onOpenPermissions,
                )
            }

            // ---------- hero ----------
            GlassCard(glow = if (orb == OrbState.AWAITING) Neo.Cyan else null) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    GlowOrb(orb, size = 210.dp, level = level) {
                        if (st.running) ListeningService.stop(context) else withMic { ListeningService.start(context) }
                    }
                    Text(
                        when (orb) {
                            OrbState.OFF -> "المساعد واقف"
                            OrbState.PAUSED -> "متوقف مؤقتًا"
                            OrbState.AWAITING -> "بسمعك… قول الأمر"
                            OrbState.IDLE -> "قول \"$wake\""
                        },
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    AnimatedVisibility(partial.isNotBlank()) {
                        Text(partial, color = Neo.Cyan, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    }
                    AnimatedVisibility(running != null || aiRunning) {
                        Text(
                            if (aiRunning) "الذكاء الاصطناعي شغال… قول \"وقف\" للإلغاء" else "بنفذ: $running",
                            color = Neo.Warning,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    PillButton(
                        "قول أمر دلوقتي",
                        { withMic { ListeningService.listenNow(context) } },
                        Modifier.fillMaxWidth(),
                        icon = Icons.Filled.Mic,
                        enabled = modelOk || googleEngine,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (st.running) {
                            PillButton(
                                if (st.paused) "استكمال" else "إيقاف مؤقت",
                                {
                                    context.startService(
                                        Intent(context, ListeningService::class.java)
                                            .setAction(if (st.paused) ListeningService.ACTION_RESUME else ListeningService.ACTION_PAUSE)
                                    )
                                },
                                primary = false,
                                icon = if (st.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            )
                            PillButton("إيقاف", { ListeningService.stop(context) }, danger = true, icon = Icons.Filled.Stop)
                        } else {
                            PillButton(
                                "شغّل كلمة التنبيه",
                                { withMic { ListeningService.start(context) } },
                                primary = false,
                                icon = Icons.Filled.PlayArrow,
                                enabled = modelOk,
                            )
                        }
                    }
                }
            }

            // ---------- quick actions ----------
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAction(Icons.Filled.Add, "أمر جديد", Neo.Accent, Modifier.weight(1f), onNewCommand)
                QuickAction(Icons.Filled.RecordVoiceOver, "أوامري", Neo.Cyan, Modifier.weight(1f), onOpenCommands)
                QuickAction(Icons.Filled.School, "الشرح", Neo.Success, Modifier.weight(1f), onOpenTutorial)
            }

            // ---------- last heard ----------
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

            // ---------- typed test ----------
            var typed by remember { mutableStateOf("") }
            GlassCard {
                Text("جرّب أمر بالكتابة", style = MaterialTheme.typography.titleMedium)
                Text("بيتنفذ بالظبط زي ما تقوله بصوتك", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                NeoTextField(
                    typed, { typed = it }, "مثلاً: واحد • افتح يوتيوب • انزل",
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
                    SettingSwitch("أوامري من غير كلمة التنبيه", "قول \"واحد\" على طول من غير \"$wake\"", s.directTriggers) {
                        scope.launch { c.settings.setDirectTriggers(it) }
                    }
                    SettingSwitch("الفقاعة العائمة", "زرار مايك فوق كل التطبيقات", s.floatingBubble) { v ->
                        if (v && !Permissions.overlay(context)) {
                            runCatching { context.startActivity(Permissions.overlayIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                        scope.launch { c.settings.setFloatingBubble(v) }
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun EnginePill(text: String, color: Color) {
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).border(1.dp, color.copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun StatusDot(label: String, ok: Boolean, onClick: () -> Unit) {
    val color = if (ok) Neo.Success else Neo.Warning
    Row(
        Modifier.clip(CircleShape).background(Neo.Glass).border(1.dp, Neo.Stroke, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Neo.Text)
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Neo.Glass)
            .border(1.dp, Neo.Stroke, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBubble(icon, tint, 46)
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Neo.Text)
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
