package com.burhannexus.voicestudio.gen

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.burhannexus.voicestudio.App
import com.burhannexus.voicestudio.audio.Dsp
import com.burhannexus.voicestudio.audio.Wav
import com.burhannexus.voicestudio.audio.WavWriter
import com.burhannexus.voicestudio.tts.EngineException
import com.burhannexus.voicestudio.tts.RateLimitException
import com.burhannexus.voicestudio.tts.Segment
import com.burhannexus.voicestudio.tts.SynthRequest
import com.burhannexus.voicestudio.tts.TextProcessor
import com.burhannexus.voicestudio.tts.TtsEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

enum class GenStatus { IDLE, RUNNING, PAUSED, DONE, FAILED, STOPPED }

data class GenState(
    val projectId: Long = 0,
    val status: GenStatus = GenStatus.IDLE,
    val total: Int = 0,
    val done: Int = 0,
    val current: Int = -1,
    val message: String = "",
    val etaSec: Long = -1,
    val labels: List<String> = emptyList(),
) {
    val active: Boolean get() = status == GenStatus.RUNNING || status == GenStatus.PAUSED
    val percent: Int get() = if (total == 0) 0 else done * 100 / total
}

/**
 * Generates a project's audio chunk by chunk.
 * Every finished chunk is kept on disk, so after an error, a stop, or the app being killed,
 * "Resume" continues from the first missing chunk instead of starting over.
 */
class GenerationManager(private val app: App) {
    private val _state = MutableStateFlow(GenState())
    val state: StateFlow<GenState> = _state

    private var job: Job? = null

    @Volatile
    private var paused = false

    fun projectDir(id: Long): File = File(app.filesDir, "projects/$id").apply { mkdirs() }
    fun chunkFile(id: Long, index: Int): File = File(projectDir(id), "chunk_%04d.wav".format(index))
    fun finalWav(id: Long): File = File(projectDir(id), "final.wav")
    fun finalMp3(id: Long): File = File(projectDir(id), "final.mp3")

    fun start(projectId: Long, fresh: Boolean) {
        if (_state.value.active) return
        paused = false
        _state.value = GenState(projectId = projectId, status = GenStatus.RUNNING, message = "Tayyari...")
        job = app.scope.launch { run(projectId, fresh) }
    }

    fun pause() {
        if (_state.value.status == GenStatus.RUNNING) {
            paused = true
            _state.update { it.copy(status = GenStatus.PAUSED, message = "Paused - jari chunk mukammal ho kar ruk jayega") }
        }
    }

    fun resume() {
        if (_state.value.status == GenStatus.PAUSED) {
            paused = false
            _state.update { it.copy(status = GenStatus.RUNNING, message = "Jari...") }
        }
    }

    fun stop() {
        paused = false
        job?.cancel()
    }

    fun clearIfFinished(projectId: Long) {
        val s = _state.value
        if (!s.active && s.projectId != projectId) _state.value = GenState()
    }

    private fun lowRam(): Boolean {
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return am.isLowRamDevice || info.totalMem < 3L * 1024 * 1024 * 1024
    }

    private suspend fun synthWithRetry(engine: TtsEngine, req: SynthRequest, out: File) {
        var attempt = 0
        while (true) {
            try {
                engine.synthesize(req, out)
                return
            } catch (e: RateLimitException) {
                attempt++
                if (attempt > 3) throw EngineException(e.message ?: "Rate limit")
                val waitSec = 25L * attempt
                _state.update { it.copy(message = "Cloud limit - $waitSec second intezar ke baad dobara koshish ($attempt/3)") }
                delay(waitSec * 1000)
            }
        }
    }

