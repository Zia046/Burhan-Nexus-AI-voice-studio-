package com.burhannexus.voicestudio.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The phone's own offline TTS engine (normally "Speech Services by Google").
 * Free, offline, fast, and it is the only offline engine here with both Urdu and pitch control.
 * Voice quality is the phone's - less natural than the neural / cloud voices.
 */
class SystemEngine(private val ctx: Context) : TtsEngine {
    override val id = "sys"
    override val name = "Phone System TTS (offline)"
    override val online = false
    override val supportsEmotion = false
    override val supportsPitch = true
    override val maxChunkChars = 300
    override val notes = "Phone ka apna TTS engine. Urdu ke liye Google TTS ka Urdu voice data install hona chahiye."

    private var tts: TextToSpeech? = null
    private val initLock = Mutex()
    private val synthLock = Mutex()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    private suspend fun ensure(): TextToSpeech = initLock.withLock {
        tts?.let { return@withLock it }
        val created = withContext(Dispatchers.Main) {
            val ready = CompletableDeferred<Int>()
            val t = TextToSpeech(ctx.applicationContext) { status -> ready.complete(status) }
            val status = withTimeout(15_000) { ready.await() }
            if (status != TextToSpeech.SUCCESS) {
                t.shutdown()
                throw EngineException("Phone ka System TTS engine dastiyab nahi. Play Store se 'Speech Services by Google' install karein.")
            }
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId != null) pending.remove(utteranceId)?.complete(true)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId != null) pending.remove(utteranceId)?.complete(false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId != null) pending.remove(utteranceId)?.complete(false)
                }
            })
            t
        }
        tts = created
        created
    }

    private fun usable(v: android.speech.tts.Voice): Boolean {
        val lang = v.locale?.language ?: return false
        if (lang != "ur" && lang != "en") return false
        if (v.isNetworkConnectionRequired) return false
        val features = v.features ?: emptySet()
        return !features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
    }

    override suspend fun voices(): List<Voice> {
        val t = ensure()
        val list = try {
            t.voices?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        return list.filter { usable(it) }
            .sortedWith(compareBy({ if (it.locale.language == "ur") 0 else 1 }, { it.locale.country }, { it.name }))
            .map { v ->
                val lower = v.name.lowercase()
                val gender = when {
                    lower.contains("female") -> "Female"
                    lower.contains("male") -> "Male"
                    else -> "Not reported"
                }
                Voice(
                    id = "$id|${v.name}",
                    engineId = id,
                    name = "${v.locale.displayLanguage} (${v.locale.country}) - ${v.name}",
                    gender = gender,
                    language = v.locale.language,
                    accent = v.locale.displayCountry.ifBlank { v.locale.toString() },
                    description = "Phone ki offline system voice. Android gender nahi batata - Preview sun kar chunein.",
                )
            }
    }

    override suspend fun synthesize(req: SynthRequest, out: File) {
        val t = ensure()
        synthLock.withLock {
            val target = try {
                t.voices?.firstOrNull { it.name == req.voice.localId }
            } catch (e: Exception) {
                null
            } ?: throw EngineException("System voice '${req.voice.localId}' ab phone par maujood nahi.")
            t.voice = target
            t.setSpeechRate(req.speed.coerceIn(0.5f, 2.0f))
            t.setPitch(req.pitch.coerceIn(0.5f, 2.0f))
            val utteranceId = UUID.randomUUID().toString()
            val done = CompletableDeferred<Boolean>()
            pending[utteranceId] = done
            out.delete()
            val result = t.synthesizeToFile(req.text, Bundle(), out, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                pending.remove(utteranceId)
                throw EngineException("System TTS ne request qabool nahi ki.")
            }
            val ok = try {
                withTimeout(180_000) { done.await() }
            } finally {
                pending.remove(utteranceId)
            }
            if (!ok || !out.exists() || out.length() <= 44) {
                throw EngineException("System TTS is text ki audio nahi bana saka (voice data install hai?).")
            }
        }
    }
}
