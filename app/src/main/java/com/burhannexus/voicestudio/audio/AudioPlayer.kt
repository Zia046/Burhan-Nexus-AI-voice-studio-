package com.burhannexus.voicestudio.audio

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** One shared player; [playing] holds the path that is currently playing (or ""). */
class AudioPlayer {
    private var mp: MediaPlayer? = null
    private val _playing = MutableStateFlow("")
    val playing: StateFlow<String> = _playing

    fun toggle(file: File) {
        if (_playing.value == file.path) stop() else play(file)
    }

    fun play(file: File) {
        stop()
        if (!file.exists()) return
        try {
            val p = MediaPlayer()
            p.setDataSource(file.path)
            p.setOnCompletionListener { stop() }
            p.setOnErrorListener { _, _, _ -> stop(); true }
            p.prepare()
            p.start()
            mp = p
            _playing.value = file.path
        } catch (e: Exception) {
            stop()
        }
    }

    fun stop() {
        try {
            mp?.release()
        } catch (_: Exception) {
        }
        mp = null
        _playing.value = ""
    }
}
