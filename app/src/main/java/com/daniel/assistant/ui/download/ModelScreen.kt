package com.daniel.assistant.ui.download

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import com.daniel.assistant.ui.components.AnimatedLogo
import com.daniel.assistant.ui.components.GlassCard
import com.daniel.assistant.ui.components.IconBubble
import com.daniel.assistant.ui.components.NabraBackground
import com.daniel.assistant.ui.components.NeoTextField
import com.daniel.assistant.ui.components.PillButton
import com.daniel.assistant.ui.components.ScreenHeader
import com.daniel.assistant.ui.rememberContainer
import com.daniel.assistant.ui.theme.Neo
import com.daniel.assistant.voice.ModelState
import com.daniel.assistant.voice.VoskModels
import kotlinx.coroutines.launch

/** First-launch download of the offline Vosk Arabic model, with progress and graceful failure. */
@Composable
fun ModelScreen(onDone: () -> Unit, onBack: (() -> Unit)? = null) {
    val c = rememberContainer()
    val scope = rememberCoroutineScope()
    val state by c.models.state.collectAsState()
    val settings by c.settings.flow.collectAsState(initial = null)
    var selectedUrl by remember(settings?.modelUrl) { mutableStateOf(settings?.modelUrl ?: VoskModels.DEFAULT.url) }
    var custom by remember { mutableStateOf(false) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) c.models.importZip(uri)
    }

    NabraBackground {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("موديل الصوت", "التعرف على الكلام بيشتغل أوفلاين على جهازك", onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                    AnimatedLogo(96.dp)
                }
                when (val s = state) {
                    is ModelState.Ready -> GlassCard(glow = Neo.Success) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Icons.Filled.CheckCircle, Neo.Success)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("الموديل جاهز", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${c.models.modelSizeMb()} MB • " +
                                        if (c.models.supportsGrammar()) "بيدعم الجرامر (دقة أعلى)" else "وضع الكلام الحر",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    is ModelState.Downloading -> GlassCard(glow = Neo.Accent) {
                        Text("بنزّل الموديل…", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = Neo.Cyan,
                            trackColor = Neo.Bg2,
                        )
                        Spacer(Modifier.height(8.dp))
                        val mb = s.downloaded / (1024 * 1024)
                        val total = if (s.total > 0) " / ${s.total / (1024 * 1024)} MB" else " MB"
                        Text("$mb$total  •  ${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        Text("ممكن تقفل الشاشة دي، ولو النت قطع هيكمل من مكانه.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { c.models.cancel() }) { Text("إلغاء", color = Neo.Danger) }
                    }
                    is ModelState.Unzipping -> GlassCard(glow = Neo.Accent) {
                        Text("بفك الضغط… (${s.files} ملف)", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = Neo.Cyan, trackColor = Neo.Bg2)
                    }
                    is ModelState.Failed -> GlassCard(glow = Neo.Danger) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Icons.Filled.ErrorOutline, Neo.Danger)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("التحميل فشل", style = MaterialTheme.typography.titleMedium)
                                Text(s.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(
                            "جرّب تاني (هيكمل من مكانه)، أو نزّل ملف الـ zip من المتصفح واستورده من زرار \"استيراد ملف\".",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    ModelState.Missing -> Unit
                }

                if (state !is ModelState.Downloading && state !is ModelState.Unzipping) {
                    Text("اختار الموديل", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                    VoskModels.ALL.forEach { m ->
                        GlassCard(
                            modifier = Modifier.selectable(selected = !custom && selectedUrl == m.url) {
                                custom = false; selectedUrl = m.url
                            },
                            glow = if (!custom && selectedUrl == m.url) Neo.Accent else null,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = !custom && selectedUrl == m.url,
                                    onClick = { custom = false; selectedUrl = m.url },
                                    colors = RadioButtonDefaults.colors(selectedColor = Neo.Cyan),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text("${m.title} • ${m.sizeMb} MB", style = MaterialTheme.typography.titleMedium)
                                    Text(m.note, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    GlassCard(glow = if (custom) Neo.Accent else null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = custom,
                                onClick = { custom = true },
                                colors = RadioButtonDefaults.colors(selectedColor = Neo.Cyan),
                            )
                            Text("رابط مخصص", style = MaterialTheme.typography.titleMedium)
                        }
                        if (custom) NeoTextField(selectedUrl, { selectedUrl = it }, "رابط ملف zip")
                    }
                    Text(
                        "مفيش موديل Vosk مصري رسمي؛ موديل MGB2 متدرب على عربي متنوع وبيفهم المصري كويس. " +
                            "دقة الأوامر بتعلى جدًا لأن التطبيق بيقارن بقائمة الأوامر بتاعتك.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (state) {
                    ModelState.Ready -> PillButton("يلا نبدأ", onDone, Modifier.fillMaxWidth())
                    is ModelState.Downloading, is ModelState.Unzipping -> Unit
                    else -> {
                        PillButton(
                            if (state is ModelState.Failed) "إعادة المحاولة" else "نزّل الموديل",
                            {
                                scope.launch { c.settings.setModelUrl(selectedUrl) }
                                c.models.download(selectedUrl)
                            },
                            Modifier.fillMaxWidth(),
                            icon = Icons.Filled.CloudDownload,
                        )
                        PillButton(
                            "استيراد ملف zip",
                            { importer.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                            Modifier.fillMaxWidth(),
                            primary = false,
                            icon = Icons.Filled.FolderOpen,
                        )
                    }
                }
                if (state == ModelState.Ready && onBack != null) {
                    TextButton(onClick = { scope.launch { c.models.delete() } }, modifier = Modifier.fillMaxWidth()) {
                        Text("امسح الموديل ونزّل غيره", color = Neo.Danger)
                    }
                }
            }
        }
    }
}
