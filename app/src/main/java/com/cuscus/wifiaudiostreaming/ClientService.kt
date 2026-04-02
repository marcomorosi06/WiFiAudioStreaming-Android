package com.cuscus.wifiaudiostreaming

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cuscus.wifiaudiostreaming.NetworkManager.updateWidgetState
import kotlinx.coroutines.*

class ClientService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val notificationId = 201
    private val channelId = "client_service_channel_v2"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!checkNotificationPermission()) {
            Toast.makeText(this, "Notifications permission missing", Toast.LENGTH_SHORT).show()
            stopSelf()
            return START_NOT_STICKY
        }
        serviceScope.launch { updateWidgetState(this@ClientService, true, false) }

        createNotificationChannel()
        startForeground(notificationId, buildNotification("Connecting..."))

        serviceScope.launch {
            NetworkManager.connectionStatus.collect { status ->
                val notif = buildNotification(status)
                NotificationManagerCompat.from(this@ClientService).notify(notificationId, notif)
            }
        }

        return START_STICKY
    }

    private fun checkNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Audio Reception Client",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, StreamingActionReceiver::class.java).apply {
            action = "com.cuscus.wifiaudiostreaming.ACTION_STOP_STREAMING"
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ricezione Audio")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)

        if (Build.VERSION.SDK_INT >= 36) {
            builder.extras.putBoolean("android.app.extra.REQUEST_PROMOTED_ONGOING", true)
        }

        return builder.build()
    }

    override fun onDestroy() {
        CoroutineScope(Dispatchers.IO).launch {
            updateWidgetState(this@ClientService, false, false)
        }
        serviceScope.cancel()
        super.onDestroy()
    }
}