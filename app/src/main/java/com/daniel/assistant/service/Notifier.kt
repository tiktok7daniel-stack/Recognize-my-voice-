package com.daniel.assistant.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.daniel.assistant.R
import com.daniel.assistant.ui.MainActivity

/** Notification channels and all user-facing notifications. */
class Notifier(private val context: Context) {

    companion object {
        const val CH_LISTEN = "listening"
        const val CH_ALERTS = "alerts"
        const val ID_SERVICE = 1
        const val ID_NOT_UNDERSTOOD = 2
        const val ID_FAILED = 3
        const val ID_ACCESSIBILITY = 4
        const val ID_CONFIRM = 5
        const val ID_BOOT = 6
        const val ID_INFO = 7
    }

    private val nm = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val m = context.getSystemService(NotificationManager::class.java)
        m.createNotificationChannel(
            NotificationChannel(CH_LISTEN, "المساعد شغال", NotificationManager.IMPORTANCE_LOW).apply {
                description = "إشعار دائم أثناء الاستماع"
                setShowBadge(false)
            }
        )
        m.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "تنبيهات المساعد", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "أخطاء، تأكيدات، وأوامر مش مفهومة"
            }
        )
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun base(channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(0xFF3D7BFF.toInt())
        .setContentIntent(openApp())

    private fun post(id: Int, b: NotificationCompat.Builder) {
        if (!canPost()) return
        try {
            nm.notify(id, b.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancel(id: Int) = nm.cancel(id)

    fun notUnderstood(heard: String) = post(
        ID_NOT_UNDERSTOOD,
        base(CH_ALERTS).setContentTitle("مفهمتش")
            .setContentText(if (heard.isBlank()) "مسمعتش حاجة واضحة" else "سمعت: \"$heard\"")
            .setAutoCancel(true)
            .setTimeoutAfter(15_000),
    )

    fun stepFailed(command: String, stepIndex: Int, step: String, reason: String) = post(
        ID_FAILED,
        base(CH_ALERTS).setContentTitle("فشل الأمر \"$command\"")
            .setContentText("الخطوة ${stepIndex + 1}: $step")
            .setStyle(NotificationCompat.BigTextStyle().bigText("الخطوة ${stepIndex + 1}: $step\nالسبب: $reason"))
            .setAutoCancel(true),
    )

    fun info(title: String, text: String) = post(
        ID_INFO, base(CH_ALERTS).setContentTitle(title).setContentText(text).setAutoCancel(true).setTimeoutAfter(20_000),
    )

    fun accessibilityDisabled() {
        val pi = PendingIntent.getActivity(
            context, 10,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        post(
            ID_ACCESSIBILITY,
            base(CH_ALERTS).setContentTitle("خدمة إمكانية الوصول مقفولة")
                .setContentText("فعّل \"Nabra\" في إمكانية الوصول عشان أقدر أتحكم في الشاشة")
                .setContentIntent(pi)
                .setAutoCancel(true),
        )
    }

    fun confirm(question: String, confirm: PendingIntent, cancel: PendingIntent) = post(
        ID_CONFIRM,
        base(CH_ALERTS).setContentTitle("تأكيد مطلوب")
            .setContentText(question)
            .setStyle(NotificationCompat.BigTextStyle().bigText(question))
            .addAction(0, "أكّد", confirm)
            .addAction(0, "إلغاء", cancel)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH),
    )

    fun bootPrompt() = post(
        ID_BOOT,
        base(CH_ALERTS).setContentTitle("Nabra")
            .setContentText("اضغط هنا لتشغيل المساعد الصوتي")
            .setAutoCancel(true),
    )
}
