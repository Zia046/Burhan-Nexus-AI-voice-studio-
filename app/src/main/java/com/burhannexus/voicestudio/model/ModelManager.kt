package com.burhannexus.voicestudio.model

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class Speaker(val sid: Int, val name: String, val gender: String, val accent: String)

/** One downloadable offline model. All of these run on-device through sherpa-onnx. */
data class ModelInfo(
    val id: String,            // also the folder name inside the archive
    val title: String,
    val family: String,        // "piper" or "kokoro"
    val language: String,      // "ur" / "en"
    val downloadMb: Int,       // approximate; the real size is shown while downloading
    val ramMb: Int,            // approximate working memory
    val speed: String,
    val license: String,
    val modelFile: String,
    val speakers: List<Speaker>,
) {
    val url: String get() = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$id.tar.bz2"
}

data class DownloadState(val phase: String, val progress: Float, val message: String = "")

object ModelCatalog {
    private const val PIPER_NOTE = "Engine MIT. Voice ka apna license archive ke MODEL_CARD mein hai - commercial istemal se pehle wahan check karein."

    val models = listOf(
        ModelInfo(
            id = "vits-piper-ur_PK-fasih-medium", title = "Piper Urdu - Fasih", family = "piper", language = "ur",
            downloadMb = 65, ramMb = 300, speed = "Tez (aam phone par real-time se tez)",
            license = "$PIPER_NOTE Baaz listings is voice ko sirf personal / non-commercial batati hain.",
            modelFile = "ur_PK-fasih-medium.onnx",
            speakers = listOf(Speaker(0, "Fasih", "Male", "Pakistan")),
        ),
        ModelInfo(
            id = "vits-piper-en_US-ryan-medium", title = "Piper English - Ryan", family = "piper", language = "en",
            downloadMb = 65, ramMb = 300, speed = "Tez", license = PIPER_NOTE,
            modelFile = "en_US-ryan-medium.onnx",
            speakers = listOf(Speaker(0, "Ryan", "Male", "American")),
        ),
        ModelInfo(
            id = "vits-piper-en_US-lessac-medium", title = "Piper English - Lessac", family = "piper", language = "en",
            downloadMb = 65, ramMb = 300, speed = "Tez", license = PIPER_NOTE,
            modelFile = "en_US-lessac-medium.onnx",
            speakers = listOf(Speaker(0, "Lessac", "Female", "American")),
        ),
        ModelInfo(
            id = "vits-piper-en_US-hfc_female-medium", title = "Piper English - HFC Female", family = "piper", language = "en",
            downloadMb = 65, ramMb = 300, speed = "Tez", license = PIPER_NOTE,
            modelFile = "en_US-hfc_female-medium.onnx",
            speakers = listOf(Speaker(0, "HFC Female", "Female", "American")),
        ),
        ModelInfo(
            id = "vits-piper-en_GB-alan-medium", title = "Piper English - Alan", family = "piper", language = "en",
            downloadMb = 65, ramMb = 300, speed = "Tez", license = PIPER_NOTE,
            modelFile = "en_GB-alan-medium.onnx",
            speakers = listOf(Speaker(0, "Alan", "Male", "British")),
        ),
        ModelInfo(
            id = "kokoro-en-v0_19", title = "Kokoro 82M English (11 voices)", family = "kokoro", language = "en",
            downloadMb = 305, ramMb = 900, speed = "Sust: budget phone par 1 minute audio = 2-3 minute; 4 GB+ RAM behtar",
            license = "Apache-2.0 (commercial istemal ki ijazat)",
            modelFile = "model.onnx",
            speakers = listOf(
                Speaker(0, "AF (default)", "Female", "American"),
                Speaker(1, "Bella", "Female", "American"),
                Speaker(2, "Nicole", "Female", "American"),
                Speaker(3, "Sarah", "Female", "American"),
                Speaker(4, "Sky", "Female", "American"),
                Speaker(5, "Adam", "Male", "American"),
                Speaker(6, "Michael", "Male", "American"),
                Speaker(7, "Emma", "Female", "British"),
                Speaker(8, "Isabella", "Female", "British"),
                Speaker(9, "George", "Male", "British"),
                Speaker(10, "Lewis", "Male", "British"),
            ),
        ),
    )

    fun byId(id: String) = models.firstOrNull { it.id == id }
}

class ModelManager(private val ctx: Context, private val scope: CoroutineScope) {
    private val root = File(ctx.filesDir, "models").apply { mkdirs() }
    private val jobs = HashMap<String, Job>()

    private val _downloads = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadState>> = _downloads

