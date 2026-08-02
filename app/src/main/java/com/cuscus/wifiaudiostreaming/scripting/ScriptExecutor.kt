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

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import com.cuscus.wifiaudiostreaming.AudioCaptureService
import com.cuscus.wifiaudiostreaming.ClientService
import com.cuscus.wifiaudiostreaming.NetworkManager
import com.cuscus.wifiaudiostreaming.SecurityMode
import com.cuscus.wifiaudiostreaming.UsbLink
import com.cuscus.wifiaudiostreaming.WfasPolicy
import com.cuscus.wifiaudiostreaming.ServerInfo
import com.cuscus.wifiaudiostreaming.data.AppSettings
import com.cuscus.wifiaudiostreaming.data.SettingsDataStore
import kotlinx.coroutines.flow.first

object ScriptExecutor {

    // I comandi possono pilotare il collegamento USB senza passare dalle
    // impostazioni: applicato prima di aprire qualsiasi socket, altrimenti
    // la scelta dell'interfaccia sarebbe gia' stata fatta.
    fun applyLinkOverrides(settings: AppSettings, command: ScriptCommand, context: Context) {
        val usb = command.bool(ScriptParams.USB) ?: settings.usbModeEnabled
        val latency = command.int(ScriptParams.USBLATENCY) ?: settings.usbLatencyMs
        UsbLink.configure(context.applicationContext, usb, latency)
        WfasPolicy.configure(resolveWfasMode(command) ?: settings.wfasMode)
    }

    fun resolveWfasMode(command: ScriptCommand): String? = when (
        command.str(ScriptParams.WFASMODE)?.lowercase()?.replace("_", "-")
    ) {
        "always" -> WfasPolicy.MODE_ALWAYS
        "not-on-usb", "notonusb", "offonusb" -> WfasPolicy.MODE_OFF_ON_USB
        "off" -> WfasPolicy.MODE_OFF
        else -> null
    }

    suspend fun applyUsbAction(context: Context, command: ScriptCommand) {
        val store = SettingsDataStore(context.applicationContext)
        val settings = store.settingsFlow.first()
        val enable = command.bool(ScriptParams.USB) ?: !settings.usbModeEnabled
        store.saveUsbMode(enable)
        command.int(ScriptParams.USBLATENCY)?.let { store.saveUsbLatency(it) }
        resolveWfasMode(command)?.let { store.saveWfasMode(it) }
        UsbLink.configure(
            context.applicationContext,
            enable,
            command.int(ScriptParams.USBLATENCY) ?: settings.usbLatencyMs
        )
    }

