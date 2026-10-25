package com.daniel.assistant.service

import android.content.Context
import android.os.SystemClock
import com.daniel.assistant.NabraApp
import com.daniel.assistant.ui.onboarding.Permissions
import kotlinx.coroutines.launch

/**
 * Keeps the assistant always on: starts listening when the app opens, when the
 * accessibility service connects (also after reboot), and re-starts it if the system
 * killed it — unless the user explicitly pressed Stop.
 */
object AutoStarter {
    private var lastCheck = 0L

    /** @param fromAppOpen opening the app always starts it (and clears a manual stop). */
    fun tryStart(context: Context, fromAppOpen: Boolean = false) {
        val c = (context.applicationContext as? NabraApp)?.container ?: return
        c.scope.launch {
            val s = c.settings.current()
            if (!s.autoStart) return@launch
            if (fromAppOpen && s.manualStop) c.settings.setManualStop(false)
            else if (s.manualStop) return@launch
            if (ListeningService.state.value.running) return@launch
            if (!Permissions.mic(context) || !c.models.isReady()) return@launch
            try {
                ListeningService.start(context.applicationContext)
            } catch (e: Exception) {
                c.log.info("التشغيل التلقائي اتمنع من النظام: ${e.message}")
            }
        }
    }

    /** Cheap watchdog called from frequent events; checks at most every 30 s. */
    fun watchdog(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCheck < 30_000) return
        lastCheck = now
        if (!ListeningService.state.value.running) tryStart(context)
    }
}
