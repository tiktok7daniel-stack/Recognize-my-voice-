package com.daniel.assistant.executor

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.daniel.assistant.accessibility.AssistantAccessibilityService
import com.daniel.assistant.accessibility.NodeActions
import com.daniel.assistant.commands.AppEntry
import com.daniel.assistant.commands.CommandResolver
import com.daniel.assistant.commands.CustomCommand
import com.daniel.assistant.commands.Step
import com.daniel.assistant.commands.SysAction
import com.daniel.assistant.commands.withVars
import com.daniel.assistant.data.AppIndex
import com.daniel.assistant.data.ExecLog
import com.daniel.assistant.service.Notifier
import com.daniel.assistant.voice.Feedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Runs command chains step by step. Every UI step polls the accessibility
 * tree for its target (default timeout 5 s) instead of sleeping blindly.
 */
class CommandExecutor(
    private val context: Context,
    private val appIndex: AppIndex,
    private val feedback: Feedback,
    private val notifier: Notifier,
    private val log: ExecLog,
    private val scope: CoroutineScope,
    private val appsProvider: suspend () -> List<AppEntry>,
) {
    private var job: Job? = null
    private val _running = MutableStateFlow<String?>(null)
    /** Name of the command currently running, or null. */
    val running: StateFlow<String?> = _running.asStateFlow()

    data class Progress(val command: String, val index: Int, val total: Int, val step: String)
    private val _progress = MutableStateFlow<Progress?>(null)
    /** Current step of the running command (for the on-screen execution panel). */
    val progress: StateFlow<Progress?> = _progress.asStateFlow()

    var stepTimeoutMs = 5_000L

    fun run(command: CustomCommand, vars: Map<String, String> = emptyMap(), announce: Boolean = true): Job {
        cancel()
        return scope.launch {
            if (announce) feedback.say("حاضر")
            if (vars.isNotEmpty()) log.info("متغيرات: " + vars.entries.joinToString { "${it.key}=${it.value}" })
            execute(command.name, command.steps.map { it.withVars(vars) })
        }.also { job = it }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _running.value = null
        _progress.value = null
    }

    /** Runs the steps; returns true on success. */
    suspend fun execute(name: String, steps: List<Step>): Boolean {
        _running.value = name
        log.info("تشغيل \"$name\" (${steps.size} خطوات)")
        try {
            steps.forEachIndexed { i, step ->
                _progress.value = Progress(name, i + 1, steps.size, step.describe())
                val error = if (step.needsAccessibility && service() == null) {
                    notifier.accessibilityDisabled()
                    "خدمة إمكانية الوصول مقفولة — فعّلها من الإعدادات"
                } else runStep(step)
                if (error != null) {
                    feedback.errorCue()
                    feedback.say("حصلت مشكلة في خطوة ${i + 1}")
                    notifier.stepFailed(name, i, step.describe(), error)
                    log.error("\"$name\" فشل في خطوة ${i + 1} (${step.describe()}): $error")
                    return false
                }
            }
            log.ok("\"$name\" خلص بنجاح")
            return true
        } finally {
            _running.value = null
            _progress.value = null
        }
    }

    suspend fun runStep(step: Step): String? = when (step) {
        is Step.OpenApp -> openApp(step.packageName, step.label)
        is Step.AttachPhotos -> attachPhotos(step)
        is Step.TypeText -> typeText(step.text)
        Step.PressSend -> pressSend()
        is Step.ClickText -> clickText(step.text)
        is Step.Wait -> {
            delay(step.millis.coerceIn(0, 60_000)); null
        }
        Step.Back -> global(AccessibilityService.GLOBAL_ACTION_BACK)
        Step.Home -> global(AccessibilityService.GLOBAL_ACTION_HOME)
        is Step.Scroll -> service()?.let { if (NodeActions.scroll(it, step.down)) null else "مفيش حاجة تتعمل لها سكرول" }
            ?: NO_ACCESSIBILITY
        is Step.System -> SysAction.byId(step.actionId)?.let { runSystem(it, step.arg) } ?: "أمر غير معروف"
        is Step.OpenUrl -> openUrl(step.url, step.packageName)
        is Step.AttachInChat -> attachInChat(step.count)
    }

    // ---------- building blocks used by skills ----------

    suspend fun <T> waitForNode(timeoutMs: Long = stepTimeoutMs, block: (AssistantAccessibilityService) -> T?): T? =
        waitFor(timeoutMs, block)

    suspend fun typeTextNow(text: String): String? = typeText(text)
    suspend fun pressSendNow(): String? = pressSend()
    fun accessibility(): AssistantAccessibilityService? = service()

    /** Runs a multi-step task (e.g. a skill) with the progress panel, cancellation and logging. */
    fun runTask(name: String, total: Int, block: suspend (step: (Int, String) -> Unit) -> String?): Job {
        cancel()
        return scope.launch {
            _running.value = name
            log.info("تشغيل: $name")
            try {
                val err = block { i, d -> _progress.value = Progress(name, i, total, d) }
                if (err != null) {
                    feedback.errorCue()
                    feedback.say(err)
                    notifier.stepFailed(name, 0, name, err)
                    log.error("$name: $err")
                } else log.ok("$name ✅")
            } finally {
                _running.value = null
                _progress.value = null
            }
        }.also { job = it }
    }

    // ---------- system commands ----------

    suspend fun runSystem(action: SysAction, arg: String?): String? {
        if (Build.VERSION.SDK_INT < action.minSdk) return "الأمر ده محتاج أندرويد أحدث"
        return when (action) {
            SysAction.BACK -> global(AccessibilityService.GLOBAL_ACTION_BACK)
            SysAction.HOME -> global(AccessibilityService.GLOBAL_ACTION_HOME)
            SysAction.RECENTS -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
            SysAction.NOTIFICATIONS -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            SysAction.QUICK_SETTINGS -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            SysAction.SCREENSHOT -> if (Build.VERSION.SDK_INT >= 28) global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) else null
            SysAction.LOCK -> if (Build.VERSION.SDK_INT >= 28) global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) else null
            SysAction.SCROLL_DOWN -> service()?.let { if (NodeActions.scroll(it, true)) null else "مفيش سكرول" } ?: NO_ACCESSIBILITY
            SysAction.SCROLL_UP -> service()?.let { if (NodeActions.scroll(it, false)) null else "مفيش سكرول" } ?: NO_ACCESSIBILITY
            SysAction.VOLUME_UP -> volume(AudioManager.ADJUST_RAISE)
            SysAction.VOLUME_DOWN -> volume(AudioManager.ADJUST_LOWER)
            SysAction.TORCH_ON -> torch(true)
            SysAction.TORCH_OFF -> torch(false)
            SysAction.WIFI -> {
                val i = if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS)
                if (AppLauncher.start(context, i)) null else "مقدرتش أفتح إعدادات الواي فاي"
            }
            SysAction.BLUETOOTH -> {
                if (AppLauncher.start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) null else "مقدرتش أفتح البلوتوث"
            }
            SysAction.PLAY_PAUSE -> media(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            SysAction.NEXT_TRACK -> media(android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
            SysAction.PREV_TRACK -> media(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            SysAction.POWER_MENU -> global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            SysAction.SPLIT_SCREEN -> global(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            SysAction.SWIPE_LEFT -> service()?.let { if (NodeActions.swipeHorizontal(it, toLeft = true)) null else "مقدرتش أسحب" } ?: NO_ACCESSIBILITY
            SysAction.SWIPE_RIGHT -> service()?.let { if (NodeActions.swipeHorizontal(it, toLeft = false)) null else "مقدرتش أسحب" } ?: NO_ACCESSIBILITY
            SysAction.CLICK -> if (arg.isNullOrBlank()) "مفيش نص أضغط عليه" else clickText(arg)
            SysAction.TYPE -> if (arg.isNullOrBlank()) "مفيش نص أكتبه" else typeText(arg)
        }
    }

    // ---------- step helpers ----------

    private fun service(): AssistantAccessibilityService? = AssistantAccessibilityService.get()

    private fun global(action: Int): String? {
        val s = service() ?: return NO_ACCESSIBILITY.also { notifier.accessibilityDisabled() }
        return if (s.performGlobalAction(action)) null else "النظام رفض الأمر"
    }

    private suspend fun <T> waitFor(timeoutMs: Long = stepTimeoutMs, block: (AssistantAccessibilityService) -> T?): T? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            val s = service() ?: return null
            runCatching { block(s) }.getOrNull()?.let { return it }
            if (SystemClock.uptimeMillis() >= end) return null
            delay(80)
        }
    }

    private suspend fun resolvePackage(pkg: String, label: String): String? {
        if (pkg.isNotBlank() && appIndex.isInstalled(pkg)) return pkg
        appIndex.ensureLoaded()
        val apps = appsProvider()
        return CommandResolver().findApp(label.ifBlank { pkg }, apps)?.app?.packageName
    }

    private suspend fun waitForForeground(pkg: String) {
        if (service() == null) {
            delay(1200); return
        }
        waitFor(stepTimeoutMs) { s -> if (s.currentPackage() == pkg) true else null }
    }

    suspend fun openApp(pkg: String, label: String): String? {
        val target = resolvePackage(pkg, label) ?: return "التطبيق \"$label\" مش متثبت"
        if (!AppLauncher.launchPackage(context, target)) return "مقدرتش أفتح \"$label\""
        waitForForeground(target)
        return null
    }

    /** Opens a link — inside the given/auto-detected app when possible (e.g. a Claude chat). */
    suspend fun openUrl(raw: String, packageName: String?): String? {
        val url = raw.trim().let { if (it.contains("://") || it.startsWith("tel:")) it else "https://$it" }
        val uri = android.net.Uri.parse(url)
        val pkg = (packageName ?: com.daniel.assistant.commands.ChatLinks.packageFor(url))?.takeIf { appIndex.isInstalled(it) }
        if (pkg != null) {
            val inApp = Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addCategory(Intent.CATEGORY_BROWSABLE)
            if (AppLauncher.start(context, inApp)) {
                waitForForeground(pkg)
                return null
            }
        }
        return if (AppLauncher.start(context, Intent(Intent.ACTION_VIEW, uri))) null else "مقدرتش أفتح اللينك"
    }

    private val attachPrefs by lazy { context.getSharedPreferences("attach_mode", Context.MODE_PRIVATE) }

    /**
     * Attaches the newest [count] photos inside the open chat.
     * Fast path: put the photos on the clipboard and paste them into the message box
     * (instant, like pasting an image in WhatsApp). The result is remembered per app; apps
     * that don't accept pasted images use the (optimized) + → Photos → pick → confirm path.
     */
    private suspend fun attachInChat(count: Int): String? {
        val s = service() ?: return NO_ACCESSIBILITY
        val pkg = s.currentPackage() ?: ""
        val mode = attachPrefs.getString(pkg, null)
        if (mode != "picker") {
            val t0 = SystemClock.uptimeMillis()
            if (pastePhotos(count)) {
                attachPrefs.edit().putString(pkg, "paste").apply()
                log.info("الصور اتلزقت في ${SystemClock.uptimeMillis() - t0}ms")
                return null
            }
            if (mode == null) attachPrefs.edit().putString(pkg, "picker").apply()
        }
        return attachViaPicker(count)
    }

    /** Clipboard paste of the photos into the focused message box. True when the UI changed. */
    private suspend fun pastePhotos(count: Int): Boolean {
        val uris = withContext(Dispatchers.IO) { PhotoSharer.copyLatestToCache(context, count.coerceIn(1, 10)) }
        if (uris.isEmpty()) return false
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newUri(context.contentResolver, "photos", uris.first())
        uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
        cm.setPrimaryClip(clip)
        val edit = waitFor(4_000) { NodeActions.findEditableNode(it.root()) } ?: return false
        val before = NodeActions.visibleNodes(service()?.root()).size
        edit.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        if (!edit.performAction(AccessibilityNodeInfo.ACTION_PASTE)) return false
        // Success = new attachment previews appear; failure = nothing (or a "content://" text).
        val changed = waitFor(1_500) { sv ->
            val root = sv.root()
            val box = NodeActions.findEditableNode(root)
            if (box?.text?.contains("content://") == true) false
            else if (NodeActions.visibleNodes(root).size > before) true else null
        } ?: false
        if (!changed) NodeActions.findEditableNode(service()?.root())?.let { box ->
            if (box.text?.contains("content://") == true) NodeActions.setText(box, "")
        }
        return changed
    }

    private val PHOTO_OPTION = listOf("photos", "photo library", "gallery", "images", "library", "choose photo", "upload photo",
        "الصور", "صور", "المعرض", "الاستوديو", "foto", "galleria", "immagini")
    private val CONFIRM = listOf("add", "done", "select", "upload", "attach", "ok", "send", "اضافه", "اضف", "تم", "اختيار",
        "موافق", "ارفاق", "aggiungi", "fine", "seleziona", "carica", "allega")

    /** + → (Photos) → newest N → confirm, driven by polling instead of fixed pauses. */
    private suspend fun attachViaPicker(count: Int): String? {
        val s = service() ?: return NO_ACCESSIBILITY
        val width = s.resources.displayMetrics.widthPixels
        val attach = waitFor(5_000) { NodeActions.findAttachButton(it.root()) } ?: return "مش لاقي زرار الإرفاق (+) في الشات"
        NodeActions.clickNode(s, attach)
        // Recent photos may already be in the sheet; otherwise open "Photos".
        var thumbs = waitFor(1_200) { NodeActions.findThumbnails(it.root(), width).takeIf { l -> l.isNotEmpty() } }
        if (thumbs == null) {
            waitFor(1_500) { NodeActions.findByWords(it.root(), PHOTO_OPTION, exclude = listOf("camera", "كاميرا")) }
                ?.let { NodeActions.clickNode(s, it) }
            thumbs = waitFor(5_000) { NodeActions.findThumbnails(it.root(), width).takeIf { l -> l.isNotEmpty() } }
                ?: return "مش لاقي الصور في القايمة"
        }
        thumbs.take(count.coerceIn(1, 20)).forEach { t ->
            NodeActions.clickNode(s, t)
            delay(120)
        }
        // Multi-select pickers need a confirm button; single-pick ones return by themselves.
        waitFor(1_500) { NodeActions.findByWords(it.root(), CONFIRM, exclude = listOf("cancel", "الغاء", "annulla")) }
            ?.let { NodeActions.clickNode(s, it) }
        // Back in the chat when the message box is visible again.
        waitFor(3_000) { NodeActions.findEditableNode(it.root()) }
        return null
    }

    private suspend fun attachPhotos(step: Step.AttachPhotos): String? {
        if (!step.chatUrl.isNullOrBlank()) {
            // Existing chat: open its link inside the app, then attach from inside the chat.
            openUrl(step.chatUrl, step.packageName.ifBlank { null })?.let { return it }
            return attachInChat(step.count)
        }
        val target = resolvePackage(step.packageName, step.label) ?: return "التطبيق \"${step.label}\" مش متثبت"
        PhotoSharer.share(context, target, step.count.coerceIn(1, 20), step.text)?.let { return it }
        waitForForeground(target)
        return null
    }

    private suspend fun typeText(text: String): String? {
        val node = waitFor { NodeActions.findEditableNode(it.root()) } ?: return "مش لاقي خانة كتابة"
        if (NodeActions.setText(node, text)) return null
        // Some apps ignore ACTION_SET_TEXT: tap the field and paste instead.
        service()?.let { NodeActions.clickNode(it, node) }
        delay(250)
        val fresh = NodeActions.findEditableNode(service()?.root()) ?: node
        return if (NodeActions.pasteText(context, fresh, text)) null else "مقدرتش أكتب في الخانة"
    }

    private suspend fun pressSend(): String? {
        val s = service() ?: return NO_ACCESSIBILITY
        // Wait until the send button is enabled (e.g. photos finished attaching), then tap.
        val enabled = waitFor(10_000) { NodeActions.findSendButton(it.root())?.takeIf { n -> n.isEnabled } }
        val node = enabled ?: NodeActions.findSendButton(s.root())
        if (node != null && NodeActions.clickNode(s, node)) return null
        // Fallback: the keyboard's "send/enter" action on the text field.
        val edit = NodeActions.findEditableNode(s.root())
        if (edit != null && NodeActions.imeEnter(edit)) return null
        return if (node == null) "مش لاقي زرار الإرسال" else "مقدرتش أدوس إرسال"
    }

    private suspend fun clickText(text: String): String? {
        var node = waitFor { NodeActions.findNodeByText(it.root(), text) }
        // Recovery: the target may be further down the list — scroll and look again.
        var tries = 0
        while (node == null && tries < 2) {
            val sv = service() ?: return NO_ACCESSIBILITY
            NodeActions.scroll(sv, down = true)
            delay(450)
            node = waitFor(1_500) { NodeActions.findNodeByText(it.root(), text) }
            tries++
        }
        if (node == null) return "مش لاقي \"$text\" على الشاشة"
        val s = service() ?: return NO_ACCESSIBILITY
        return if (NodeActions.clickNode(s, node)) null else "مقدرتش أضغط على \"$text\""
    }

    private fun media(keyCode: Int): String? {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
        return null
    }

    private fun volume(direction: Int): String? {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return null
    }

    private fun torch(on: Boolean): String? = try {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return "مفيش فلاش"
        cm.setTorchMode(id, on)
        null
    } catch (e: Exception) {
        "مقدرتش أشغل الكشاف"
    }

    companion object {
        const val NO_ACCESSIBILITY = "خدمة إمكانية الوصول مقفولة"
    }
}
