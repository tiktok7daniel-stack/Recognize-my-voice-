package com.daniel.assistant.executor

import android.content.Context
import android.content.Intent
import com.daniel.assistant.accessibility.AssistantAccessibilityService

/** Starts activities, preferring the accessibility-service context (exempt from background-start limits). */
object AppLauncher {

    fun start(context: Context, intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        AssistantAccessibilityService.get()?.let { if (it.launch(intent)) return true }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun launchPackage(context: Context, pkg: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return start(context, intent)
    }
}
