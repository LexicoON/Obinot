package com.obinot.app.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.obinot.app.BinotApplication
import com.obinot.app.MainActivity
import com.obinot.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service que mantiene la grabación viva cuando la pantalla se apaga
 * o la app pasa a segundo plano.
 *
 * NO posee su propio MediaRecorder — [AudioRecorderManager] es el que graba. Este servicio:
 *  1) Sostiene un foreground service de tipo "microphone" para que Android no corte el acceso al mic.
 *  2) Muestra una notificación persistente con el tiempo transcurrido.
 *
 * Se inicia cuando empieza una grabación (solo si el toggle "Record in background" está activo)
 * y se detiene solo cuando AudioRecorderManager reporta que ya no está grabando.
 */
class RecordingService : Service() {

    companion object {
        private const val TAG = "RecordingService"
        private const val CHANNEL_ID = "obinot_recording_channel"
        private const val NOTIFICATION_ID = 4821

        const val ACTION_START = "com.obinot.app.action.START_BACKGROUND_RECORDING"
        const val ACTION_STOP = "com.obinot.app.action.STOP_BACKGROUND_RECORDING"
        const val ACTION_TOGGLE_PAUSE = "com.obinot.app.action.TOGGLE_PAUSE_RECORDING"

        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply { action = ACTION_START }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply { action = ACTION_STOP }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop service", e)
            }
        }
    }

    private var watcherJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var elapsedSeconds = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelfCleanly()
                return START_NOT_STICKY
            }
            else -> {
                beginWatching()
            }
        }
        return START_NOT_STICKY
    }

    private fun beginWatching() {
        if (watcherJob != null) return // Ya está corriendo

        elapsedSeconds = 0

        try {
            val notification = buildNotification(elapsedSeconds)
            // ServiceCompat maneja las diferencias entre API levels sin ramas manuales
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0
                }
            )
            Log.d(TAG, "startForeground successful")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.javaClass.simpleName}: ${e.message}", e)
            stopSelfCleanly()
            return
        }

        val audioRecorderManager = (applicationContext as BinotApplication).container.audioRecorderManager

        watcherJob = serviceScope.launch {
            while (true) {
                delay(1000)
                if (!audioRecorderManager.isRecording.value) {
                    stopSelfCleanly()
                    break
                }
                elapsedSeconds += 1
                // El cronómetro de la notificación corre solo; refrescamos rara vez.
                if (elapsedSeconds % 30 == 0) updateNotification(elapsedSeconds)
            }
        }
    }

    private fun stopSelfCleanly() {
        watcherJob?.cancel()
        watcherJob = null
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "stopForeground", e)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Background Recording",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows recording progress while Obinot records with the screen off or the app in the background."
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(seconds: Int): Notification {
        val openAppIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Visual: cronómetro nativo del sistema (se actualiza solo, sin parpadeo),
        // acento con el color de la app y subtexto en vez de texto plano.
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Recording")
            .setContentText("Tap to return to Obinot")
            .setSubText("Obinot")
            .setSmallIcon(R.drawable.ic_recording_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis() - seconds * 1000L)
            .setUsesChronometer(true)
            .setColorized(true)
            .setColor(0xFFB3261E.toInt())
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(seconds: Int) {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, buildNotification(seconds))
        } catch (e: Exception) {
            Log.w(TAG, "updateNotification", e)
        }
    }

    private fun formatTime(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        watcherJob?.cancel()
        watcherJob = null
    }
}