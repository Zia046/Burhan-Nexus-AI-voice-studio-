package com.burhannexus.voicestudio.audio

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Mono 16-bit PCM audio held in memory. */
class Pcm(val samples: ShortArray, val sampleRate: Int) {
    val durationMs: Long get() = if (sampleRate == 0) 0 else samples.size * 1000L / sampleRate
}

/** Streaming writer for a mono 16-bit PCM WAV file. */
class WavWriter(file: File, val sampleRate: Int) : Closeable {
    private val raf = RandomAccessFile(file, "rw")
    private var dataBytes = 0L

    init {
        raf.setLength(0)
        raf.write(ByteArray(44))
    }

    val frames: Long get() = dataBytes / 2

    fun append(samples: ShortArray, count: Int = samples.size) {
        if (count <= 0) return
        val bb = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.asShortBuffer().put(samples, 0, count)
        raf.write(bb.array())
        dataBytes += count * 2L
    }

    override fun close() {
        raf.seek(0)
        raf.write(Wav.header(sampleRate, dataBytes))
        raf.close()
    }
}

/** Streaming reader for 16-bit PCM WAV files (mono, or stereo down-mixed to mono). */
class WavReader(file: File) : Closeable {
    private val input: InputStream = BufferedInputStream(FileInputStream(file), 1 shl 16)
    var sampleRate = 0
        private set
    var channels = 1
        private set
    private var remaining = 0L

    init {
        try {
            val riff = readBytes(12)
            if (String(riff, 0, 4, Charsets.US_ASCII) != "RIFF" || String(riff, 8, 4, Charsets.US_ASCII) != "WAVE") {
                throw IOException("Not a WAV file")
            }
            var found = false
            while (!found) {
                val head = readBytes(8)
                val id = String(head, 0, 4, Charsets.US_ASCII)
                val size = ByteBuffer.wrap(head, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                when (id) {
                    "fmt " -> {
                        val fmt = readBytes(size.toInt())
                        val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                        val format = bb.short.toInt() and 0xFFFF
                        channels = bb.short.toInt()
                        sampleRate = bb.int
                        bb.int
                        bb.short
                        val bits = bb.short.toInt()
                        if ((format != 1 && format != 0xFFFE) || bits != 16 || channels !in 1..2) {
                            throw IOException("Unsupported WAV format (need 16-bit PCM)")
                        }
                        if (size % 2L == 1L) skip(1)
                    }
                    "data" -> {
                        remaining = if (size == 0L || size == 0xFFFFFFFFL) Long.MAX_VALUE else size
                        found = true
                    }
                    else -> skip(size + (size % 2L))
                }
            }
            if (sampleRate <= 0) throw IOException("WAV has no fmt chunk")
        } catch (e: Exception) {
            input.close()
            throw e
        }
    }

    private fun readBytes(n: Int): ByteArray {
        val b = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(b, off, n - off)
            if (r < 0) throw IOException("Unexpected end of WAV file")
            off += r
        }
        return b
    }

    private fun skip(n: Long) {
        var left = n
        while (left > 0) {
            val s = input.skip(left)
            if (s <= 0) {
                if (input.read() < 0) throw IOException("Unexpected end of WAV file")
                left -= 1
            } else left -= s
        }
    }

    private var raw = ByteArray(0)

    /** Reads up to buf.size mono samples. Returns the count, or -1 at end of data. */
    fun read(buf: ShortArray): Int {
        if (remaining <= 0) return -1
        val frameBytes = 2 * channels
        val want = min(buf.size.toLong() * frameBytes, remaining).toInt()
        if (raw.size < want) raw = ByteArray(want)
        var off = 0
        while (off < want) {
            val r = input.read(raw, off, want - off)
            if (r < 0) break
            off += r
        }
        if (off < frameBytes) {
            remaining = 0
            return -1
        }
        val frames = off / frameBytes
        remaining -= (frames * frameBytes).toLong()
        if (off < want) remaining = 0
        val bb = ByteBuffer.wrap(raw, 0, frames * frameBytes).order(ByteOrder.LITTLE_ENDIAN)
        if (channels == 1) {
            bb.asShortBuffer().get(buf, 0, frames)
        } else {
            for (i in 0 until frames) {
                val l = bb.short.toInt()
                val r = bb.short.toInt()
                buf[i] = ((l + r) / 2).toShort()
            }
        }
        return frames
    }

