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

package com.cuscus.wifiaudiostreaming.scripting

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cuscus.wifiaudiostreaming.MainActivity
import com.cuscus.wifiaudiostreaming.NetworkManager
import com.cuscus.wifiaudiostreaming.data.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScriptCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val command = ScriptCommand.fromIntent(intent) ?: return
        val appContext = context.applicationContext

        when (command.action) {
            ScriptActionType.STOP -> ScriptExecutor.stop(appContext)

            ScriptActionType.SET -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        ScriptExecutor.applySet(appContext, command)
                    } finally {
                        pending.finish()
                    }
                }
            }

            ScriptActionType.CONNECT -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        ScriptExecutor.connect(appContext, command)
                    } finally {
                        pending.finish()
                    }
                }
            }

            ScriptActionType.TOGGLE -> {
                if (NetworkManager.isStreamingCurrent.value) {
                    ScriptExecutor.stop(appContext)
                } else {
                    handleServerStart(appContext, command)
                }
            }

            ScriptActionType.START_SERVER -> handleServerStart(appContext, command)
        }
    }

    private fun handleServerStart(context: Context, command: ScriptCommand) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = SettingsDataStore(context)
                val settings = store.settingsFlow.first()
                ScriptExecutor.persistSecurityIfPresent(store, settings, command)
                val resolved = ScriptExecutor.resolveServerParams(settings, command)
                if (resolved.streamInternal) {
                    launchActivityForProjection(context, command)
                } else if (resolved.streamMic) {
                    ScriptExecutor.startServerMicOnly(context, resolved)
                } else {
                    launchActivityForProjection(context, command)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun launchActivityForProjection(context: Context, command: ScriptCommand) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(command.toUri())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }
}