    /** Bumped whenever a model is installed or removed, so screens refresh. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    fun modelDir(m: ModelInfo) = File(File(root, m.id), m.id)
    fun isInstalled(m: ModelInfo) = File(File(root, m.id), ".installed").exists() && File(modelDir(m), m.modelFile).exists()
    fun installed(): List<ModelInfo> = ModelCatalog.models.filter { isInstalled(it) }

    fun sizeOnDisk(m: ModelInfo): Long = dirSize(File(root, m.id))
    fun totalSize(): Long = dirSize(root)

    fun freeBytes(): Long = try {
        StatFs(ctx.filesDir.path).availableBytes
    } catch (e: Exception) {
        0
    }

    fun deviceRamMb(): Long {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / (1024 * 1024)
    }

    fun remove(m: ModelInfo) {
        jobs.remove(m.id)?.cancel()
        File(root, m.id).deleteRecursively()
        setState(m.id, null)
        _revision.update { it + 1 }
    }

    fun cancel(m: ModelInfo) {
        jobs.remove(m.id)?.cancel()
        File(root, m.id).deleteRecursively()
        setState(m.id, null)
    }

    private fun setState(id: String, s: DownloadState?) {
        _downloads.update { if (s == null) it - id else it + (id to s) }
    }

    fun install(m: ModelInfo) {
        if (jobs[m.id]?.isActive == true || isInstalled(m)) return
        val needed = m.downloadMb * 3L * 1024 * 1024
        if (freeBytes() in 1 until needed) {
            setState(m.id, DownloadState("error", 0f, "Storage kam hai: taqreeban ${m.downloadMb * 3} MB khaali jagah chahiye."))
            return
        }
        jobs[m.id] = scope.launch(Dispatchers.IO) {
            val base = File(root, m.id)
            val archive = File(ctx.cacheDir, "${m.id}.tar.bz2")
            try {
                base.deleteRecursively()
                base.mkdirs()
                setState(m.id, DownloadState("downloading", 0f, "Connecting..."))
                download(m, archive)
                setState(m.id, DownloadState("extracting", 0f, "Files khol raha hai..."))
                extract(m, archive, base)
                if (!File(modelDir(m), m.modelFile).exists() || !File(modelDir(m), "tokens.txt").exists()) {
                    throw IllegalStateException("Archive mein model files nahi milein.")
                }
                File(base, ".installed").writeText(System.currentTimeMillis().toString())
                setState(m.id, null)
                _revision.update { it + 1 }
            } catch (e: kotlinx.coroutines.CancellationException) {
                base.deleteRecursively()
                throw e
            } catch (e: Exception) {
                base.deleteRecursively()
                setState(m.id, DownloadState("error", 0f, "Install nakaam: ${e.message ?: e.javaClass.simpleName}"))
            } finally {
                archive.delete()
            }
        }
    }

    private suspend fun download(m: ModelInfo, target: File) {
        var url = m.url
        var conn: HttpURLConnection? = null
        try {
            for (hop in 0 until 6) {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 20_000
                c.readTimeout = 60_000
                c.instanceFollowRedirects = false
                c.setRequestProperty("User-Agent", "BurhanNexusVoiceStudio")
                val code = c.responseCode
                if (code in 300..399) {
                    val next = c.getHeaderField("Location")
                    c.disconnect()
                    url = next ?: throw IllegalStateException("Redirect without location")
                    continue
                }
                if (code != 200) {
                    c.disconnect()
                    throw IllegalStateException("Server ne HTTP $code diya")
                }
                conn = c
                break
            }
            val c = conn ?: throw IllegalStateException("Too many redirects")
            val total = c.contentLengthLong
            var done = 0L
            var lastUpdate = 0L
            c.inputStream.use { input ->
                FileOutputStream(target).use { out ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 400) {
                            lastUpdate = now
                            val mb = done / (1024 * 1024)
                            val totalMb = if (total > 0) total / (1024 * 1024) else m.downloadMb.toLong()
                            val p = if (total > 0) done.toFloat() / total else 0f
                            setState(m.id, DownloadState("downloading", p, "$mb / $totalMb MB"))
                        }
                    }
                }
            }
            if (total > 0 && done != total) throw IllegalStateException("Download adhoora reh gaya")
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun extract(m: ModelInfo, archive: File, dest: File) {
        val canonicalDest = dest.canonicalPath + File.separator
        val totalBytes = archive.length().coerceAtLeast(1)
        val fileIn = FileInputStream(archive)
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(fileIn, 1 shl 16))).use { tar ->
            var count = 0
            while (true) {
                kotlin.coroutines.coroutineContext.ensureActive()
                val entry = tar.nextEntry ?: break
                val out = File(dest, entry.name)
                if (!out.canonicalPath.startsWith(canonicalDest)) continue   // never write outside the model folder
                if (entry.isDirectory) {
                    out.mkdirs()
                } else if (entry.isFile) {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { tar.copyTo(it, 1 shl 16) }
                }
                if (++count % 25 == 0) {
                    val p = fileIn.channel.position().toFloat() / totalBytes
                    setState(m.id, DownloadState("extracting", p.coerceIn(0f, 1f), "Files khol raha hai..."))
                }
            }
        }
    }

    companion object {
        fun dirSize(f: File): Long {
            if (!f.exists()) return 0
            if (f.isFile) return f.length()
            var total = 0L
            f.listFiles()?.forEach { total += dirSize(it) }
            return total
        }
    }
}
