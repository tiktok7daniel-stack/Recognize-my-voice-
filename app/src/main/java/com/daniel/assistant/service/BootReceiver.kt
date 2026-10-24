package com.daniel.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daniel.assistant.NabraApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Restarts listening after reboot when "start on boot" is enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val container = (context.applicationContext as NabraApp).container
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val s = container.settings.current()
                if (s.startOnBoot && container.models.isReady()) {
                    try {
                        ListeningService.start(context)
                    } catch (e: Exception) {
                        container.notifier.bootPrompt()
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
