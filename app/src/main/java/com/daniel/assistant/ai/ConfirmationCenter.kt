package com.daniel.assistant.ai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

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

/** Saves the last successful AI task as a custom command (skill memory). */
class SaveRecipeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = (context.applicationContext as com.daniel.assistant.NabraApp).container
        val recipe = c.ai.lastRecipe ?: return
        val pending = goAsync()
        c.scope.launch {
            try {
                c.repo.save(
                    com.daniel.assistant.commands.CustomCommand(
                        name = recipe.command.take(40),
                        triggers = listOf(recipe.command),
                        steps = recipe.steps,
                    )
                )
                c.notifier.cancel(com.daniel.assistant.service.Notifier.ID_INFO)
                c.feedback.say("اتحفظ كأمر")
                c.log.ok("اتحفظ كأمر: \"${recipe.command}\"")
            } finally {
                pending.finish()
            }
        }
    }
}
