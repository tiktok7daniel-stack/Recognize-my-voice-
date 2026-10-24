package com.daniel.assistant.ui.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.daniel.assistant.accessibility.AssistantAccessibilityService

object Permissions {

    val imagesPermission: String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    fun has(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun mic(context: Context) = has(context, Manifest.permission.RECORD_AUDIO)
    fun images(context: Context) = has(context, imagesPermission)
    fun notifications(context: Context) =
        Build.VERSION.SDK_INT < 33 || has(context, Manifest.permission.POST_NOTIFICATIONS)
    fun accessibility(context: Context) = AssistantAccessibilityService.isEnabledInSettings(context)
    fun battery(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
    fun overlay(context: Context) = Settings.canDrawOverlays(context)

    fun allEssential(context: Context) = mic(context) && accessibility(context)

    fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    @SuppressLint("BatteryLife")
    fun batteryIntent(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    fun overlayIntent(context: Context) =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun accessibilityIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun notificationSettings(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** Manufacturer-specific "autostart / protected apps" screens. */
    data class OemInfo(val brand: String, val intents: List<Intent>, val tip: String)

    fun oemInfo(): OemInfo? {
        val m = Build.MANUFACTURER.lowercase()
        fun comp(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> OemInfo(
                "Xiaomi",
                listOf(
                    comp("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                    comp("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
                ),
                "فعّل التشغيل التلقائي (Autostart) واختار \"بدون قيود\" في توفير البطارية.",
            )
            m.contains("samsung") -> OemInfo(
                "Samsung",
                listOf(
                    comp("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                    comp("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                ),
                "من البطارية: شيل نبرة من \"التطبيقات النايمة\" وحطه في \"التطبيقات اللي مش بتنام\".",
            )
            m.contains("huawei") || m.contains("honor") -> OemInfo(
                "Huawei",
                listOf(
                    comp("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    comp("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                ),
                "من تشغيل التطبيقات: خلي نبرة \"إدارة يدوية\" وفعّل كل الاختيارات.",
            )
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> OemInfo(
                Build.MANUFACTURER,
                listOf(comp("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                "فعّل التشغيل التلقائي واسمح بالنشاط في الخلفية.",
            )
            m.contains("vivo") -> OemInfo(
                "Vivo",
                listOf(comp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                "فعّل التشغيل التلقائي في الخلفية.",
            )
            else -> null
        }
    }

    fun dontKillUrl(): String = "https://dontkillmyapp.com/" + Build.MANUFACTURER.lowercase()

    /** Starts the first intent that resolves; falls back to the app details screen. */
    fun launchFirst(context: Context, intents: List<Intent>) {
        for (i in intents) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
            } catch (_: Exception) {
            }
        }
        runCatching { context.startActivity(appDetails(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
