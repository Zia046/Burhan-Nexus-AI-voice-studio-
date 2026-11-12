package com.burhannexus.voicestudio

import com.burhannexus.voicestudio.audio.Dsp
import com.burhannexus.voicestudio.audio.Wav
import com.burhannexus.voicestudio.audio.WavWriter
import com.burhannexus.voicestudio.tts.Segment
import com.burhannexus.voicestudio.tts.TextProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CoreTest {

    @Test
    fun emotionTagsApplyToTheirLine() {
        val script = "[Happy] آج ہمارے لیے بہت خوشی کا دن ہے!\n[Sad] مجھے اس بات کا بہت افسوس ہے۔\nعام جملہ۔"
        val parsed = TextProcessor.parse(script, 220)
        val speech = parsed.segments.filterIsInstance<Segment.Speech>()
        assertEquals(3, speech.size)
        assertEquals("Happy", speech[0].emotion)
        assertEquals("Sad", speech[1].emotion)
        assertEquals(null, speech[2].emotion)
        assertTrue(parsed.unknownTags.isEmpty())
    }

    @Test
    fun pauseTagBecomesSilence() {
        val parsed = TextProcessor.parse("Hello there. [Pause: 2 seconds] Next line. [pause 500ms] End.", 220)
        val pauses = parsed.segments.filterIsInstance<Segment.Pause>()
        assertEquals(listOf(2000, 500), pauses.map { it.ms })
        assertEquals(5, parsed.segments.size)
    }

    @Test
    fun unknownTagsAreReportedAndRemoved() {
        val parsed = TextProcessor.parse("[Robot] Hello world.", 220)
        assertEquals(listOf("Robot"), parsed.unknownTags)
        assertEquals("Hello world.", (parsed.segments.single() as Segment.Speech).text)
    }

    @Test
    fun longScriptIsSplitWithoutLosingText() {
        val sentence = "یہ ایک آزمائشی جملہ ہے جو بار بار دہرایا جا رہا ہے۔ "
        val script = sentence.repeat(200)          // ~10,000 characters, roughly ten minutes of speech
        val parsed = TextProcessor.parse(script, 220)
        val speech = parsed.segments.filterIsInstance<Segment.Speech>()
        assertTrue(speech.size > 30)
        assertTrue(speech.all { it.text.length <= 220 })
        val original = script.filter { !it.isWhitespace() }
        val rebuilt = speech.joinToString("") { it.text }.filter { !it.isWhitespace() }
        assertEquals(original, rebuilt)
    }

    @Test
    fun overlongSentenceIsHardSplit() {
        val text = "word ".repeat(200).trim()
        val chunks = TextProcessor.splitText(text, 120)
        assertTrue(chunks.all { it.length <= 120 })
        assertEquals(200, chunks.sumOf { it.split(" ").size })
    }

    @Test
    fun urduDetection() {
        assertTrue(TextProcessor.hasUrdu("Hello سلام"))
        assertTrue(!TextProcessor.hasUrdu("Hello"))
        assertEquals(3, TextProcessor.countWords("[Happy] one two three"))
    }

    @Test
    fun wavRoundTripAndStreamingJoin() {
        val a = ShortArray(24000) { (it % 100 * 300).toShort() }
        val b = ShortArray(12000) { (-it % 50 * 200).toShort() }
        val f1 = File.createTempFile("chunk", ".wav")
        val joined = File.createTempFile("joined", ".wav")
        try {
            Wav.write(f1, a, 24000)
            val read = Wav.read(f1)
            assertEquals(24000, read.sampleRate)
            assertTrue(a.contentEquals(read.samples))
            WavWriter(joined, 24000).use { w ->
                w.append(a)
                w.append(Dsp.silence(500, 24000))
                w.append(b)
            }
            val all = Wav.read(joined)
            assertEquals(24000 + 12000 + 12000, all.samples.size)
            assertEquals(2000L, all.durationMs)
            assertEquals(44L + all.samples.size * 2L, joined.length())
        } finally {
            f1.delete()
            joined.delete()
        }
    }

    @Test
    fun dspHelpers() {
        val quietEdges = ShortArray(22050) { i -> if (i in 8000..12000) 9000 else 0 }
        val trimmed = Dsp.trimSilence(quietEdges, 22050)
        assertTrue(trimmed.size < 7000 && trimmed.size >= 4001)
        val normalized = Dsp.normalize(trimmed)
        assertTrue(normalized.maxOf { it.toInt() } in 28000..30000)
        assertEquals(48000, Dsp.resample(ShortArray(24000), 24000, 48000).size)
        assertEquals(24000, Dsp.resample(ShortArray(22050), 22050, 24000).size)
    }
}
