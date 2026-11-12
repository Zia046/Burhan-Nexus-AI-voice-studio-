package com.burhannexus.voicestudio.tts

import com.burhannexus.voicestudio.audio.Dsp
import com.burhannexus.voicestudio.audio.Wav
import com.burhannexus.voicestudio.model.ModelCatalog
import com.burhannexus.voicestudio.model.ModelInfo
import com.burhannexus.voicestudio.model.ModelManager
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Offline neural voices (Piper / Kokoro) running on the phone's CPU through sherpa-onnx + ONNX Runtime.
 * No internet and no fees once a model is installed from the Model Manager.
 * These models read text in a neutral voice only: they have no emotion control and no pitch control.
 */
class SherpaEngine(private val models: ModelManager) : TtsEngine {
    override val id = "sherpa"
    override val name = "Offline AI voices (Piper / Kokoro)"
    override val online = false
    override val supportsEmotion = false
    override val supportsPitch = false
    override val maxChunkChars = 220
    override val notes = "Model Manager se model install karein. Phone ke CPU par chalta hai, internet nahi chahiye."

    private val lock = Mutex()
    private var loadedId: String? = null
    private var tts: OfflineTts? = null

    override suspend fun voices(): List<Voice> = models.installed().flatMap { m ->
        m.speakers.map { s ->
            Voice(
                id = "$id|${m.id}#${s.sid}",
                engineId = id,
                name = if (m.speakers.size == 1) m.title else "${s.name} - ${m.title}",
                gender = s.gender,
                language = m.language,
                accent = s.accent,
                description = "Offline ${m.family.replaceFirstChar { it.uppercase() }} neural voice. Emotions / pitch support nahi.",
            )
        }
    }

    private fun load(m: ModelInfo): OfflineTts {
        if (loadedId == m.id) tts?.let { return it }
        release()
        val dir = models.modelDir(m).path
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        val modelConfig = if (m.family == "kokoro") {
            OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = "$dir/${m.modelFile}",
                    voices = "$dir/voices.bin",
                    tokens = "$dir/tokens.txt",
                    dataDir = "$dir/espeak-ng-data",
                ),
                numThreads = threads,
                debug = false,
                provider = "cpu",
            )
        } else {
            OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = "$dir/${m.modelFile}",
                    tokens = "$dir/tokens.txt",
                    dataDir = "$dir/espeak-ng-data",
                ),
                numThreads = threads,
                debug = false,
                provider = "cpu",
            )
        }
        val created = try {
            OfflineTts(null, OfflineTtsConfig(model = modelConfig))
        } catch (e: Throwable) {
            throw EngineException("Model load nahi hua (${m.title}). RAM kam ho sakti hai ya files kharab hain - model dobara install karein. [${e.message}]")
        }
        tts = created
        loadedId = m.id
        return created
    }

    fun release() {
        try {
            tts?.release()
        } catch (_: Throwable) {
        }
        tts = null
        loadedId = null
    }

    override suspend fun synthesize(req: SynthRequest, out: File) = withContext(Dispatchers.Default) {
        lock.withLock {
            val modelId = req.voice.localId.substringBefore('#')
            val sid = req.voice.localId.substringAfter('#', "0").toIntOrNull() ?: 0
            val info = ModelCatalog.byId(modelId) ?: throw EngineException("Unknown offline model: $modelId")
            if (!models.isInstalled(info)) throw EngineException("${info.title} install nahi hai. Model Manager se install karein.")
            val engine = load(info)
            val audio = try {
                engine.generate(req.text, sid, req.speed.coerceIn(0.5f, 2.0f))
            } catch (e: Throwable) {
                release()
                throw EngineException("Offline model audio nahi bana saka: ${e.message ?: e.javaClass.simpleName}")
            }
            if (audio.samples.isEmpty()) throw EngineException("Offline model ne khaali audio di (is text ki zaban model support nahi karta?).")
            Wav.write(out, Dsp.floatToPcm(audio.samples), audio.sampleRate)
        }
    }
}
