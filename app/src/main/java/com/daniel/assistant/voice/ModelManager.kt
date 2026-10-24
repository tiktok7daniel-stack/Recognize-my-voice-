package com.daniel.assistant.voice

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

sealed interface ModelState {
    data object Missing : ModelState
    data class Downloading(val downloaded: Long, val total: Long) : ModelState {
        val progress: Float get() = if (total > 0) downloaded.toFloat() / total else 0f
    }
    data class Unzipping(val files: Int) : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

/**
 * Downloads (with HTTP Range resume) and unpacks the Vosk model into
 * filesDir/model. Also supports importing a model zip picked by the user.
 */
class ModelManager(private val context: Context, private val scope: CoroutineScope) {

    val modelDir = File(context.filesDir, "model")
    private val partFile = File(context.filesDir, "model.zip.part")
    private val tmpDir = File(context.filesDir, "model_tmp")

    private val _state = MutableStateFlow<ModelState>(if (isReady()) ModelState.Ready else ModelState.Missing)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private var job: Job? = null

    fun isReady(): Boolean =
        File(modelDir, "am/final.mdl").exists() || File(modelDir, "conf/model.conf").exists()

    /** True when the model contains runtime-graph files needed for grammar mode. */
    fun supportsGrammar(): Boolean =
        File(modelDir, "graph/HCLr.fst").exists() && File(modelDir, "graph/Gr.fst").exists()

    fun modelSizeMb(): Long = if (!modelDir.exists()) 0 else modelDir.walk().sumOf { it.length() } / (1024 * 1024)

    fun download(url: String) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            try {
                downloadZip(url)
                _state.value = ModelState.Unzipping(0)
                partFile.inputStream().use { unpack(it) }
                partFile.delete()
                _state.value = ModelState.Ready
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = ModelState.Missing
                throw e
            } catch (e: Exception) {
                _state.value = ModelState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    fun importZip(uri: Uri) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            try {
                _state.value = ModelState.Unzipping(0)
                val input = context.contentResolver.openInputStream(uri) ?: error("مش قادر أفتح الملف")
                input.use { unpack(it) }
                _state.value = ModelState.Ready
            } catch (e: Exception) {
                _state.value = ModelState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        modelDir.deleteRecursively()
        partFile.delete()
        _state.value = ModelState.Missing
    }

    private suspend fun downloadZip(url: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        var existing = if (partFile.exists()) partFile.length() else 0L
        val req = Request.Builder().url(url)
            .apply { if (existing > 0) header("Range", "bytes=$existing-") }
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 416) { // already complete
                return
            }
            if (!resp.isSuccessful) error("فشل التحميل (HTTP ${resp.code})")
            val append = resp.code == 206
            if (!append) existing = 0
            val body = resp.body ?: error("رد فاضي من السيرفر")
            val total = if (body.contentLength() > 0) body.contentLength() + existing else -1L
            FileOutputStream(partFile, append).use { out ->
                val buf = ByteArray(64 * 1024)
                var done = existing
                var lastEmit = 0L
                body.byteStream().use { input ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - lastEmit > 256 * 1024) {
                            lastEmit = done
                            _state.value = ModelState.Downloading(done, total)
                        }
                    }
                }
                _state.value = ModelState.Downloading(done, total)
                if (total > 0 && done < total) error("التحميل اتقطع، دوس إعادة المحاولة وهيكمل من مكانه")
            }
        }
    }

    /** Unzips into a temp dir, locates the model root and moves it to [modelDir]. */
    private suspend fun unpack(input: InputStream) {
        tmpDir.deleteRecursively()
        tmpDir.mkdirs()
        val canonicalTmp = tmpDir.canonicalPath + File.separator
        var count = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                coroutineContext.ensureActive()
                val entry = zip.nextEntry ?: break
                val out = File(tmpDir, entry.name)
                if (!out.canonicalPath.startsWith(canonicalTmp)) error("ملف zip غير آمن")
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zip.copyTo(it) }
                    count++
                    if (count % 20 == 0) _state.value = ModelState.Unzipping(count)
                }
            }
        }
        if (count == 0) error("الملف مش zip صالح")
        val root = tmpDir.walk().firstOrNull { dir ->
            dir.isDirectory && (File(dir, "am/final.mdl").exists() || File(dir, "conf/model.conf").exists())
        } ?: error("الملف ده مش موديل Vosk")
        modelDir.deleteRecursively()
        if (!root.renameTo(modelDir)) {
            root.copyRecursively(modelDir, overwrite = true)
        }
        tmpDir.deleteRecursively()
    }
}
