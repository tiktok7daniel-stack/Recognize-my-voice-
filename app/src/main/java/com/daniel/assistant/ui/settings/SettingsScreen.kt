package com.daniel.assistant.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.daniel.assistant.BuildConfig
import com.daniel.assistant.ai.AiDefaults
import com.daniel.assistant.ai.ProviderKind
import com.daniel.assistant.commands.CustomCommand
import com.daniel.assistant.commands.ExportAlias
import com.daniel.assistant.commands.ImportExport
import com.daniel.assistant.data.CueMode
import com.daniel.assistant.ui.commands.TriggerRecorderDialog
import com.daniel.assistant.ui.components.GlassCard
import com.daniel.assistant.ui.components.IconBubble
import com.daniel.assistant.ui.components.NabraBackground
import com.daniel.assistant.ui.components.NabraLogo
import com.daniel.assistant.ui.components.NeoTextField
import com.daniel.assistant.ui.components.PillButton
import com.daniel.assistant.ui.components.ScreenHeader
import com.daniel.assistant.ui.components.SectionTitle
import com.daniel.assistant.ui.components.SettingSwitch
import com.daniel.assistant.ui.rememberContainer
import com.daniel.assistant.ui.theme.Neo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onOpenModel: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenTutorial: () -> Unit,
) {
    val c = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by c.settings.flow.collectAsState(initial = null)
    val settings = s ?: return

    var wake by remember(settings.wakeWord) { mutableStateOf(settings.wakeWord) }
    var recordWake by remember { mutableStateOf(false) }
    var threshold by remember(settings.threshold) { mutableFloatStateOf(settings.threshold) }
    var aiModel by remember(settings.aiModel) { mutableStateOf(settings.aiModel) }
    var aiBase by remember(settings.aiBaseUrl) { mutableStateOf(settings.aiBaseUrl) }
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    val hasKey = settings.aiKeyEncrypted.isNotBlank()

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    var localCopy by remember { mutableStateOf<String?>(null) }
    var llmUrl by remember { mutableStateOf("") }
    var pickListenButton by remember { mutableStateOf(false) }
    val llmPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            localCopy = "بنسخ الموديل… (ممكن ياخد دقيقة)"
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val dest = com.daniel.assistant.ai.OnDeviceProvider.modelFile(context)
                    dest.parentFile?.mkdirs()
                    val tmp = java.io.File(dest.parentFile, "model.tmp")
                    context.contentResolver.openInputStream(uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 20) } }
                    com.daniel.assistant.ai.OnDeviceProvider.release()
                    dest.delete()
                    tmp.renameTo(dest)
                }.getOrDefault(false)
            }
            localCopy = if (ok) "اتنسخ ✅" else "فشل نسخ الملف"
        }
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val text = ImportExport.encode(
                c.repo.all(),
                c.repo.allAliases().map { ExportAlias(it.packageName, it.appLabel, it.alias) },
            )
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }.isSuccess
            }
            toast(if (ok) "اتصدّر بنجاح" else "فشل التصدير")
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: ""
                    ImportExport.decode(text)
                }
            }
            result.onSuccess { bundle ->
                bundle.commands.forEach { e ->
                    c.repo.save(CustomCommand(name = e.name, enabled = e.enabled, triggers = e.triggers, steps = e.steps, autoTime = e.autoTime, autoApp = e.autoApp, autoButton = e.autoButton))
                }
                val existing = c.repo.allAliases().map { it.packageName to it.alias }.toSet()
                bundle.aliases.filter { (it.packageName to it.alias) !in existing }
                    .forEach { c.repo.addAlias(it.packageName, it.appLabel, it.alias) }
                toast("اتستورد ${bundle.commands.size} أمر و ${bundle.aliases.size} اسم")
            }.onFailure { toast("الملف ده مش صالح") }
        }
    }

    NabraBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader("الإعدادات")
            Column(Modifier.padding(horizontal = 16.dp)) {
                SectionTitle("كلمة التنبيه")
                GlassCard {
                    NeoTextField(wake, { wake = it }, "كلمة التنبيه", trailing = {
                        IconButton(onClick = { recordWake = true }) { Icon(Icons.Filled.Mic, "سجّل", tint = Neo.Cyan) }
                    })
                    Spacer(Modifier.height(8.dp))
                    PillButton("حفظ", {
                        if (wake.isNotBlank()) scope.launch { c.settings.setWakeWord(wake); toast("اتحفظت") }
                    }, enabled = wake.isNotBlank() && wake != settings.wakeWord)
                    Text("قولها وبعدها الأمر، أو الاتنين مع بعض: \"${settings.wakeWord} افتح يوتيوب\"", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }

                SectionTitle("محرك فهم الأوامر")
                GlassCard {
                    Text("بعد كلمة التنبيه، الأمر نفسه بيتفهم بإيه؟", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(settings.commandEngine == "google", { scope.launch { c.settings.setCommandEngine("google") } },
                            { Text("Google — دقة عالية") }, colors = chipColors())
                        FilterChip(settings.commandEngine == "vosk", { scope.launch { c.settings.setCommandEngine("vosk") } },
                            { Text("Vosk — أوفلاين 100%") }, colors = chipColors())
                    }
                    Text(
                        if (c.google.isAvailable()) {
                            "Google بيفهم المصري أدق بكتير وبيدّي كذا احتمال للكلمة. لو النت فاصل بيرجع للأوفلاين لوحده. " +
                                "لو نزّلت حزمة العربي من إعدادات Google بيشتغل أوفلاين كمان."
                        } else "خدمة Google للتعرف على الكلام مش موجودة على الموبايل ده — هيتستخدم Vosk.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
                    )
                    SettingSwitch(
                        "⚡ تنفيذ لحظي",
                        "أوامرك (زي \"واحد\") تتنفذ أول ما الكلمة تتفهم، من غير ما يستنى سكوتك",
                        settings.instantTriggers,
                    ) { v -> scope.launch { c.settings.setInstantTriggers(v) } }
                    if (settings.instantTriggers) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "أسرع حاجة", 150 to "متوازن", 350 to "أضمن").forEach { (ms, label) ->
                                FilterChip(settings.instantDelayMs == ms, { scope.launch { c.settings.setInstantDelay(ms) } },
                                    { Text(label) }, colors = chipColors())
                            }
                        }
                        Text(
                            "\"أسرع حاجة\" بينفّذ فورًا، بس ممكن يشتغل لو الكلمة جت في نص كلامك. \"أضمن\" بيستنى لحظة صغيرة يتأكد إنك خلصت.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    SettingSwitch("أوامري من غير كلمة التنبيه", "قول \"واحد\" على طول من غير \"${settings.wakeWord}\"", settings.directTriggers) { v ->
                        scope.launch { c.settings.setDirectTriggers(v) }
                    }
                    SettingSwitch("الفقاعة العائمة", "زرار مايك صغير فوق كل التطبيقات — دوسة = قول أمر", settings.floatingBubble) { v ->
                        if (v && !android.provider.Settings.canDrawOverlays(context)) {
                            runCatching {
                                context.startActivity(
                                    com.daniel.assistant.ui.onboarding.Permissions.overlayIntent(context)
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                        scope.launch { c.settings.setFloatingBubble(v) }
                    }
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("زرار للاستماع", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                com.daniel.assistant.accessibility.ButtonGesture.byId(settings.listenButton)?.title ?: "مش متحدد — مثلاً ضغطة طويلة على وطّي الصوت",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        androidx.compose.material3.TextButton(onClick = { pickListenButton = true }) { Text("اختار", color = Neo.Cyan) }
                    }
                    Text(
                        "طرق تانية سريعة: اسحب الإشعارات وضيف مربع \"Nabra\" للإعدادات السريعة، أو اضغط مطولًا على أيقونة التطبيق ← \"قول أمر\".",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),
                    )
                }

                SectionTitle("الأمان")
                VoiceLockSection(settings)

                SectionTitle("الصوت والاستماع")
                GlassCard {
                    SettingSwitch("الرد بالصوت (TTS)", "تأكيدات قصيرة زي \"حاضر\"", settings.ttsEnabled) { v -> scope.launch { c.settings.setTts(v) } }
                    Text("تنبيه بداية الاستماع", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CueMode.entries.forEach { m ->
                            FilterChip(settings.cueMode == m, { scope.launch { c.settings.setCue(m) } }, { Text(m.title) }, colors = chipColors())
                        }
                    }
                    SettingSwitch("وقّف الاستماع والشاشة مقفولة", "بيوفر بطارية", settings.pauseOnScreenOff) { v -> scope.launch { c.settings.setPauseOnScreenOff(v) } }
                    SettingSwitch("شغّل مع تشغيل الموبايل", "أندرويد 14+ ممكن يطلب منك تدوس على إشعار", settings.startOnBoot) { v -> scope.launch { c.settings.setStartOnBoot(v) } }
                    SettingSwitch(
                        "وضع الأوامر الدقيق (Grammar)",
                        if (c.models.supportsGrammar()) "الموديل بيدعمه — دقة أعلى وتنبيهات غلط أقل" else "الموديل الحالي مش بيدعمه، هيشتغل كلام حر",
                        settings.useGrammar,
                    ) { v -> scope.launch { c.settings.setUseGrammar(v) } }
                    Text("حساسية مطابقة الأوامر: ${(threshold * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                    Slider(
                        value = threshold,
                        onValueChange = { threshold = it },
                        onValueChangeFinished = { scope.launch { c.settings.setThreshold(threshold) } },
                        valueRange = 0.6f..0.95f,
                        colors = SliderDefaults.colors(thumbColor = Neo.Pill, activeTrackColor = Neo.Accent),
                    )
                }

                SectionTitle("موديل الصوت")
                NavCard(Icons.Filled.CloudDownload, "موديل Vosk العربي", "${c.models.modelSizeMb()} MB • تغيير أو إعادة تحميل", onOpenModel)

                SectionTitle("الذكاء الاصطناعي (اختياري)")
                GlassCard {
                    SettingSwitch("تفعيل وضع الذكاء الاصطناعي", "بيتستخدم بس لو مفيش أمر أوفلاين طابق", settings.aiEnabled) { v -> scope.launch { c.settings.setAiEnabled(v) } }
                    Text("المزوّد", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ProviderKind.entries.forEach { p ->
                            FilterChip(settings.aiProvider == p.id, {
                                scope.launch {
                                    c.settings.setAiProvider(p.id)
                                    when (p) {
                                        ProviderKind.ANTHROPIC -> c.settings.setAiModel(AiDefaults.ANTHROPIC_MODEL)
                                        ProviderKind.OPENAI_COMPATIBLE -> c.settings.setAiModel(AiDefaults.OPENAI_MODEL)
                                        ProviderKind.ON_DEVICE -> Unit
                                    }
                                }
                            }, { Text(p.title) }, colors = chipColors())
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (settings.aiProvider == ProviderKind.ON_DEVICE.id) {
                        val f = com.daniel.assistant.ai.OnDeviceProvider.modelFile(context)
                        Text(
                            if (f.exists()) "✅ الموديل المحلي جاهز (${f.length() / (1024 * 1024)} MB)" else "مفيش موديل محلي لسه",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        localCopy?.let { Text(it, color = Neo.Cyan, style = MaterialTheme.typography.bodySmall) }
                        Spacer(Modifier.height(6.dp))
                        PillButton("اختار ملف الموديل (.task)", {
                            llmPicker.launch(arrayOf("*/*"))
                        }, primary = !f.exists())
                        Spacer(Modifier.height(6.dp))
                        NeoTextField(llmUrl, { llmUrl = it }, "أو حط لينك مباشر لملف .task")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("نزّل من اللينك", {
                                scope.launch {
                                    localCopy = "بنزّل…"
                                    val ok = com.daniel.assistant.ai.OnDeviceProvider.download(context, llmUrl) { done, total ->
                                        localCopy = "بنزّل… ${done / (1024 * 1024)}" + (if (total > 0) " / ${total / (1024 * 1024)} MB" else " MB")
                                    }
                                    localCopy = if (ok) "اتنزل ✅" else "فشل التحميل — جرّب تنزّله من المتصفح وتختاره"
                                }
                            }, enabled = llmUrl.startsWith("http"), primary = false)
                            androidx.compose.material3.TextButton(onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://huggingface.co/litert-community"))
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }) { Text("صفحة الموديلات", color = Neo.Cyan) }
                        }
                        Text(
                            "نزّل موديل بصيغة .task من صفحة LiteRT على Hugging Face (مثلاً Gemma 3 1B أو Qwen 2.5 1.5B — من 0.5 لـ 1.5 جيجا)، " +
                                "واختاره أو حط لينكه. بيشتغل جوه الموبايل من غير نت ومن غير مفتاح ومن غير فلوس. " +
                                "بيفهم كلامك العادي (رسايل، مكالمات، أسئلة) كويس، وأضعف من Claude في المهام الطويلة على الشاشة.",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
                        )
                    } else {
                    NeoTextField(aiModel, { aiModel = it }, "اسم الموديل")
                    if (settings.aiProvider == ProviderKind.OPENAI_COMPATIBLE.id) {
                        Spacer(Modifier.height(8.dp))
                        Text("موديلات مجانية جاهزة:", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Triple("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-oss-120b:free"),
                                Triple("NVIDIA", "https://integrate.api.nvidia.com/v1", "meta/llama-3.3-70b-instruct"),
                                Triple("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
                            ).forEach { (label, url, model) ->
                                FilterChip(aiBase == url, { aiBase = url; aiModel = model }, { Text(label) }, colors = chipColors())
                            }
                        }
                        NeoTextField(aiBase, { aiBase = it }, "Base URL (مثلاً http://127.0.0.1:8080/v1)")
                        Text(
                            "موديل محلي على الموبايل: شغّل llama.cpp server أو Ollama في Termux وحط عنوانه هنا (بيشتغل من غير نت). " +
                                "أو حط أي API متوافق مع OpenAI (OpenRouter, Groq, Gemini…).",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    NeoTextField(
                        apiKey, { apiKey = it },
                        if (hasKey) "مفتاح API (محفوظ ومشفّر — اكتب جديد للتغيير)" else "مفتاح API",
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = Neo.TextDim)
                            }
                        },
                    )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("حفظ", {
                            scope.launch {
                                c.settings.setAiModel(aiModel)
                                c.settings.setAiBaseUrl(aiBase)
                                if (apiKey.isNotBlank()) c.settings.setApiKey(apiKey)
                                apiKey = ""
                                toast("اتحفظ")
                            }
                        })
                        if (hasKey) PillButton("امسح المفتاح", { scope.launch { c.settings.setApiKey("") } }, primary = false)
                    }
                    SettingSwitch("اطلب تأكيد قبل الحاجات الحساسة", "إرسال، شراء، دفع، مسح، تغيير إعدادات", settings.aiConfirmSensitive) { v ->
                        scope.launch { c.settings.setAiConfirm(v) }
                    }
                    Text(
                        "المفتاح بيتشفر بـ Android Keystore. التطبيق مش بيشتغل جوه تطبيقات البنوك أو مديري الباسوردات، وقول \"وقف\" في أي وقت للإلغاء.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                SectionTitle("التحكم عن بعد")
                TelegramSection(settings)

                SectionTitle("البيانات")
                NavCard(Icons.Filled.FileUpload, "تصدير الأوامر", "ملف JSON فيه كل الأوامر والأسماء") { exporter.launch("nabra-commands.json") }
                Spacer(Modifier.height(10.dp))
                NavCard(Icons.Filled.FileDownload, "استيراد أوامر", "من ملف JSON") { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }
                Spacer(Modifier.height(10.dp))
                NavCard(Icons.Filled.History, "السجل", "الأوامر اللي اتنفذت والأخطاء", onOpenLogs)

                SectionTitle("أخرى")
                NavCard(Icons.Filled.School, "الشرح خطوة بخطوة", "إزاي Nabra بيشتغل وإزاي تعمل أول أمر", onOpenTutorial)
                Spacer(Modifier.height(10.dp))
                NavCard(Icons.Filled.Security, "الصلاحيات", "راجع الصلاحيات وإعدادات البطارية", onOpenPermissions)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    NabraLogo(28.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Nabra ${BuildConfig.VERSION_NAME} — للاستخدام الشخصي", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(110.dp))
            }
        }
    }

    if (pickListenButton) {
        com.daniel.assistant.ui.components.GesturePickerDialog(onDismiss = { pickListenButton = false }) { g ->
            scope.launch { c.settings.setListenButton(g?.id ?: "") }
            pickListenButton = false
        }
    }
    if (recordWake) {
        TriggerRecorderDialog(title = "قول كلمة التنبيه", maxTakes = 1, onDismiss = { recordWake = false }) { v ->
            wake = v.first()
            recordWake = false
        }
    }
}

@Composable
private fun NavCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    GlassCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Filled.ChevronLeft, null, tint = Neo.TextDim)
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Neo.Accent,
    selectedLabelColor = Neo.Text,
    labelColor = Neo.TextDim,
)
