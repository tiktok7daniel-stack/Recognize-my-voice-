package com.daniel.assistant.executor

import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** Shares the newest N gallery images directly to a target app via ACTION_SEND(_MULTIPLE). */
object PhotoSharer {

    fun latestImages(context: Context, count: Int): List<Uri> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val out = mutableListOf<Uri>()
        context.contentResolver.query(collection, projection, null, null, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext() && out.size < count) {
                out.add(ContentUris.withAppendedId(collection, c.getLong(idCol)))
            }
        }
        return out
    }

    /**
     * Copies the newest [count] photos into our cache and returns FileProvider URIs —
     * these can be granted to any app (e.g. through the clipboard).
     */
    fun copyLatestToCache(context: Context, count: Int): List<Uri> {
        val dir = java.io.File(context.cacheDir, "shared").apply { deleteRecursively(); mkdirs() }
        val cr = context.contentResolver
        return latestImages(context, count).mapIndexedNotNull { i, src ->
            runCatching {
                val ext = when (cr.getType(src)) {
                    "image/png" -> "png"; "image/webp" -> "webp"; "image/heic" -> "heic"; else -> "jpg"
                }
                val f = java.io.File(dir, "nabra_${System.currentTimeMillis()}_$i.$ext")
                cr.openInputStream(src)!!.use { input -> f.outputStream().use { input.copyTo(it, 1 shl 16) } }
                androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", f)
            }.getOrNull()
        }
    }

    /** Returns null on success or an Arabic error message. */
    fun share(context: Context, targetPackage: String, count: Int, text: String? = null): String? {
        val uris = try {
            latestImages(context, count)
        } catch (e: SecurityException) {
            return "مفيش صلاحية قراءة الصور"
        }
        if (uris.isEmpty()) return "مفيش صور على الجهاز"

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = "image/*"
        if (!text.isNullOrBlank()) intent.putExtra(Intent.EXTRA_TEXT, text)
        val clip = ClipData.newUri(context.contentResolver, "images", uris.first())
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        intent.clipData = clip
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.setPackage(targetPackage)

        // Pick the target app's share activity explicitly to avoid a chooser.
        val pm = context.packageManager
        val matches = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
        }
        if (matches.isEmpty()) return "التطبيق ده مش بيستقبل صور"
        val best = matches.first().activityInfo
        intent.setClassName(best.packageName, best.name)
        uris.forEach { context.grantUriPermission(targetPackage, it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }

        return if (AppLauncher.start(context, intent)) null else "مقدرتش أفتح التطبيق"
    }
}
