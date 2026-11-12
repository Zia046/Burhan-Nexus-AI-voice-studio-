package com.burhannexus.voicestudio

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.burhannexus.voicestudio.audio.AudioPlayer
import com.burhannexus.voicestudio.data.AppDb
import com.burhannexus.voicestudio.data.Prefs
import com.burhannexus.voicestudio.gen.GenerationManager
import com.burhannexus.voicestudio.model.ModelManager
import com.burhannexus.voicestudio.tts.EngineRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var prefs: Prefs
    lateinit var db: AppDb
    lateinit var models: ModelManager
    lateinit var engines: EngineRegistry
    lateinit var generator: GenerationManager
    val player = AudioPlayer()

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        db = AppDb.build(this)
        models = ModelManager(this, scope)
        engines = EngineRegistry(this, prefs, models)
        generator = GenerationManager(this)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Audio generation", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val CHANNEL_ID = "generation"
        lateinit var instance: App
            private set
    }
}
