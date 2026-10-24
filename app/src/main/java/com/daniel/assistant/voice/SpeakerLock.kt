package com.daniel.assistant.voice

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.vosk.SpkModel
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.math.sqrt

/**
 * "Voice lock": a Vosk speaker model turns each utterance into a voice-print vector.
 * The user's enrolled print is compared (cosine similarity) with the wake word speaker,
 * so only the owner's voice can trigger commands.
 */
class SpeakerLock(private val context: Context) {

    private val dir = File(context.filesDir, "spk")
    private var model: SpkModel? = null

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _lastSimilarity = MutableStateFlow<Float?>(null)
    /** Similarity of the last checked utterance (helps tune the threshold). */
    val lastSimilarity: StateFlow<Float?> = _lastSimilarity.asStateFlow()

    fun isDownloaded(): Boolean = File(dir, "mean.vec").exists() || File(dir, "final.ext.raw").exists()

    suspend fun download(): Boolean = withContext(Dispatchers.IO) {
        try {
            _status.value = "بنزّل موديل بصمة الصوت (13 ميجا)…"
            val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
            val zip = File(context.filesDir, "spk.zip")
            http.newCall(Request.Builder().url(URL).build()).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                resp.body!!.byteStream().use { input -> FileOutputStream(zip).use { input.copyTo(it) } }
            }
            val tmp = File(context.filesDir, "spk_tmp").apply { deleteRecursively(); mkdirs() }
            val canonical = tmp.canonicalPath + File.separator
            ZipInputStream(zip.inputStream().buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val out = File(tmp, e.name)
                    if (!out.canonicalPath.startsWith(canonical)) error("zip غير آمن")
                    if (e.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs(); FileOutputStream(out).use { z.copyTo(it) }
                    }
                }
            }
            val root = tmp.walk().firstOrNull { File(it, "mean.vec").exists() || File(it, "final.ext.raw").exists() }
                ?: error("الملف مش موديل بصمة")
            dir.deleteRecursively()
            if (!root.renameTo(dir)) root.copyRecursively(dir, overwrite = true)
            tmp.deleteRecursively(); zip.delete()
            _status.value = "موديل البصمة جاهز ✅"
            true
        } catch (e: Exception) {
            _status.value = "فشل التحميل: ${e.message}"
            false
        }
    }

    suspend fun load(): SpkModel? = withContext(Dispatchers.IO) {
        model ?: runCatching { if (isDownloaded()) SpkModel(dir.absolutePath) else null }.getOrNull()?.also { model = it }
    }

    /** Compares [vector] with the enrolled [print]; records the similarity. */
    fun matches(vector: FloatArray?, print: FloatArray?, threshold: Float): Boolean {
        if (print == null) return true
        if (vector == null) {
            _lastSimilarity.value = null
            return false
        }
        val sim = cosine(vector, print)
        _lastSimilarity.value = sim
        return sim >= threshold
    }

    companion object {
        const val URL = "https://alphacephei.com/vosk/models/vosk-model-spk-0.4.zip"

        fun cosine(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size || a.isEmpty()) return 0f
            var dot = 0.0; var na = 0.0; var nb = 0.0
            for (i in a.indices) {
                dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]
            }
            return if (na == 0.0 || nb == 0.0) 0f else (dot / (sqrt(na) * sqrt(nb))).toFloat()
        }

        fun average(vectors: List<FloatArray>): FloatArray? {
            val v = vectors.filter { it.isNotEmpty() }
            if (v.isEmpty()) return null
            val n = v.first().size
            val out = FloatArray(n)
            v.filter { it.size == n }.forEach { x -> for (i in 0 until n) out[i] += x[i] }
            for (i in 0 until n) out[i] /= v.size
            return out
        }

        fun encode(v: FloatArray): String = v.joinToString(",")
        fun decode(s: String): FloatArray? =
            s.takeIf { it.isNotBlank() }?.split(",")?.mapNotNull { it.toFloatOrNull() }?.toFloatArray()
    }
}
