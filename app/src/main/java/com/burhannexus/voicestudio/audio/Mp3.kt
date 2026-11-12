package com.burhannexus.voicestudio.audio

import com.naman14.androidlame.AndroidLame
import com.naman14.androidlame.LameBuilder
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/** WAV -> MP3 using the LAME encoder (via TAndroidLame). */
object Mp3 {
    fun encode(wav: File, mp3: File, bitrateKbps: Int) {
        WavReader(wav).use { reader ->
            val lame: AndroidLame = LameBuilder()
                .setInSampleRate(reader.sampleRate)
                .setOutChannels(1)
                .setOutBitrate(bitrateKbps)
                .setOutSampleRate(reader.sampleRate)
                .setQuality(5)
                .build()
            try {
                BufferedOutputStream(FileOutputStream(mp3)).use { out ->
                    val pcm = ShortArray(8192)
                    val buf = ByteArray((1.25 * pcm.size + 7200).toInt())
                    while (true) {
                        val n = reader.read(pcm)
                        if (n < 0) break
                        if (n == 0) continue
                        val encoded = lame.encode(pcm, pcm, n, buf)
                        if (encoded > 0) out.write(buf, 0, encoded)
                    }
                    val tail = lame.flush(buf)
                    if (tail > 0) out.write(buf, 0, tail)
                }
            } finally {
                lame.close()
            }
        }
    }
}
