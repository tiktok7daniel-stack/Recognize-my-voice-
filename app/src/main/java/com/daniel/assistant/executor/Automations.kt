package com.daniel.assistant.executor

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.daniel.assistant.NabraApp
import com.daniel.assistant.commands.CustomCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Runs commands automatically: every day at a time (AlarmManager) or when a chosen app
 * comes to the foreground (reported by the accessibility service).
 */
class Automations(private val context: Context) {

    private val prefs = context.getSharedPreferences("automations", Context.MODE_PRIVATE)
    private val am = context.getSystemService(AlarmManager::class.java)
    private val lastAppRun = mutableMapOf<Long, Long>()

    /** (Re)schedules daily alarms for all commands with a time; cancels removed ones. */
    fun schedule(commands: List<CustomCommand>) {
        val wanted = commands.filter { it.enabled && !it.autoTime.isNullOrBlank() }
        val old = prefs.getStringSet("ids", emptySet()).orEmpty()
        old.forEach { id -> am.cancel(pending(id.toLong())) }
        wanted.forEach { cmd -> nextTrigger(cmd.autoTime!!)?.let { at -> setAlarm(cmd.id, at) } }
        prefs.edit().putStringSet("ids", wanted.map { it.id.toString() }.toSet()).apply()
    }

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    private fun setAlarm(id: Long, at: Long) {
        val pi = pending(id)
        if (canScheduleExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun pending(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, (id % Int.MAX_VALUE).toInt(),
        Intent(context, AutoRunReceiver::class.java).putExtra(AutoRunReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Called when an app comes to the foreground. Returns the command to run, if any. */
    fun commandForApp(pkg: String, commands: List<CustomCommand>): CustomCommand? {
        val now = SystemClock.elapsedRealtime()
        val cmd = commands.firstOrNull { it.enabled && it.autoApp == pkg } ?: return null
        val last = lastAppRun[cmd.id] ?: 0L
        if (now - last < 60_000) return null // at most once a minute, avoids loops
        lastAppRun[cmd.id] = now
        return cmd
    }

    companion object {
        /** Next occurrence of "HH:mm" in epoch millis, or null if invalid. */
        fun nextTrigger(hhmm: String, now: Calendar = Calendar.getInstance()): Long? {
            val parts = hhmm.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
            val m = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
            val c = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            if (c.timeInMillis <= now.timeInMillis) c.add(Calendar.DAY_OF_YEAR, 1)
            return c.timeInMillis
        }
    }
}

class AutoRunReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val pending = goAsync()
        val c = (context.applicationContext as NabraApp).container
        CoroutineScope(Dispatchers.Main).launch {
            try {
                c.repo.get(id)?.takeIf { it.enabled }?.let { cmd ->
                    c.log.info("تشغيل تلقائي: \"${cmd.name}\" (${cmd.autoTime})")
                    c.executor.run(cmd, announce = false)
                }
                c.automations.schedule(c.repo.all()) // book tomorrow
            } finally {
                pending.finish()
            }
        }
    }

    companion object { const val EXTRA_ID = "command_id" }
}
