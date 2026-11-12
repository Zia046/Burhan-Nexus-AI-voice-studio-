package com.burhannexus.voicestudio.tts

sealed class Segment {
    data class Speech(val text: String, val emotion: String?) : Segment()
    data class Pause(val ms: Int) : Segment()
}

data class ParsedScript(val segments: List<Segment>, val unknownTags: List<String>)

/**
 * Turns a script into an ordered list of speech chunks and pauses.
 *
 * Tags:
 *  - [Happy], [Sad] ... : emotion for the rest of that line
 *  - [Normal]           : back to the project's default emotion
 *  - [Pause: 2 seconds] : real silence of that length (works with every engine)
 */
object TextProcessor {
    val EMOTIONS = listOf(
        "Happy", "Laughing", "Sad", "Crying", "Angry", "Excited", "Surprised", "Romantic",
        "Emotional", "Fearful", "Whispering", "Shouting", "Calm", "Serious", "Motivational",
        "Storytelling", "Sarcastic"
    )

    private val TAG = Regex("\\[([^\\[\\]\\n]{1,40})]")
    private val PAUSE = Regex("^pause\\s*[:=]?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(ms|milliseconds?|s|sec|secs|seconds?)?$", RegexOption.IGNORE_CASE)
    private const val SENTENCE_END = ".!?۔؟"
    private const val SOFT_BREAK = ",،;؛:"

    fun hasUrdu(s: String): Boolean = s.any { it in '؀'..'ۿ' || it in 'ﭐ'..'﷿' || it in 'ﹰ'..'﻿' }
    fun hasLatin(s: String): Boolean = s.any { it in 'A'..'Z' || it in 'a'..'z' }

    fun countWords(s: String): Int = TAG.replace(s, " ").trim().split(Regex("\\s+")).count { it.isNotBlank() }

    fun parse(script: String, maxChars: Int): ParsedScript {
        val segments = ArrayList<Segment>()
        val unknown = ArrayList<String>()
        for (line in script.replace("\r", "").split('\n')) {
            if (line.isBlank()) continue
            var emotion: String? = null
            var pos = 0
            for (m in TAG.findAll(line)) {
                addSpeech(line.substring(pos, m.range.first), emotion, maxChars, segments)
                pos = m.range.last + 1
                val tag = m.groupValues[1].trim()
                val pause = PAUSE.find(tag)
                val known = EMOTIONS.firstOrNull { it.equals(tag, ignoreCase = true) }
                when {
                    pause != null -> {
                        val value = pause.groupValues[1].toDoubleOrNull() ?: 0.0
                        val unit = pause.groupValues[2].lowercase()
                        val ms = if (unit.startsWith("m")) value else value * 1000.0
                        segments.add(Segment.Pause(ms.toInt().coerceIn(50, 60_000)))
                    }
                    known != null -> emotion = known
                    tag.equals("normal", true) || tag.equals("neutral", true) -> emotion = null
                    else -> unknown.add(tag)
                }
            }
            addSpeech(line.substring(pos), emotion, maxChars, segments)
        }
        return ParsedScript(segments, unknown.distinct())
    }

    private fun addSpeech(text: String, emotion: String?, maxChars: Int, out: MutableList<Segment>) {
        val clean = text.trim()
        if (clean.isEmpty() || clean.none { it.isLetterOrDigit() }) return
        for (chunk in splitText(clean, maxChars)) out.add(Segment.Speech(chunk, emotion))
    }

    /** Splits on sentence ends, packing sentences up to [maxChars]; over-long sentences break at commas/spaces. */
    fun splitText(text: String, maxChars: Int): List<String> {
        val sentences = ArrayList<String>()
        val sb = StringBuilder()
        for (i in text.indices) {
            val c = text[i]
            sb.append(c)
            val next = if (i + 1 < text.length) text[i + 1] else ' '
            if (c in SENTENCE_END && (next.isWhitespace() || c == '۔' || c == '؟')) {
                sentences.add(sb.toString().trim())
                sb.setLength(0)
            }
        }
        if (sb.isNotBlank()) sentences.add(sb.toString().trim())

        val chunks = ArrayList<String>()
        val cur = StringBuilder()
        fun flush() {
            if (cur.isNotBlank()) chunks.add(cur.toString().trim())
            cur.setLength(0)
        }
        for (s in sentences) {
            if (s.isBlank()) continue
            if (s.length > maxChars) {
                flush()
                chunks.addAll(hardSplit(s, maxChars))
            } else {
                if (cur.isNotEmpty() && cur.length + 1 + s.length > maxChars) flush()
                if (cur.isNotEmpty()) cur.append(' ')
                cur.append(s)
            }
        }
        flush()
        return chunks.filter { p -> p.any { it.isLetterOrDigit() } }
    }

    private fun hardSplit(s: String, maxChars: Int): List<String> {
        val out = ArrayList<String>()
        var rest = s
        while (rest.length > maxChars) {
            var cut = -1
            for (i in maxChars - 1 downTo maxChars / 2) {
                if (rest[i] in SOFT_BREAK) { cut = i + 1; break }
            }
            if (cut < 0) {
                for (i in maxChars - 1 downTo maxChars / 2) {
                    if (rest[i].isWhitespace()) { cut = i + 1; break }
                }
            }
            if (cut < 0) cut = maxChars
            out.add(rest.substring(0, cut).trim())
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotBlank()) out.add(rest)
        return out
    }
}
