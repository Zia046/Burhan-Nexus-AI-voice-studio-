package com.burhannexus.voicestudio.tts

import android.util.Base64
import com.burhannexus.voicestudio.audio.Wav
import com.burhannexus.voicestudio.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Google Gemini TTS through the user's own API key (Google AI Studio free tier).
 * This is the only engine in the app whose model really performs emotions, and it speaks Urdu.
 * Text is sent to Google - the UI asks for consent before the first cloud request.
 */
class GeminiEngine(private val prefs: Prefs) : TtsEngine {
    override val id = "gemini"
    override val name = "Google Gemini TTS (online)"
    override val online = true
    override val supportsEmotion = true
    override val supportsPitch = false
    override val maxChunkChars = 900
    override val notes = "Apni free Gemini API key Settings mein dalein. Free tier ki rozana/minute limits Google tay karta hai."

    // name, gender, style - as published by Google for the prebuilt Gemini TTS voices
    private val catalog = listOf(
        Triple("Charon", "Male", "Informative - news / documentary"),
        Triple("Kore", "Female", "Firm - professional"),
        Triple("Algenib", "Male", "Gravelly - deep"),
        Triple("Gacrux", "Female", "Mature"),
        Triple("Puck", "Male", "Upbeat - young"),
        Triple("Leda", "Female", "Youthful"),
        Triple("Achird", "Male", "Friendly - conversational"),
        Triple("Sulafat", "Female", "Warm - storytelling"),
        Triple("Orus", "Male", "Firm"),
        Triple("Aoede", "Female", "Breezy"),
        Triple("Fenrir", "Male", "Excitable"),
        Triple("Zephyr", "Female", "Bright"),
        Triple("Enceladus", "Male", "Breathy - soft"),
        Triple("Achernar", "Female", "Soft"),
        Triple("Iapetus", "Male", "Clear"),
        Triple("Vindemiatrix", "Female", "Gentle"),
        Triple("Sadaltager", "Male", "Knowledgeable"),
        Triple("Despina", "Female", "Smooth"),
        Triple("Rasalgethi", "Male", "Informative"),
        Triple("Callirrhoe", "Female", "Easy-going"),
        Triple("Umbriel", "Male", "Easy-going"),
        Triple("Autonoe", "Female", "Bright"),
        Triple("Algieba", "Male", "Smooth"),
        Triple("Erinome", "Female", "Clear"),
        Triple("Alnilam", "Male", "Firm"),
        Triple("Laomedeia", "Female", "Upbeat"),
        Triple("Schedar", "Male", "Even"),
        Triple("Pulcherrima", "Female", "Forward"),
        Triple("Zubenelgenubi", "Male", "Casual"),
        Triple("Sadachbia", "Male", "Lively"),
    )

    override suspend fun voices(): List<Voice> = catalog.map { (n, g, style) ->
        Voice(
            id = "$id|$n",
            engineId = id,
            name = "$n ($style)",
            gender = g,
            language = "multi",
            accent = "Text ki zaban ke mutabiq (Urdu / English)",
            description = "Gemini cloud voice - $style. Urdu + English, emotions supported. Internet aur API key zaroori.",
        )
    }

    private val emotionStyles = mapOf(
        "Happy" to "happy and cheerful",
        "Laughing" to "amused, with light natural laughter in the voice",
        "Sad" to "sad and sorrowful",
        "Crying" to "tearful, as if crying while speaking",
        "Angry" to "angry",
        "Excited" to "excited and energetic",
        "Surprised" to "surprised and astonished",
        "Romantic" to "romantic, soft and affectionate",
        "Emotional" to "deeply emotional and moved",
        "Fearful" to "frightened and nervous",
        "Whispering" to "whispering quietly",
        "Shouting" to "shouting loudly",
        "Calm" to "calm and relaxed",
        "Serious" to "serious and formal",
        "Motivational" to "motivational, inspiring and confident",
        "Storytelling" to "like an expressive storyteller",
        "Sarcastic" to "sarcastic",
    )

    fun buildPrompt(req: SynthRequest): String {
        val parts = ArrayList<String>()
        val style = req.emotion?.let { emotionStyles[it] }
        if (style != null) {
            val degree = when {
                req.intensity < 25 -> "slightly "
                req.intensity < 60 -> ""
                req.intensity < 85 -> "very "
                else -> "extremely "
            }
            parts.add("in a $degree$style tone")
        }
        when {
            req.speed < 0.7f -> parts.add("very slowly")
            req.speed < 0.9f -> parts.add("at a slow pace")
            req.speed > 1.5f -> parts.add("very fast")
            req.speed > 1.15f -> parts.add("at a fast pace")
        }
        val how = if (parts.isEmpty()) "naturally and clearly" else parts.joinToString(", ")
        return "Read the following text aloud $how. Say exactly the words written, in the same language, " +
            "without translating, adding or skipping anything:\n\n${req.text}"
    }

    override suspend fun synthesize(req: SynthRequest, out: File) = withContext(Dispatchers.IO) {
        val key = prefs.geminiKey
        if (key.isBlank()) throw EngineException("Gemini API key set nahi hai. Settings > API Configuration mein apni key dalein.")
        val model = prefs.geminiModel.ifBlank { Prefs.DEFAULT_GEMINI_MODEL }
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", buildPrompt(req))))))
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig",
                        JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", req.voice.localId)))
                    )
            )
        val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 180_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("x-goog-api-key", key)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code != 200) {
                val err = try {
                    conn.errorStream?.bufferedReader()?.readText().orEmpty()
                } catch (e: Exception) {
                    ""
                }
                val msg = try {
                    JSONObject(err).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    err.take(300)
                }
                when (code) {
                    429 -> throw RateLimitException("Gemini free-tier limit poori ho gayi (429). $msg")
                    400, 401, 403 -> throw EngineException("Gemini ne request rad ki ($code) - API key ya model name check karein. $msg")
                    else -> throw EngineException("Gemini server error ($code). $msg")
                }
            }
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val candidates = json.optJSONArray("candidates")
            val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            var data: String? = null
            var mime = ""
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val inline = parts.getJSONObject(i).optJSONObject("inlineData") ?: continue
                    data = inline.optString("data")
                    mime = inline.optString("mimeType")
                    break
                }
            }
            if (data.isNullOrEmpty()) {
                val reason = candidates?.optJSONObject(0)?.optString("finishReason").orEmpty()
                throw EngineException("Gemini ne audio wapas nahi ki${if (reason.isNotEmpty()) " ($reason)" else ""}. Text ya voice badal kar dobara try karein.")
            }
            val rate = Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: 24000
            val bytes = Base64.decode(data, Base64.DEFAULT)
            val samples = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
            Wav.write(out, samples, rate)
        } catch (e: java.io.IOException) {
            throw EngineException("Internet / network masla: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn.disconnect()
        }
    }
}
