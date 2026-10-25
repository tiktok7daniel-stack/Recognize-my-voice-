package com.daniel.assistant.skills

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.daniel.assistant.AppContainer
import com.daniel.assistant.accessibility.NodeActions
import com.daniel.assistant.commands.CommandResolver
import com.daniel.assistant.executor.AppLauncher
import kotlinx.coroutines.delay
import java.net.URLEncoder

/**
 * Executes [SkillIntent]s: messages (WhatsApp deep link / SMS / chat-app UI automation),
 * calls, alarms, timers, search, navigation, volume, brightness and real Wi-Fi/Bluetooth toggles.
 */
class SkillRunner(private val context: Context, private val c: AppContainer) {

    private val contacts = Contacts(context)

    fun describe(i: SkillIntent): String = when (i) {
        is SkillIntent.Message -> "ابعت لـ ${i.contact}" + (i.app?.let { " على ${it.title}" } ?: "") + ": \"${i.text}\""
        is SkillIntent.Call -> "اتصل بـ ${i.contact}"
        is SkillIntent.Alarm -> "منبه الساعة %02d:%02d".format(i.hour, i.minute)
        is SkillIntent.Timer -> "تايمر ${i.seconds / 60} دقيقة" + if (i.seconds % 60 != 0) " و${i.seconds % 60} ثانية" else ""
        is SkillIntent.Search -> "دور على \"${i.query}\""
        is SkillIntent.Navigate -> "وديني ${i.place}"
        is SkillIntent.Volume -> "الصوت ${i.percent}%"
        is SkillIntent.Brightness -> "الإضاءة ${i.percent}%"
        is SkillIntent.Toggle -> (if (i.on) "شغّل " else "اقفل ") + i.what.title
        is SkillIntent.OpenApp -> "افتح ${i.name}"
        is SkillIntent.Chat -> i.answer
        is SkillIntent.Task -> i.goal
    }

    /** Runs the intent as a tracked task (progress panel, Stop, log). */
    fun run(i: SkillIntent) {
        val name = describe(i)
        c.feedback.say("حاضر")
        c.executor.runTask(name, total = 4) { step -> execute(i, step) }
    }

