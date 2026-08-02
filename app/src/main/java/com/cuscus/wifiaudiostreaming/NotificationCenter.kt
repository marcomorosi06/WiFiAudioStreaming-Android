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
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat

object NotificationCenter {

    const val ID_SERVER = 101
    const val ID_CLIENT = 201
    const val ID_AUTO_CONNECT = 301

    const val CHANNEL_SERVER = "wfas_server_v3"
    const val CHANNEL_CLIENT = "wfas_client_v3"
    const val CHANNEL_AUTO_CONNECT = "wfas_auto_connect_v3"

    const val MIN_VOLUME = 0.0f
    const val MAX_VOLUME = 2.0f
    const val VOLUME_STEP = 0.1f

    private const val VOLUME_SCALE = 100
    private const val BOOST_SEGMENT = 100

    private const val REQ_OPEN_APP = 0
    private const val REQ_STOP = 1
    private const val REQ_VOLUME_DOWN = 2
    private const val REQ_VOLUME_UP = 3

    private val obsoleteChannels = listOf(
        "audio_stream_channel_v2",
        "client_service_channel_v2",
        "auto_connect_channel"
    )

    fun ensureChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        obsoleteChannels.forEach { manager.deleteNotificationChannel(it) }
        manager.createNotificationChannelsCompat(
            listOf(
                silentChannel(
                    context,
                    CHANNEL_SERVER,
                    R.string.notif_channel_server_name,
                    R.string.notif_channel_server_desc
                ),
                silentChannel(
                    context,
                    CHANNEL_CLIENT,
                    R.string.notif_channel_client_name,
                    R.string.notif_channel_client_desc
                ),
                silentChannel(
                    context,
                    CHANNEL_AUTO_CONNECT,
                    R.string.notif_channel_auto_connect_name,
                    R.string.notif_channel_auto_connect_desc
                )
            )
        )
    }

    private fun silentChannel(
        context: Context,
        id: String,
        nameRes: Int,
        descriptionRes: Int
    ): NotificationChannelCompat =
        NotificationChannelCompat.Builder(id, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(nameRes))
            .setDescription(context.getString(descriptionRes))
            .setShowBadge(false)
            .setVibrationEnabled(false)
            .setLightsEnabled(false)
            .setSound(null, null)
            .build()

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun post(context: Context, id: Int, notification: Notification) {
        if (!canPost(context)) return
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    fun cancelAll(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(ID_SERVER)
        manager.cancel(ID_CLIENT)
        manager.cancel(ID_AUTO_CONNECT)
    }

    fun volumePercent(volume: Float): Int =
        (volume * VOLUME_SCALE).toInt().coerceIn(0, (MAX_VOLUME * VOLUME_SCALE).toInt())

    fun nudgeVolume(current: Float, delta: Float): Float {
        val stepped = Math.round((current + delta) / VOLUME_STEP) * VOLUME_STEP
        return stepped.coerceIn(MIN_VOLUME, MAX_VOLUME)
    }

    fun serverNotification(context: Context, status: String, volume: Float): Notification {
        val percent = volumePercent(volume)
        val muted = percent == 0
        val icon = if (muted) R.drawable.ic_notif_volume_off else R.drawable.ic_notif_stream

        return baseBuilder(context, CHANNEL_SERVER, icon)
            .setContentTitle(context.getString(R.string.notif_server_title))
            .setContentText(status)
            .setSubText(volumeLabel(context, percent))
            .setStyle(volumeStyle(context, percent))
            .setShortCriticalText(
                if (muted) context.getString(R.string.notif_chip_muted) else "$percent%"
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    IconCompat.createWithResource(context, R.drawable.ic_notif_volume_down),
                    context.getString(R.string.notif_action_volume_down),
                    broadcast(context, REQ_VOLUME_DOWN, StreamingActionReceiver.ACTION_VOLUME_DOWN)
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    IconCompat.createWithResource(context, R.drawable.ic_notif_volume_up),
                    context.getString(R.string.notif_action_volume_up),
                    broadcast(context, REQ_VOLUME_UP, StreamingActionReceiver.ACTION_VOLUME_UP)
                ).build()
            )
            .addAction(stopAction(context))
            .build()
    }

    fun clientNotification(context: Context, status: String): Notification =
        baseBuilder(context, CHANNEL_CLIENT, R.drawable.ic_notif_client)
            .setContentTitle(context.getString(R.string.notif_client_title))
            .setContentText(status)
            .setShortCriticalText(context.getString(R.string.notif_chip_live))
            .addAction(stopAction(context))
            .build()

    fun autoConnectNotification(
        context: Context,
        status: String,
        streaming: Boolean
    ): Notification {
        val builder = baseBuilder(context, CHANNEL_AUTO_CONNECT, R.drawable.ic_notif_radar)
            .setContentTitle(context.getString(R.string.notif_auto_connect_title))
            .setContentText(status)
            .setShortCriticalText(
                context.getString(
                    if (streaming) R.string.notif_chip_live else R.string.notif_chip_scanning
                )
            )

        if (streaming) builder.addAction(stopAction(context))

        return builder.build()
    }

    private fun baseBuilder(
        context: Context,
        channelId: String,
        smallIcon: Int
    ): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setColor(ContextCompat.getColor(context, R.color.notif_accent))
            .setContentIntent(openApp(context))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setShowWhen(false)
            .setRequestPromotedOngoing(true)

    private fun volumeStyle(context: Context, percent: Int): NotificationCompat.ProgressStyle =
        NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(percent)
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(VOLUME_SCALE)
                        .setColor(ContextCompat.getColor(context, R.color.notif_volume_primary)),
                    NotificationCompat.ProgressStyle.Segment(BOOST_SEGMENT)
                        .setColor(ContextCompat.getColor(context, R.color.notif_volume_boost))
                )
            )

    private fun volumeLabel(context: Context, percent: Int): String =
        if (percent == 0) {
            context.getString(R.string.notif_volume_muted)
        } else {
            context.getString(R.string.notif_volume, percent)
        }

    private fun stopAction(context: Context): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, R.drawable.ic_notif_stop),
            context.getString(R.string.notif_action_stop),
            broadcast(context, REQ_STOP, StreamingActionReceiver.ACTION_STOP_STREAMING)
        ).build()

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            REQ_OPEN_APP,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun broadcast(context: Context, requestCode: Int, action: String): PendingIntent {
        val intent = Intent(context, StreamingActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