    private suspend fun run(projectId: Long, fresh: Boolean) {
        val dao = app.db.projects()
        var done = 0
        try {
            var p = dao.get(projectId) ?: throw EngineException("Project nahi mila")
            val voice = app.engines.findVoice(p.voiceId)
                ?: throw EngineException("Voice '${p.voiceName}' dastiyab nahi. Model install karein ya doosri voice chunein.")
            val engine = app.engines.engineOf(voice)
            val maxChars = if (!engine.online && lowRam()) minOf(engine.maxChunkChars, 140) else engine.maxChunkChars
            val parsed = TextProcessor.parse(p.script, maxChars)
            val segments = parsed.segments
            if (segments.none { it is Segment.Speech }) throw EngineException("Script mein bolne ke liye koi text nahi.")

            val dir = projectDir(projectId)
            val signature = listOf(p.script, p.voiceId, p.speed, p.pitch, p.emotion, p.intensity, maxChars)
                .joinToString("|").hashCode().toString()
            val sigFile = File(dir, "signature.txt")
            if (fresh || !sigFile.exists() || sigFile.readText() != signature) {
                dir.listFiles()?.forEach { it.delete() }
                sigFile.writeText(signature)
            }
            finalWav(projectId).delete()
            finalMp3(projectId).delete()

            val total = segments.size
            val labels = segments.map {
                when (it) {
                    is Segment.Speech -> (if (it.emotion != null) "[${it.emotion}] " else "") + it.text.take(70)
                    is Segment.Pause -> "Pause ${it.ms} ms"
                }
            }
            fun exists(i: Int) = chunkFile(projectId, i).let { it.exists() && it.length() > 44 }
            done = (0 until total).count { exists(it) }
            _state.value = GenState(projectId, GenStatus.RUNNING, total, done, -1, "Shuru...", -1, labels)
            p = p.copy(status = "running", totalChunks = total, doneChunks = done, audioPath = "", durationMs = 0, updatedAt = System.currentTimeMillis())
            dao.update(p)
            try {
                ContextCompat.startForegroundService(app, Intent(app, GenerationService::class.java))
            } catch (_: Exception) {
            }

            val defaultEmotion = p.emotion.takeIf { it.isNotBlank() && it != "None" }
            var genChars = 0L
            var genMs = 0L
            val remainingChars = { from: Int ->
                var n = 0L
                for (i in from until total) {
                    val s = segments[i]
                    if (s is Segment.Speech && !exists(i)) n += s.text.length
                }
                n
            }

            for ((i, seg) in segments.withIndex()) {
                while (paused) delay(200)
                coroutineContext.ensureActive()
                val file = chunkFile(projectId, i)
                if (!exists(i)) {
                    _state.update { it.copy(current = i, status = if (paused) GenStatus.PAUSED else GenStatus.RUNNING, message = "Chunk ${i + 1} / $total ban raha hai") }
                    when (seg) {
                        is Segment.Pause -> Wav.write(file, Dsp.silence(seg.ms, 24000), 24000)
                        is Segment.Speech -> {
                            val tmp = File(dir, "tmp_$i.wav")
                            val emotion = if (engine.supportsEmotion) (seg.emotion ?: defaultEmotion) else null
                            val t0 = System.currentTimeMillis()
                            synthWithRetry(engine, SynthRequest(seg.text, voice, p.speed, p.pitch, emotion, p.intensity), tmp)
                            genMs += System.currentTimeMillis() - t0
                            genChars += seg.text.length
                            val pcm = Wav.read(tmp)
                            tmp.delete()
                            val cleaned = Dsp.normalize(Dsp.trimSilence(pcm.samples, pcm.sampleRate))
                            if (cleaned.isEmpty()) throw EngineException("Chunk ${i + 1} ki audio khaali aayi.")
                            Wav.write(file, cleaned, pcm.sampleRate)
                        }
                    }
                    done++
                }
                val eta = if (genChars > 0) remainingChars(i + 1) * genMs / genChars / 1000 else -1
                _state.update { it.copy(done = done, etaSec = eta) }
                if (i % 5 == 0) dao.update(p.copy(doneChunks = done, updatedAt = System.currentTimeMillis()))
            }

            _state.update { it.copy(current = -1, message = "Tamam hisson ko jor raha hai...", etaSec = 0) }
            val durationMs = merge(projectId, segments)
            dao.update(
                p.copy(
                    status = "done", doneChunks = total, audioPath = finalWav(projectId).path,
                    durationMs = durationMs, updatedAt = System.currentTimeMillis()
                )
            )
            _state.update { it.copy(status = GenStatus.DONE, done = total, message = "Mukammal - audio tayyar hai", etaSec = 0) }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                dao.get(projectId)?.let { dao.update(it.copy(status = "stopped", doneChunks = done, updatedAt = System.currentTimeMillis())) }
            }
            _state.update { it.copy(status = GenStatus.STOPPED, current = -1, message = "Roka gaya - Resume dabane par wahin se jari hoga") }
            throw e
        } catch (e: Throwable) {
            val msg = if (e is OutOfMemoryError) "Phone ki memory kam par gayi. Chhota model ya chhota script try karein." else (e.message ?: e.toString())
            try {
                dao.get(projectId)?.let { dao.update(it.copy(status = "failed", doneChunks = done, updatedAt = System.currentTimeMillis())) }
            } catch (_: Exception) {
            }
            _state.update { it.copy(status = GenStatus.FAILED, current = -1, message = "Error: $msg") }
        }
    }

    /** Joins all chunk files, in order, into one WAV. Returns the duration in ms. */
    private fun merge(projectId: Long, segments: List<Segment>): Long {
        val firstSpeech = segments.indexOfFirst { it is Segment.Speech }
        val rate = Wav.read(chunkFile(projectId, firstSpeech)).sampleRate
        val out = finalWav(projectId)
        val tmp = File(out.path + ".tmp")
        var frames = 0L
        WavWriter(tmp, rate).use { w ->
            for (i in segments.indices) {
                val pcm = Wav.read(chunkFile(projectId, i))
                w.append(Dsp.resample(pcm.samples, pcm.sampleRate, rate))
                // short natural gap between two spoken chunks (edges were trimmed)
                if (segments[i] is Segment.Speech && i + 1 < segments.size && segments[i + 1] is Segment.Speech) {
                    w.append(Dsp.silence(160, rate))
                }
            }
            frames = w.frames
        }
        out.delete()
        tmp.renameTo(out)
        return frames * 1000L / rate
    }
}