    override fun close() = input.close()
}

object Wav {
    fun header(sampleRate: Int, dataBytes: Long): ByteArray {
        val bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
        bb.putInt((36 + dataBytes).toInt())
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII))
        bb.putInt(16)
        bb.putShort(1)
        bb.putShort(1)
        bb.putInt(sampleRate)
        bb.putInt(sampleRate * 2)
        bb.putShort(2)
        bb.putShort(16)
        bb.put("data".toByteArray(Charsets.US_ASCII))
        bb.putInt(dataBytes.toInt())
        return bb.array()
    }

    fun write(file: File, samples: ShortArray, sampleRate: Int) {
        WavWriter(file, sampleRate).use { it.append(samples) }
    }

    fun read(file: File): Pcm {
        WavReader(file).use { r ->
            var out = ShortArray(1 shl 16)
            var n = 0
            val buf = ShortArray(8192)
            while (true) {
                val got = r.read(buf)
                if (got < 0) break
                if (n + got > out.size) out = out.copyOf(max(out.size * 2, n + got))
                System.arraycopy(buf, 0, out, n, got)
                n += got
            }
            return Pcm(out.copyOf(n), r.sampleRate)
        }
    }

    fun durationMs(file: File): Long = try {
        WavReader(file).use { r ->
            val bytes = file.length() - 44
            if (r.sampleRate <= 0) 0 else bytes * 1000L / (r.sampleRate.toLong() * 2 * r.channels)
        }
    } catch (e: Exception) {
        0
    }
}

/** Small DSP helpers used when stitching chunks together. */
object Dsp {
    fun floatToPcm(samples: FloatArray): ShortArray {
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            val v = (samples[i] * 32767f).toInt()
            out[i] = max(-32768, min(32767, v)).toShort()
        }
        return out
    }

    fun silence(ms: Int, sampleRate: Int): ShortArray = ShortArray((sampleRate.toLong() * ms / 1000L).toInt())

    /** Linear-interpolation resampler; good enough for speech when rates differ between engines. */
    fun resample(input: ShortArray, from: Int, to: Int): ShortArray {
        if (from == to || input.isEmpty()) return input
        val outLen = (input.size.toLong() * to / from).toInt()
        val out = ShortArray(outLen)
        val ratio = from.toDouble() / to.toDouble()
        for (i in 0 until outLen) {
            val pos = i * ratio
            val i0 = pos.toInt()
            val i1 = min(i0 + 1, input.size - 1)
            val frac = pos - i0
            out[i] = (input[i0] * (1 - frac) + input[i1] * frac).toInt().toShort()
        }
        return out
    }

    /** Removes leading/trailing near-silence but keeps [keepMs] of padding, so joins sound natural. */
    fun trimSilence(input: ShortArray, sampleRate: Int, keepMs: Int = 50, threshold: Int = 350): ShortArray {
        if (input.isEmpty()) return input
        var start = 0
        while (start < input.size && abs(input[start].toInt()) < threshold) start++
        if (start >= input.size) return input
        var end = input.size - 1
        while (end > start && abs(input[end].toInt()) < threshold) end--
        val keep = sampleRate * keepMs / 1000
        val s = max(0, start - keep)
        val e = min(input.size, end + 1 + keep)
        return input.copyOfRange(s, e)
    }

    /** Peak-normalises a chunk so different chunks have matching loudness. */
    fun normalize(input: ShortArray, targetPeak: Float = 0.89f): ShortArray {
        var peak = 0
        for (s in input) {
            val a = abs(s.toInt())
            if (a > peak) peak = a
        }
        if (peak < 500) return input
        val gain = min(4f, targetPeak * 32767f / peak)
        if (abs(gain - 1f) < 0.03f) return input
        val out = ShortArray(input.size)
        for (i in input.indices) {
            val v = (input[i] * gain).toInt()
            out[i] = max(-32768, min(32767, v)).toShort()
        }
        return out
    }
}
