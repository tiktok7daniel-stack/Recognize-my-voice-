package com.daniel.assistant.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.daniel.assistant.commands.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Index of launchable apps (label + package), merged with user aliases. */
class AppIndex(private val context: Context) {

    data class RawApp(val label: String, val packageName: String)

    private val _apps = MutableStateFlow<List<RawApp>>(emptyList())
    val apps: StateFlow<List<RawApp>> = _apps.asStateFlow()

    private val icons = LruCache<String, Bitmap>(120)

    suspend fun refresh(): List<RawApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
        }
        val result = list
            .map { RawApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
        _apps.value = result
        result
    }

    suspend fun ensureLoaded(): List<RawApp> = _apps.value.ifEmpty { refresh() }

    fun entries(aliases: Map<String, List<String>>): List<AppEntry> =
        _apps.value.map { AppEntry(it.label, it.packageName, aliases[it.packageName].orEmpty()) }

    fun isInstalled(pkg: String): Boolean = runCatching {
        context.packageManager.getLaunchIntentForPackage(pkg) != null
    }.getOrDefault(false)

    fun labelOf(pkg: String): String? = _apps.value.firstOrNull { it.packageName == pkg }?.label

    fun icon(pkg: String): Bitmap? {
        icons.get(pkg)?.let { return it }
        return runCatching {
            context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96)
        }.getOrNull()?.also { icons.put(pkg, it) }
    }
}
