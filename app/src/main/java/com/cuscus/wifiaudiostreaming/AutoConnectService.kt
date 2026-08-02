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

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.cuscus.wifiaudiostreaming.data.AutoConnectEntry
import com.cuscus.wifiaudiostreaming.data.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException

class AutoConnectService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isConnecting = false
    private var listenJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        NotificationCenter.ensureChannels(this)

        val notification = buildNotification(getString(R.string.auto_connect_listening), false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationCenter.ID_AUTO_CONNECT,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationCenter.ID_AUTO_CONNECT, notification)
        }

        serviceScope.launch {
            combine(
                NetworkManager.connectionStatus,
                NetworkManager.isStreamingCurrent
            ) { status, streaming -> status to streaming }
                .distinctUntilChanged()
                .conflate()
                .collect { (status, streaming) ->
                    val text = if (streaming && status.isNotBlank()) {
                        status
                    } else {
                        getString(R.string.auto_connect_listening)
                    }
                    NotificationCenter.post(
                        this@AutoConnectService,
                        NotificationCenter.ID_AUTO_CONNECT,
                        buildNotification(text, streaming)
                    )
                    delay(UPDATE_THROTTLE_MS)
                }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (listenJob?.isActive == true) {
            return START_STICKY
        }

        Log.d("AutoConnect", "Servizio Avviato. Inizio loop di background.")

        listenJob = serviceScope.launch(Dispatchers.IO) {
            val settingsDataStore = SettingsDataStore(applicationContext)
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager

            while (isActive) {
                val prefs = settingsDataStore.settingsFlow.first()

                if (!prefs.autoConnectEnabled) {
                    Log.d("AutoConnect", "Auto-connect disabilitato. Arresto servizio.")
                    stopSelf()
                    return@launch
                }

                if (NetworkManager.isStreamingCurrent.value || isConnecting) {
                    delay(2000)
                    continue
                }

                var targetServer: ServerInfo? = null
                val multicastLock = wifiManager.createMulticastLock("auto_connect_multicast_lock")
                multicastLock.setReferenceCounted(false)

                try {
                    multicastLock.acquire()
                    val groupAddress = InetAddress.getByName("239.255.0.1")

                    val socket = MulticastSocket(null).apply {
                        reuseAddress = true
                        bind(InetSocketAddress(9091))
                        soTimeout = 5000
                    }

                    try {
                        val interfaces = NetworkInterface.getNetworkInterfaces().toList()
                        val wifiIface = if (prefs.networkInterface != "Auto") {
                            interfaces.firstOrNull { it.displayName == prefs.networkInterface || it.name == prefs.networkInterface }
                        } else {
                            interfaces.firstOrNull { it.isUp && !it.isLoopback && !it.isVirtual && it.name.contains("wlan") }
                        }
                        if (wifiIface != null) {
                            socket.networkInterface = wifiIface
                            Log.d("AutoConnect", "Forzata interfaccia di rete: ${wifiIface.name}")
                        }
                    } catch (e: Exception) {}

                    MulticastNet.joinAllGroups(socket)
                    Log.d("AutoConnect", "Socket Multicast APERTO e in ASCOLTO sulla porta 9091")

                    val buffer = ByteArray(1024)
                    val packet = DatagramPacket(buffer, buffer.size)
                    val localIp = NetworkManager.getLocalIpAddress(applicationContext)

                    while (isActive && !NetworkManager.isStreamingCurrent.value && !isConnecting) {
                        try {
                            socket.receive(packet)
                            val message = String(packet.data, 0, packet.length).trim()
                            val remoteIp = packet.address.hostAddress

                            if (remoteIp != null && remoteIp != localIp && message.startsWith("WIFI_AUDIO_STREAMER_DISCOVERY")) {
                                Log.d("AutoConnect", "-> Ricevuto UDP da $remoteIp: $message")

                                val parts = message.split(";")
                                if (parts.size >= 4) {
                                    val isMulticast = parts[2].equals("MULTICAST", ignoreCase = true)
                                    val port = parts[3].toIntOrNull() ?: continue

                                    val currentSsid = NetworkManager.getCurrentSsid(applicationContext).trim().removePrefix("\"").removeSuffix("\"")
                                    val priorityList = AutoConnectEntry.parseList(prefs.autoConnectList)
                                    val validEntries = priorityList.filter { it.ip.isNotBlank() }

                                    val isMatch = if (validEntries.isEmpty()) {
                                        true
                                    } else {
                                        validEntries.any { entry ->
                                            val cleanIp = entry.ip.trim()
                                            val cleanSsid = entry.ssid.trim().removePrefix("\"").removeSuffix("\"")
                                            val isSsidUnknown = currentSsid == "<unknown ssid>" || currentSsid.isEmpty()
                                            val matchesSsid = cleanSsid.isEmpty() || isSsidUnknown || cleanSsid == currentSsid

                                            matchesSsid && cleanIp == remoteIp
                                        }
                                    }

                                    if (isMatch) {
                                        Log.d("AutoConnect", "MATCH POSITIVO. Preparo la connessione a $remoteIp:$port")
                                        val micTok = parts.firstOrNull { it.startsWith("mic=") }
                                            ?.removePrefix("mic=")
                                        targetServer = ServerInfo(
                                            remoteIp, isMulticast, port,
                                            serverSendsMic = micTok?.contains("tx") == true,
                                            serverWantsMic = micTok?.contains("rx") == true,
                                            fromBeacon = true,
                                            audioFormat = StreamAudioFormat.fromBeaconParts(parts)
                                        )
                                        break
                                    }
                                }
                            }
                        } catch (e: SocketTimeoutException) {
                            continue
                        } catch (e: Exception) {
                            break
                        }
                    }

                    try {
                        MulticastNet.leaveAllGroups(socket)
                        socket.close()
                        Log.d("AutoConnect", "Socket CHIUSO correttamente.")
                    } catch (e: Exception) {}

                } catch (e: Exception) {
                    delay(3000)
                } finally {
                    if (multicastLock.isHeld) {
                        multicastLock.release()
                    }
                }

                if (targetServer != null) {
                    isConnecting = true
                    NetworkManager.isServerStreaming = false

                    try {
                        Log.d("AutoConnect", "Avvio Service per Client a ${targetServer.ip}")
                        val clientIntent = Intent(applicationContext, ClientService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            applicationContext.startForegroundService(clientIntent)
                        } else {
                            applicationContext.startService(clientIntent)
                        }

                        NetworkManager.startClient(
                            context = applicationContext,
                            serverInfo = targetServer,
                            sampleRate = prefs.sampleRate,
                            channelConfig = prefs.channelConfig,
                            bufferSize = prefs.bufferSize,
                            sendMicrophone = prefs.sendClientMicrophone,
                            micPort = prefs.micPort,
                            networkInterfaceName = prefs.networkInterface,
                            connectionSoundEnabled = prefs.connectionSoundEnabled,
                            disconnectionSoundEnabled = prefs.disconnectionSoundEnabled,
                            onServerDisconnected = {
                                Log.d("AutoConnect", "Callback disconnessione ricevuta. Reset dello stato.")
                                NetworkManager.isStreamingCurrent.value = false
                                val stopIntent = Intent(applicationContext, ClientService::class.java)
                                applicationContext.stopService(stopIntent)
                            }
                        )
                    } catch (e: Exception) {
                        NetworkManager.isStreamingCurrent.value = false
                        val stopIntent = Intent(applicationContext, ClientService::class.java)
                        applicationContext.stopService(stopIntent)
                    } finally {
                        isConnecting = false
                        while (NetworkManager.isStreamingCurrent.value) {
                            delay(5000)
                        }
                        Log.d("AutoConnect", "Disconnesso. Ripresa del loop di ascolto.")
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("AutoConnect", "Servizio distrutto.")
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        NotificationCenter.cancel(this, NotificationCenter.ID_AUTO_CONNECT)
        super.onDestroy()
    }

    private fun buildNotification(statusText: String, streaming: Boolean) =
        NotificationCenter.autoConnectNotification(this, statusText, streaming)

    private companion object {
        const val UPDATE_THROTTLE_MS = 500L
    }
}