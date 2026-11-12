package com.burhannexus.voicestudio.gen

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.burhannexus.voicestudio.App
import com.burhannexus.voicestudio.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps generation alive (and the CPU awake) while the app is in the background. */
class GenerationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(text: String, progress: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, App.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Burhan Nexus AI Voice Studio")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .setContentIntent(open)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // 1 = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Audio ban rahi hai...", 0), 1)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "voicestudio:generation").apply {
                try {
                    acquire(2 * 60 * 60 * 1000L)
                } catch (_: Exception) {
                }
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            scope.launch {
                App.instance.generator.state.collect { s ->
                    if (!s.active) {
                        stopSelf()
                    } else {
                        try {
                            manager.notify(NOTIFICATION_ID, notification("${s.percent}% - ${s.message}", s.percent))
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 41
    }
}
