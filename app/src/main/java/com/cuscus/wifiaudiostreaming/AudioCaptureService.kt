/*
 * Copyright (c) 2026 Marco Morosi
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package com.cuscus.wifiaudiostreaming

import android.Manifest
import android.annotation.SuppressLint
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
import com.cuscus.wifiaudiostreaming.NetworkManager.updateWidgetState
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
                serviceScope.launch { updateWidgetState(this@AudioCaptureService, true, true) }

                val streamInternal = intent.getBooleanExtra(EXTRA_STREAM_INTERNAL, false)
                val streamMic = intent.getBooleanExtra(EXTRA_STREAM_MIC, false)
                val sampleRate = intent.getIntExtra("sample_rate", 48000)
                val channelConfig = intent.getStringExtra("channel_config") ?: "STEREO"
                val bufferSize = intent.getIntExtra("buffer_size", 6144)
                val isMulticast = intent.getBooleanExtra(EXTRA_IS_MULTICAST, true)

                val streamingPort = intent.getIntExtra("streaming_port", 9090)
                val networkInterfaceName = intent.getStringExtra("network_interface") ?: "Auto"
                val rtpEnabled = intent.getBooleanExtra("rtp_enabled", false)
                val rtpPort = intent.getIntExtra("rtp_port", 9094)
                val httpEnabled = intent.getBooleanExtra("http_enabled", false)
                val httpPort = intent.getIntExtra("http_port", 8080)
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)

                if (streamInternal && data != null) {
                    val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = projectionManager.getMediaProjection(resultCode, data)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                            override fun onStop() {
                                stopCapture()
                            }
                        }, android.os.Handler(android.os.Looper.getMainLooper()))
                    }
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
                        streamingPort = streamingPort,
                        networkInterfaceName = networkInterfaceName,
                        rtpEnabled = rtpEnabled,
                        rtpPort = rtpPort,
                        httpEnabled = httpEnabled,
                        httpPort = httpPort,
                        onClientDisconnected = { stopCapture() }
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

        CoroutineScope(Dispatchers.IO).launch {
            updateWidgetState(this@AudioCaptureService, false, true)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    private fun startForegroundWithNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Streaming",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

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

        serviceScope.launch {
            NetworkManager.connectionStatus.collect { status ->
                val updatedNotification = buildNotification(status)
                NotificationManagerCompat.from(this@AudioCaptureService)
                    .notify(SERVICE_ID, updatedNotification)
            }
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, StreamingActionReceiver::class.java).apply {
            action = "com.cuscus.wifiaudiostreaming.ACTION_STOP_STREAMING"
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Server Audio")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)

        if (Build.VERSION.SDK_INT >= 36) {
            builder.extras.putBoolean("android.app.extra.REQUEST_PROMOTED_ONGOING", true)
        }

        return builder.build()
    }

    companion object {
        const val ACTION_START = "com.cuscus.wifiaudiostreamer.ACTION_START"
        const val ACTION_STOP = "com.cuscus.wifiaudiostreamer.ACTION_STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val EXTRA_STREAM_INTERNAL = "stream_internal"
        const val EXTRA_STREAM_MIC = "stream_mic"
        const val EXTRA_IS_MULTICAST = "is_multicast"
        private const val SERVICE_ID = 101
        private const val CHANNEL_ID = "audio_stream_channel_v2"
    }

    override fun onDestroy() {
        stopCapture()
        serviceScope.cancel()
        serviceScope.launch { updateWidgetState(this@AudioCaptureService, false, true) }
        super.onDestroy()
    }
}