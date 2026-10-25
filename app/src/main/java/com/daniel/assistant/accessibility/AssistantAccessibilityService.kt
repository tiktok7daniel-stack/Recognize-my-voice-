package com.daniel.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "The hands": reads the screen and performs clicks, typing, scrolling and
 * global actions. Exposed through [instance] so the executor can use it.
 */
class AssistantAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        _instance.value = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        lastEventAt = SystemClock.uptimeMillis()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { pkg ->
                // Ignore our own overlays and the system UI shade.
                if (pkg != "com.android.systemui" && pkg != packageName && pkg != foregroundPackage) {
                    foregroundPackage = pkg
                    _appOpened.tryEmit(pkg)
                }
            }
        }
    }

    override fun onInterrupt() {}

    /** Volume-key gestures (double / triple / long press) → commands. */
    override fun onKeyEvent(event: android.view.KeyEvent): Boolean =
        ButtonGestures.onKeyEvent(event) || super.onKeyEvent(event)

    override fun onUnbind(intent: Intent?): Boolean {
        _instance.value = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        _instance.value = null
        super.onDestroy()
    }

    fun root(): AccessibilityNodeInfo? = try {
        rootInActiveWindow
    } catch (e: Exception) {
        null
    }

    /** Package of the window currently in front. */
    fun currentPackage(): String? = root()?.packageName?.toString() ?: foregroundPackage

    /** Starting activities from the accessibility context is exempt from background-start limits. */
    fun launch(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    companion object {
        private val _instance = MutableStateFlow<AssistantAccessibilityService?>(null)
        val instance: StateFlow<AssistantAccessibilityService?> = _instance.asStateFlow()

        @Volatile var foregroundPackage: String? = null
            private set

        private val _appOpened = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 8)
        /** Emits the package name each time a different app comes to the foreground. */
        val appOpened: kotlinx.coroutines.flow.SharedFlow<String> = _appOpened
        @Volatile var lastEventAt: Long = 0
            private set

        fun get(): AssistantAccessibilityService? = _instance.value

        fun isEnabledInSettings(context: Context): Boolean {
            val expected = ComponentName(context, AssistantAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any {
                ComponentName.unflattenFromString(it) == expected
            }
        }
    }
}
