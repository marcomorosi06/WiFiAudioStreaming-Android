package com.cuscus.wifiaudiostreaming

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StreamingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.cuscus.wifiaudiostreaming.ACTION_STOP_STREAMING") {
            NetworkManager.stopStreaming(context)
            context.stopService(Intent(context, AudioCaptureService::class.java))
            context.stopService(Intent(context, ClientService::class.java))
            context.stopService(Intent(context, AutoConnectService::class.java))
        }
    }
}