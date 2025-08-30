package com.example.wifiaudiostreaming

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AudioCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundWithNotification()

                val streamInternal = intent.getBooleanExtra(EXTRA_STREAM_INTERNAL, false)
                val streamMic = intent.getBooleanExtra(EXTRA_STREAM_MIC, false)
                val sampleRate = intent.getIntExtra("sample_rate", 48000)
                val channelConfig = intent.getStringExtra("channel_config") ?: "STEREO"
                val bufferSize = intent.getIntExtra("buffer_size", 6144)
                val isMulticast = intent.getBooleanExtra(EXTRA_IS_MULTICAST, true)
                // MODIFICATO: Leggi la porta dall'intent, con un default di 9090
                val streamingPort = intent.getIntExtra("streaming_port", 9090)

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)

                if (streamInternal && data != null) {
                    val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = projectionManager.getMediaProjection(resultCode, data)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    NetworkManager.startServerAudio(
                        context = this,
                        projection = mediaProjection,
                        streamInternal = streamInternal,
                        streamMic = streamMic,
                        sampleRate = sampleRate,
                        channelConfig = channelConfig,
                        bufferSize = bufferSize,
                        isMulticast = isMulticast,
                        streamingPort = streamingPort // <-- PASSA LA NUOVA PORTA
                    )
                }
            }
            ACTION_STOP -> stopCapture()
        }
        return START_STICKY
    }

    private fun stopCapture() {
        NetworkManager.stopStreaming(this)
        mediaProjection?.stop()
        mediaProjection = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundWithNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Streaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Notifiche per il servizio di streaming audio" }
            manager.createNotificationChannel(channel)
        }

        // Avvio con stato iniziale
        val initialNotification = buildNotification("Avvio del servizio...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                SERVICE_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(SERVICE_ID, initialNotification)
        }

        // Aggiorna dinamicamente la notifica con lo stato di NetworkManager
        serviceScope.launch {
            NetworkManager.connectionStatus.collect { status ->
                val updatedNotification = buildNotification(status)
                NotificationManagerCompat.from(this@AudioCaptureService)
                    .notify(SERVICE_ID, updatedNotification)
            }
        }
    }

    private fun buildNotification(statusText: String): Notification {
        // Intent per aprire l’app
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent per fermare lo streaming
        val stopIntent = Intent(this, AudioCaptureService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Audio Streaming")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setOngoing(true)

        // Se il server è "in attesa", mostri progress indeterminato
        if (statusText.contains("attesa", ignoreCase = true)) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(0, 0, false)
        }

        return builder.build()
    }

    override fun onDestroy() {
        stopCapture()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.wifiaudiostreamer.ACTION_START"
        const val ACTION_STOP = "com.example.wifiaudiostreamer.ACTION_STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val EXTRA_STREAM_INTERNAL = "stream_internal"
        const val EXTRA_STREAM_MIC = "stream_mic"
        const val EXTRA_IS_MULTICAST = "is_multicast"
        private const val SERVICE_ID = 101
        private const val CHANNEL_ID = "audio_stream_channel"
    }
}
