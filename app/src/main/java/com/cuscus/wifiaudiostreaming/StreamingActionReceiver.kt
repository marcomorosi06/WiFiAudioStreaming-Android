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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StreamingActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP_STREAMING -> stopEverything(context)
            ACTION_VOLUME_UP -> shiftVolume(NotificationCenter.VOLUME_STEP)
            ACTION_VOLUME_DOWN -> shiftVolume(-NotificationCenter.VOLUME_STEP)
        }
    }

    private fun stopEverything(context: Context) {
        NetworkManager.stopStreaming(context)
        context.stopService(Intent(context, AudioCaptureService::class.java))
        context.stopService(Intent(context, ClientService::class.java))
        context.stopService(Intent(context, AutoConnectService::class.java))
        NotificationCenter.cancelAll(context)
    }

    private fun shiftVolume(delta: Float) {
        NetworkManager.serverVolume.value =
            NotificationCenter.nudgeVolume(NetworkManager.serverVolume.value, delta)
    }

    companion object {
        const val ACTION_STOP_STREAMING = "com.cuscus.wifiaudiostreaming.ACTION_STOP_STREAMING"
        const val ACTION_VOLUME_UP = "com.cuscus.wifiaudiostreaming.ACTION_VOLUME_UP"
        const val ACTION_VOLUME_DOWN = "com.cuscus.wifiaudiostreaming.ACTION_VOLUME_DOWN"
    }
}