    private suspend fun execute(i: SkillIntent, step: (Int, String) -> Unit): String? = when (i) {
        is SkillIntent.Message -> message(i, step)
        is SkillIntent.Call -> call(i)
        is SkillIntent.Alarm -> start(
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, i.hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, i.minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, i.label.ifBlank { "Nabra" })
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
            "مقدرتش أظبط المنبه",
        )
        is SkillIntent.Timer -> start(
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, i.seconds)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Nabra")
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
            "مقدرتش أعمل التايمر",
        )
        is SkillIntent.Search -> search(i)
        is SkillIntent.Navigate ->
            start(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(i.place))), null)?.let {
                start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(i.place))), "مقدرتش أفتح الخرايط")
            }
        is SkillIntent.Volume -> {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * i.percent / 100f).toInt(), AudioManager.FLAG_SHOW_UI)
            null
        }
        is SkillIntent.Brightness -> brightness(i.percent)
        is SkillIntent.Toggle -> toggle(i, step)
        is SkillIntent.OpenApp -> {
            val app = CommandResolver().findApp(i.name, c.appEntries())?.app
            if (app == null) "مش لاقي تطبيق \"${i.name}\"" else c.executor.openApp(app.packageName, app.label)
        }
        is SkillIntent.Chat, is SkillIntent.Task -> null // handled by the brain
    }

    private fun start(intent: Intent, error: String?): String? =
        if (AppLauncher.start(context, intent)) null else error ?: "فشل"

    // ---------------- messages ----------------

    private fun installed(app: ChatApp): String? = app.packages.firstOrNull { c.appIndex.isInstalled(it) }

    private suspend fun message(i: SkillIntent.Message, step: (Int, String) -> Unit): String? {
        // No app named → WhatsApp if installed, otherwise SMS.
        val app = i.app ?: if (installed(ChatApp.WHATSAPP) != null) ChatApp.WHATSAPP else ChatApp.SMS
        step(1, "بدوّر على ${i.contact}")
        val contact = contacts.find(i.contact)
        return when (app) {
            ChatApp.WHATSAPP -> {
                val pkg = installed(app) ?: return "واتساب مش متثبت"
                if (contact != null) {
                    step(2, "بفتح محادثة ${contact.name}")
                    val url = "https://api.whatsapp.com/send?phone=${contacts.international(contact.number)}&text=" +
                        URLEncoder.encode(i.text, "UTF-8")
                    if (!AppLauncher.start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg))) {
                        return "مقدرتش أفتح واتساب"
                    }
                    step(3, "بدوس إرسال")
                    delay(400)
                    c.executor.pressSendNow()
                } else chatViaUi(pkg, app.title, i.contact, i.text, step)
            }
            ChatApp.SMS -> {
                if (contact == null) return if (contacts.hasPermission()) "مش لاقي \"${i.contact}\" في الأسماء" else "اسمح بصلاحية جهات الاتصال"
                step(2, "بكتب الرسالة لـ ${contact.name}")
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + contact.number)).putExtra("sms_body", i.text)
                if (!AppLauncher.start(context, intent)) return "مقدرتش أفتح الرسايل"
                step(3, "بدوس إرسال")
                delay(500)
                c.executor.pressSendNow()
            }
            else -> {
                val pkg = installed(app) ?: return "${app.title} مش متثبت"
                chatViaUi(pkg, app.title, contact?.name ?: i.contact, i.text, step)
            }
        }
    }

    /** Generic chat-app automation: open → search contact → open chat → type → send. */
    private suspend fun chatViaUi(pkg: String, title: String, name: String, text: String, step: (Int, String) -> Unit): String? {
        if (c.executor.accessibility() == null) return "فعّل خدمة إمكانية الوصول الأول"
        step(1, "بفتح $title")
        c.executor.openApp(pkg, title)?.let { return it }
        delay(700)
        step(2, "بدوّر على $name")
        val search = c.executor.waitForNode(6_000) { NodeActions.findSearch(it.root()) }
            ?: return "مش لاقي البحث في $title"
        c.executor.accessibility()?.let { NodeActions.clickNode(it, search) }
        val box = c.executor.waitForNode(4_000) { NodeActions.findEditableNode(it.root()) } ?: return "مش لاقي خانة البحث"
        NodeActions.setText(box, name)
        delay(1_300)
        val result = c.executor.waitForNode(5_000) { NodeActions.findBestTextMatch(it.root(), name) }
            ?: return "مش لاقي \"$name\" في $title"
        c.executor.accessibility()?.let { NodeActions.clickNode(it, result) }
        delay(900)
        step(3, "بكتب الرسالة")
        c.executor.typeTextNow(text)?.let { return it }
        step(4, "بدوس إرسال")
        return c.executor.pressSendNow()
    }

    // ---------------- calls / search / settings ----------------

    private fun call(i: SkillIntent.Call): String? {
        val contact = contacts.find(i.contact)
            ?: return if (contacts.hasPermission()) "مش لاقي \"${i.contact}\" في الأسماء" else "اسمح بصلاحية جهات الاتصال"
        val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val intent = Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + contact.number))
        c.log.info("اتصال بـ ${contact.name}")
        return start(intent, "مقدرتش أتصل")
    }

    private fun search(i: SkillIntent.Search): String? = when (i.where) {
        SearchTarget.YOUTUBE -> {
            val yt = Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", i.query)
            start(yt, null)?.let {
                start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(i.query))), "مقدرتش أدوّر")
            }
        }
        SearchTarget.MAPS -> start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(i.query))), "مقدرتش أفتح الخرايط")
        SearchTarget.GOOGLE -> start(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, i.query), null)?.let {
            start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(i.query))), "مقدرتش أدوّر")
        }
    }

    private fun brightness(percent: Int): String? {
        if (!Settings.System.canWrite(context)) {
            AppLauncher.start(
                context,
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")),
            )
            return "اسمح لـ Nabra بتعديل إعدادات النظام وجرّب تاني"
        }
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (percent * 255 / 100).coerceIn(1, 255))
        return null
    }

    /** Real on/off: opens the system panel and flips its switch via accessibility. */
    private suspend fun toggle(i: SkillIntent.Toggle, step: (Int, String) -> Unit): String? {
        if (i.what == ToggleTarget.WIFI && Build.VERSION.SDK_INT < 29) {
            @Suppress("DEPRECATION")
            (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager).isWifiEnabled = i.on
            return null
        }
        val svc = c.executor.accessibility() ?: return "فعّل خدمة إمكانية الوصول الأول"
        step(1, "بفتح ${i.what.title}")
        val intent = when (i.what) {
            ToggleTarget.WIFI -> Intent(Settings.Panel.ACTION_WIFI)
            ToggleTarget.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        }
        if (!AppLauncher.start(context, intent)) return "مقدرتش أفتح الإعدادات"
        step(2, "بدوس على المفتاح")
        val sw = c.executor.waitForNode(5_000) { NodeActions.findSwitch(it.root()) } ?: return "مش لاقي مفتاح ${i.what.title}"
        if (sw.isChecked != i.on) NodeActions.clickNode(svc, sw)
        delay(600)
        svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        return null
    }
}