    // Con usb=true e nessun IP il target e' il peer trovato sul cavo: il suo
    // indirizzo cambia a ogni sessione di tethering, quindi non si puo' fissare
    // in uno script.
    suspend fun resolveUsbPeer(timeoutMs: Long = 6000): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            NetworkManager.discoveredDevices.value.values
                .firstOrNull { it.viaUsb }?.let { return it.ip }
            kotlinx.coroutines.delay(300)
        }
        return null
    }

    private fun resolveAuthMode(command: ScriptCommand, fallback: String): String = when {
        command.str(ScriptParams.AUTHMODE) != null ->
            SecurityMode.storedMode(command.str(ScriptParams.AUTHMODE).orEmpty())
        command.str(ScriptParams.AUTHKEY) != null -> SecurityMode.KEY.name
        else -> fallback
    }

    suspend fun persistSecurityIfPresent(
        store: SettingsDataStore,
        current: AppSettings,
        command: ScriptCommand
    ) {
        val authMode = command.str(ScriptParams.AUTHMODE)
        val authKey = command.str(ScriptParams.AUTHKEY)
        if (authMode == null && authKey == null) return
        val resolvedKey = authKey ?: current.authKey
        store.saveSecurity(resolveAuthMode(command, current.securityMode), resolvedKey)
        store.saveQrPairing(false)
        if (authKey != null) store.saveManualAuthKey(resolvedKey)
    }

    fun resolveServerParams(settings: AppSettings, command: ScriptCommand): ResolvedServerParams {
        val rtpEnabled = command.bool(ScriptParams.RTP) ?: settings.rtpEnabled
        val httpEnabled = command.bool(ScriptParams.HTTP) ?: settings.httpEnabled
        val multicast = command.bool(ScriptParams.MULTICAST)
            ?: (settings.lastMulticastMode || rtpEnabled || httpEnabled)
        return ResolvedServerParams(
            streamInternal = command.bool(ScriptParams.INTERNAL) ?: settings.streamInternal,
            streamMic = command.bool(ScriptParams.MIC) ?: settings.streamMic,
            sampleRate = command.int(ScriptParams.SAMPLERATE) ?: settings.sampleRate,
            channelConfig = command.str(ScriptParams.CHANNELS)?.uppercase() ?: settings.channelConfig,
            bufferSize = command.int(ScriptParams.BUFFER) ?: settings.bufferSize,
            isMulticast = multicast,
            streamingPort = command.int(ScriptParams.PORT) ?: settings.streamingPort,
            networkInterface = command.str(ScriptParams.IFACE) ?: settings.networkInterface,
            rtpEnabled = rtpEnabled,
            rtpPort = command.int(ScriptParams.RTPPORT) ?: settings.rtpPort,
            httpEnabled = httpEnabled,
            httpPort = command.int(ScriptParams.HTTPPORT) ?: settings.httpPort,
            dlnaEnabled = settings.dlnaEnabled,
            dlnaPort = settings.dlnaPort,
            dlnaFormat = settings.dlnaFormat,
            dlnaDevices = settings.dlnaDevices,
            usbMode = command.bool(ScriptParams.USB) ?: settings.usbModeEnabled,
            usbLatencyMs = command.int(ScriptParams.USBLATENCY) ?: settings.usbLatencyMs
        )
    }

    fun startServerMicOnly(context: Context, params: ResolvedServerParams) {
        UsbLink.configure(context.applicationContext, params.usbMode, params.usbLatencyMs)
        val intent = Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_START
            putExtra(AudioCaptureService.EXTRA_STREAM_INTERNAL, false)
            putExtra(AudioCaptureService.EXTRA_STREAM_MIC, true)
            putExtra("sample_rate", params.sampleRate)
            putExtra("channel_config", params.channelConfig)
            putExtra("buffer_size", params.bufferSize)
            putExtra(AudioCaptureService.EXTRA_IS_MULTICAST, params.isMulticast)
            putExtra("streaming_port", params.streamingPort)
            putExtra("network_interface", params.networkInterface)
            putExtra("rtp_enabled", params.rtpEnabled)
            putExtra("rtp_port", params.rtpPort)
            putExtra("http_enabled", params.httpEnabled)
            putExtra("http_port", params.httpPort)
            putExtra("dlna_enabled", params.dlnaEnabled)
            putExtra("dlna_port", params.dlnaPort)
            putExtra("dlna_format", params.dlnaFormat)
            putExtra("dlna_devices", params.dlnaDevices.toTypedArray())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        NetworkManager.isServerStreaming = true
        NetworkManager.isStreamingCurrent.value = true
    }

    fun stop(context: Context) {
        NetworkManager.stopStreaming(context)
        context.stopService(Intent(context, AudioCaptureService::class.java))
        context.stopService(Intent(context, ClientService::class.java))
        NetworkManager.isStreamingCurrent.value = false
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(context: Context, command: ScriptCommand) {
        val store = SettingsDataStore(context.applicationContext)
        val settings = store.settingsFlow.first()
        applyLinkOverrides(settings, command, context)
        val wantsUsb = command.bool(ScriptParams.USB) == true
        val ip = command.str(ScriptParams.IP)
            ?: command.str(ScriptParams.CLIENTIP)
            ?: (if (wantsUsb) resolveUsbPeer() else null)
            ?: return
        val port = command.int(ScriptParams.PORT) ?: settings.streamingPort
        val clientMic = command.bool(ScriptParams.CLIENTMIC) ?: settings.sendClientMicrophone

        NetworkManager.connectionStatus.value = "Detecting mode for $ip..."
        val known = NetworkManager.discoveredDevices.value.values.find { it.ip == ip }
        val isMulti = known?.isMulticast ?: NetworkManager.probeIsMulticast(ip, port)
        val serverInfo = ServerInfo(ip = ip, isMulticast = isMulti, port = port)

        NetworkManager.clientPresharedKey = command.str(ScriptParams.AUTHKEY) ?: ""

        context.startService(Intent(context, ClientService::class.java))
        NetworkManager.isStreamingCurrent.value = true
        NetworkManager.startClient(
            context = context.applicationContext,
            serverInfo = serverInfo,
            sampleRate = command.int(ScriptParams.SAMPLERATE) ?: settings.sampleRate,
            channelConfig = command.str(ScriptParams.CHANNELS)?.uppercase() ?: settings.channelConfig,
            bufferSize = command.int(ScriptParams.BUFFER) ?: settings.bufferSize,
            sendMicrophone = clientMic,
            micPort = command.int(ScriptParams.MICPORT) ?: settings.micPort,
            networkInterfaceName = command.str(ScriptParams.IFACE) ?: settings.networkInterface,
            connectionSoundEnabled = command.bool(ScriptParams.CONNSOUND) ?: settings.connectionSoundEnabled,
            disconnectionSoundEnabled = command.bool(ScriptParams.DISCSOUND) ?: settings.disconnectionSoundEnabled,
            onServerDisconnected = {
                NetworkManager.isStreamingCurrent.value = false
                context.stopService(Intent(context, ClientService::class.java))
            }
        )
    }

    suspend fun applySet(context: Context, command: ScriptCommand) {
        val store = SettingsDataStore(context.applicationContext)
        val s = store.settingsFlow.first()

        val internal = command.bool(ScriptParams.INTERNAL)
        val mic = command.bool(ScriptParams.MIC)
        if (internal != null || mic != null) {
            store.saveAudioSourceSettings(internal ?: s.streamInternal, mic ?: s.streamMic)
        }

        val sampleRate = command.int(ScriptParams.SAMPLERATE)
        val channels = command.str(ScriptParams.CHANNELS)?.uppercase()
        if (sampleRate != null || channels != null) {
            store.saveAudioQualitySettings(sampleRate ?: s.sampleRate, channels ?: s.channelConfig)
        }

        command.int(ScriptParams.BUFFER)?.let { store.saveBufferSize(it) }
        command.int(ScriptParams.PORT)?.let { if (it in 1024..65535) store.saveStreamingPort(it) }
        command.int(ScriptParams.MICPORT)?.let { if (it in 1024..65535) store.saveMicPort(it) }
        command.bool(ScriptParams.MULTICAST)?.let { store.saveLastMulticastMode(it) }

        val rtp = command.bool(ScriptParams.RTP)
        val rtpPort = command.int(ScriptParams.RTPPORT)
        val http = command.bool(ScriptParams.HTTP)
        if (rtp != null || rtpPort != null || http != null) {
            store.saveServerProtocols(rtp ?: s.rtpEnabled, rtpPort ?: s.rtpPort, http ?: s.httpEnabled)
        }

        val httpPort = command.int(ScriptParams.HTTPPORT)
        val httpSafari = command.bool(ScriptParams.HTTPSAFARI)
        if (httpPort != null || httpSafari != null) {
            store.saveHttpSettings(httpPort ?: s.httpPort, httpSafari ?: s.httpSafariMode)
        }

        command.str(ScriptParams.IFACE)?.let { store.saveNetworkInterface(it) }
        command.str(ScriptParams.CLIENTIP)?.let { store.saveClientTileIp(it) }
        command.bool(ScriptParams.AUTOCONNECT)?.let { store.setAutoConnectEnabled(it) }
        command.bool(ScriptParams.CONNSOUND)?.let { store.saveConnectionSoundEnabled(it) }
        command.bool(ScriptParams.DISCSOUND)?.let { store.saveDisconnectionSoundEnabled(it) }

        command.bool(ScriptParams.USB)?.let { store.saveUsbMode(it) }
        command.int(ScriptParams.USBLATENCY)?.let { store.saveUsbLatency(it) }
        resolveWfasMode(command)?.let { store.saveWfasMode(it) }

        persistSecurityIfPresent(store, s, command)
    }
}
