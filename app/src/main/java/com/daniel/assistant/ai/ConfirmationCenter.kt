package com.daniel.assistant.ai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CompletableDeferred

/** Bridges the Confirm / Cancel notification actions to the waiting AI agent. */
object ConfirmationCenter {
    const val ACTION_CONFIRM = "com.daniel.assistant.CONFIRM"
    const val ACTION_CANCEL = "com.daniel.assistant.CANCEL"

    @Volatile private var pending: CompletableDeferred<Boolean>? = null

    fun begin(): CompletableDeferred<Boolean> = CompletableDeferred<Boolean>().also { pending = it }

    fun answer(yes: Boolean) {
        pending?.complete(yes)
        pending = null
    }
}

class ConfirmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ConfirmationCenter.ACTION_CONFIRM -> ConfirmationCenter.answer(true)
            ConfirmationCenter.ACTION_CANCEL -> ConfirmationCenter.answer(false)
        }
    }
}
