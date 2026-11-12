package com.burhannexus.voicestudio.tts

import android.content.Context
import com.burhannexus.voicestudio.data.Prefs
import com.burhannexus.voicestudio.model.ModelManager
import java.io.File

class EngineException(message: String) : Exception(message)

/** Thrown when a cloud provider says "too many requests"; the generator waits and retries. */
class RateLimitException(message: String) : Exception(message)

data class Voice(
    val id: String,          // "<engineId>|<local id>" - unique across engines
    val engineId: String,
    val name: String,
    val gender: String,      // "Male", "Female" or "Not reported"
    val language: String,    // "ur", "en" or "multi"
    val accent: String,
    val description: String,
) {
    val localId: String get() = id.substringAfter('|')
    fun speaks(lang: String) = language == "multi" || language == lang
}

data class SynthRequest(
    val text: String,
    val voice: Voice,
    val speed: Float,
    val pitch: Float,
    val emotion: String?,
    val intensity: Int,
)

/**
 * Adapter every speech engine implements. To add a new model family later
 * (F5-TTS server, another cloud API ...) implement this and register it in [EngineRegistry].
 */
interface TtsEngine {
    val id: String
    val name: String
    val online: Boolean
    /** True only when the model itself can act emotions. Never faked with pitch tricks. */
    val supportsEmotion: Boolean
    val supportsPitch: Boolean
    val maxChunkChars: Int
    val notes: String

    suspend fun voices(): List<Voice>

    /** Writes a 16-bit PCM WAV to [out] or throws. */
    suspend fun synthesize(req: SynthRequest, out: File)
}

class EngineRegistry(ctx: Context, prefs: Prefs, models: ModelManager) {
    val system = SystemEngine(ctx)
    val sherpa = SherpaEngine(models)
    val gemini = GeminiEngine(prefs)
    val all: List<TtsEngine> = listOf(sherpa, system, gemini)

    fun engine(id: String): TtsEngine? = all.firstOrNull { it.id == id }
    fun engineOf(voice: Voice): TtsEngine = engine(voice.engineId) ?: throw EngineException("Unknown engine ${voice.engineId}")

    suspend fun allVoices(): List<Voice> {
        val out = ArrayList<Voice>()
        for (e in all) {
            try {
                out.addAll(e.voices())
            } catch (_: Exception) {
            }
        }
        return out
    }

    suspend fun findVoice(id: String): Voice? {
        val engine = engine(id.substringBefore('|')) ?: return null
        return try {
            engine.voices().firstOrNull { it.id == id }
        } catch (_: Exception) {
            null
        }
    }
}
