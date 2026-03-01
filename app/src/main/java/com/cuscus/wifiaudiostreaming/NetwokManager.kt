package com.cuscus.wifiaudiostreaming

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.cancellation.CancellationException

data class ServerInfo(val ip: String, val isMulticast: Boolean, val port: Int)

@SuppressLint("MissingPermission")
object NetworkManager {

    // --- GESTIONE VOLUME SERVER ANDROID ---
    val serverVolume = MutableStateFlow(1.0f)
    var isServerStreaming = false
    // --------------------------------------

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamingJob: Job? = null
    private var listeningJob: Job? = null
    private var broadcastingJob: Job? = null
    private var originalMediaVolume: Int? = null
    private var micStreamingJob: Job? = null

    private object NetworkSettings {
        const val DISCOVERY_PORT = 9091
        const val DISCOVERY_MESSAGE = "WIFI_AUDIO_STREAMER_DISCOVERY"
        const val CLIENT_HELLO_MESSAGE = "HELLO_FROM_CLIENT"
        const val MULTICAST_GROUP_IP = "239.255.0.1"
    }

    val connectionStatus = MutableStateFlow("")
    val discoveredDevices = MutableStateFlow<Map<String, ServerInfo>>(emptyMap())

    fun getLocalIpAddress(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val ipAddress = wifiManager.connectionInfo.ipAddress
            if (ipAddress != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ipAddress and 0xff,
                    ipAddress shr 8 and 0xff,
                    ipAddress shr 16 and 0xff,
                    ipAddress shr 24 and 0xff
                )
            }
            java.net.NetworkInterface.getNetworkInterfaces().toList().forEach { intf ->
                if (!intf.isLoopback && intf.isUp) {
                    intf.inetAddresses.toList().forEach { addr ->
                        if (addr is java.net.Inet4Address && !addr.hostAddress.startsWith("192.168.112")) {
                            return addr.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return "127.0.0.1"
    }

    private fun getAllLocalIpAddresses(): Set<String> {
        val ipSet = mutableSetOf<String>()
        try {
            NetworkInterface.getNetworkInterfaces().toList().forEach { iface ->
                iface.inetAddresses.toList().forEach { addr ->
                    if (!addr.isLoopbackAddress && addr.hostAddress.indexOf(':') < 0) {
                        ipSet.add(addr.hostAddress)
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return ipSet
    }

    private fun getWifiNetworkInterface(): NetworkInterface? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList().firstOrNull { iface ->
                iface.isUp &&
                        !iface.isLoopback &&
                        !iface.isVirtual &&
                        iface.inetAddresses.toList().any { addr ->
                            addr is java.net.Inet4Address &&
                                    !addr.isLoopbackAddress &&
                                    !addr.hostAddress.startsWith("192.168.112") &&
                                    !addr.hostAddress.startsWith("192.168.42")
                        }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun startListeningForDevices(context: Context) {
        if (listeningJob?.isActive == true) return
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicastLock = wifiManager.createMulticastLock("wifi_audio_streamer_discovery_lock")
        multicastLock.setReferenceCounted(true)

        listeningJob = scope.launch {
            var socket: MulticastSocket? = null
            try {
                multicastLock.acquire()
                val localIps = getAllLocalIpAddresses()
                val groupAddress = InetAddress.getByName(NetworkSettings.MULTICAST_GROUP_IP)

                socket = MulticastSocket(NetworkSettings.DISCOVERY_PORT).apply {
                    getWifiNetworkInterface()?.let { networkInterface = it }
                    joinGroup(groupAddress)
                    soTimeout = 5000
                }

                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive) {
                    try {
                        socket.receive(packet)
                        val remoteIp = packet.address.hostAddress
                        val message = String(packet.data, 0, packet.length).trim()

                        if (remoteIp != null && remoteIp !in localIps && message.startsWith(NetworkSettings.DISCOVERY_MESSAGE)) {
                            val parts = message.split(";")
                            if (parts.size == 4) {
                                val hostname = parts[1]
                                val isMulticast = parts[2].equals("MULTICAST", ignoreCase = true)
                                val port = parts[3].toIntOrNull() ?: continue

                                if (!discoveredDevices.value.containsKey(hostname)) {
                                    val serverInfo = ServerInfo(ip = remoteIp, isMulticast = isMulticast, port = port)
                                    discoveredDevices.value += (hostname to serverInfo)
                                    println("Discovered server: $hostname at $remoteIp")
                                }
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) println("Listening error: ${e.message}")
            } finally {
                try {
                    socket?.leaveGroup(InetAddress.getByName(NetworkSettings.MULTICAST_GROUP_IP))
                    socket?.close()
                } catch (e: Exception) {
                    println("Error closing multicast socket: ${e.message}")
                }
                if (multicastLock.isHeld) multicastLock.release()
            }
        }
    }

    fun stopListeningForDevices() {
        listeningJob?.cancel()
        listeningJob = null
    }

    fun startBroadcastingPresence(context: Context, isMulticast: Boolean, streamingPort: Int) {
        if (broadcastingJob?.isActive == true) return
        broadcastingJob = scope.launch {
            val deviceName = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
                        ?: "${Build.MANUFACTURER} ${Build.MODEL}"
                } else {
                    "${Build.MANUFACTURER} ${Build.MODEL}"
                }
            } catch (e: Exception) { "${Build.MANUFACTURER} ${Build.MODEL}" }

            val mode = if (isMulticast) "MULTICAST" else "UNICAST"
            val message = "${NetworkSettings.DISCOVERY_MESSAGE};$deviceName;$mode;$streamingPort"
            val messageBytes = message.toByteArray()

            val groupAddress = InetAddress.getByName(NetworkSettings.MULTICAST_GROUP_IP)
            val wifiIface = getWifiNetworkInterface()

            var socket: MulticastSocket? = null
            try {
                socket = MulticastSocket().apply {
                    timeToLive = 4
                    wifiIface?.let { networkInterface = it }
                }

                while (isActive) {
                    try {
                        val packet = DatagramPacket(
                            messageBytes,
                            messageBytes.size,
                            groupAddress,
                            NetworkSettings.DISCOVERY_PORT
                        )
                        socket.send(packet)
                        println("Broadcasting presence: $message on iface ${wifiIface?.name ?: "default"}")
                    } catch (e: Exception) {
                        if (e !is CancellationException) println("Broadcasting error: ${e.message}")
                    }
                    delay(3000)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) println("Broadcasting setup error: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    fun stopBroadcastingPresence() {
        broadcastingJob?.cancel()
        broadcastingJob = null
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun CoroutineScope.launchMicSenderJob(
        context: Context,
        serverInfo: ServerInfo,
        sampleRate: Int,
        channelConfig: String,
        bufferSize: Int,
        micPort: Int
    ) = launch {
        var micRecord: AudioRecord? = null
        var socket: DatagramSocket? = null

        try {
            val channelConfigIn = if (channelConfig == "STEREO") AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, AudioFormat.ENCODING_PCM_16BIT)
            val micBufferSize = bufferSize.coerceAtLeast(minBufferSize)

            micRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfigIn,
                AudioFormat.ENCODING_PCM_16BIT,
                micBufferSize
            )

            socket = DatagramSocket()
            val destinationAddress = InetAddress.getByName(serverInfo.ip)

            micRecord.startRecording()
            println("Invio microfono a ${serverInfo.ip}:$micPort")

            val buffer = ByteArray(micBufferSize)
            while (isActive) {
                val bytesRead = micRecord.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    val packet = DatagramPacket(buffer, bytesRead, destinationAddress, micPort)
                    socket.send(packet)
                }
            }
        } catch (e: SecurityException) {
            connectionStatus.value = context.getString(R.string.status_mic_permission_denied)
        } catch (e: Exception) {
            if (e !is CancellationException) {
                connectionStatus.value = context.getString(R.string.status_mic_send_error, e.message)
            }
        } finally {
            println("Invio microfono terminato.")
            micRecord?.stop()
            micRecord?.release()
            socket?.close()
        }
    }

    // =========================================================
    // FIX 2 (server side) — setupAudioRecorders viene chiamato
    // SUBITO in modalità unicast, prima di aspettare il client,
    // così i recorder sono già "caldi" quando arriva l'HELLO.
    // =========================================================
    @RequiresApi(Build.VERSION_CODES.Q)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startServerAudio(
        context: Context,
        projection: MediaProjection?,
        streamInternal: Boolean,
        streamMic: Boolean,
        sampleRate: Int,
        channelConfig: String,
        bufferSize: Int,
        isMulticast: Boolean,
        streamingPort: Int
    ) {
        if (streamingJob?.isActive == true) return
        startBroadcastingPresence(context, isMulticast, streamingPort)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        originalMediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (streamInternal) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        }

        streamingJob = scope.launch {
            isServerStreaming = true
            var internalRecord: AudioRecord? = null
            var micRecord: AudioRecord? = null
            var sendSocket: BoundDatagramSocket? = null

            try {
                val selectorManager = SelectorManager(Dispatchers.IO)
                val channelConfigIn = if (channelConfig == "STEREO") AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfigIn)
                    .build()

                fun setupAudioRecorders(bufSize: Int) {
                    if (streamInternal && projection != null) {
                        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .build()
                        internalRecord = AudioRecord.Builder()
                            .setAudioFormat(audioFormat)
                            .setBufferSizeInBytes(bufSize)
                            .setAudioPlaybackCaptureConfig(config)
                            .build()
                        internalRecord?.startRecording()
                    }
                    if (streamMic) {
                        micRecord = AudioRecord.Builder()
                            .setAudioSource(MediaRecorder.AudioSource.MIC)
                            .setAudioFormat(audioFormat)
                            .setBufferSizeInBytes(bufSize)
                            .build()
                        micRecord?.startRecording()
                    }
                }

                suspend fun streamingLoop(
                    targetAddress: SocketAddress,
                    bufSize: Int,
                    clientAlive: java.util.concurrent.atomic.AtomicBoolean? = null
                ) {
                    val internalBuffer = if (streamInternal) ByteArray(bufSize) else null
                    val micBuffer = if (streamMic) ByteArray(bufSize) else null
                    val mixedBuffer = ByteArray(bufSize)

                    while (isActive && clientAlive?.get() != false) {
                        val internalBytes = internalRecord?.read(internalBuffer!!, 0, internalBuffer.size, AudioRecord.READ_BLOCKING) ?: 0
                        val micBytes = micRecord?.read(micBuffer!!, 0, micBuffer.size, AudioRecord.READ_BLOCKING) ?: 0

                        if (internalBytes <= 0 && micBytes <= 0) continue

                        val bytesToProcess = when {
                            streamInternal && streamMic -> minOf(internalBytes, micBytes)
                            streamInternal -> internalBytes
                            else -> micBytes
                        }

                        val bufferToSend = if (streamInternal && streamMic && bytesToProcess > 0) {
                            val internalShorts = ByteBuffer.wrap(internalBuffer!!, 0, bytesToProcess).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val micShorts = ByteBuffer.wrap(micBuffer!!, 0, bytesToProcess).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            for (i in 0 until bytesToProcess / 2) {
                                val mixedSample = internalShorts[i].toInt() + micShorts[i].toInt()
                                val clippedSample = mixedSample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                                mixedBuffer[2 * i] = (clippedSample.toInt() and 0xff).toByte()
                                mixedBuffer[2 * i + 1] = (clippedSample.toInt() shr 8 and 0xff).toByte()
                            }
                            mixedBuffer
                        } else if (streamInternal && internalBytes > 0) {
                            internalBuffer!!
                        } else if (streamMic && micBytes > 0) {
                            micBuffer!!
                        } else null

                        if (bufferToSend != null && bytesToProcess > 0) {
                            val vol = serverVolume.value
                            if (vol != 1.0f) {
                                val shorts = ByteBuffer.wrap(bufferToSend, 0, bytesToProcess).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                for (i in 0 until bytesToProcess / 2) {
                                    var sample = (shorts[i].toInt() * vol).toInt()
                                    sample = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                    bufferToSend[2 * i] = (sample and 0xff).toByte()
                                    bufferToSend[2 * i + 1] = ((sample shr 8) and 0xff).toByte()
                                }
                            }
                            val packet = buildPacket { writeFully(bufferToSend, 0, bytesToProcess) }
                            try {
                                sendSocket?.send(Datagram(packet, targetAddress))
                            } catch (_: Exception) {
                                // send fallito → client sparito
                                clientAlive?.set(false)
                                break
                            }
                        }
                    }
                }

                if (isMulticast) {
                    val targetAddress = InetSocketAddress(NetworkSettings.MULTICAST_GROUP_IP, streamingPort)
                    sendSocket = aSocket(selectorManager).udp().bind()
                    setupAudioRecorders(bufferSize)
                    try {
                        streamingLoop(targetAddress, bufferSize)
                    } finally {
                        // BYE al gruppo multicast: tutti i client si disconnettono
                        try {
                            val byePacket = buildPacket { writeText("BYE") }
                            sendSocket?.send(Datagram(byePacket, targetAddress))
                            println("--- Sent BYE to multicast group ---")
                        } catch (_: Exception) {}
                    }
                } else {
                    // Loop esterno: il server torna in waiting dopo ogni disconnect
                    connectionStatus.value = context.getString(R.string.status_waiting_for_client, streamingPort)
                    setupAudioRecorders(bufferSize)

                    val localAddress = InetSocketAddress("0.0.0.0", streamingPort)
                    sendSocket = aSocket(SelectorManager(Dispatchers.IO)).udp().bind(localAddress) { reuseAddress = true }

                    while (isActive) {
                        startBroadcastingPresence(context, isMulticast = false, streamingPort)
                        connectionStatus.value = context.getString(R.string.status_waiting_for_client, streamingPort)

                        val clientDatagram = sendSocket.receive()
                        val clientAddress = clientDatagram.address
                        val message = clientDatagram.packet.readText().trim()

                        if (message != NetworkSettings.CLIENT_HELLO_MESSAGE) continue

                        connectionStatus.value = context.getString(R.string.status_client_connected, clientAddress)
                        stopBroadcastingPresence()

                        // Drain AudioRecord
                        val drainBuffer = ByteArray(bufferSize)
                        var staleBytesTotal = 0
                        var staleBytes: Int
                        do {
                            staleBytes = internalRecord?.read(drainBuffer, 0, drainBuffer.size, AudioRecord.READ_NON_BLOCKING) ?: 0
                            if (staleBytes > 0) staleBytesTotal += staleBytes
                            val micStale = micRecord?.read(drainBuffer, 0, drainBuffer.size, AudioRecord.READ_NON_BLOCKING) ?: 0
                            if (micStale > 0) staleBytesTotal += micStale
                        } while (staleBytes > 0)
                        println("--- Drained $staleBytesTotal stale bytes ---")

                        val ackPacket = buildPacket { writeText("HELLO_ACK") }
                        sendSocket.send(Datagram(ackPacket, clientAddress))

                        val clientAlive = java.util.concurrent.atomic.AtomicBoolean(true)

                        // PING ogni secondo al client
                        val pingJob = launch {
                            var failures = 0
                            while (isActive && clientAlive.get()) {
                                delay(1000)
                                try {
                                    sendSocket.send(Datagram(buildPacket { writeText("PING") }, clientAddress))
                                    failures = 0
                                } catch (_: Exception) {
                                    if (++failures >= 3) { clientAlive.set(false) }
                                }
                            }
                        }

                        try {
                            streamingLoop(clientAddress, bufferSize, clientAlive)
                        } finally {
                            pingJob.cancel()
                            // BYE solo se è il server a chiudersi (client ancora vivo)
                            if (clientAlive.get()) {
                                try {
                                    sendSocket.send(Datagram(buildPacket { writeText("BYE") }, clientAddress))
                                    println("--- Sent BYE to $clientAddress ---")
                                } catch (_: Exception) {}
                            }
                            // Il while(isActive) torna in waiting automaticamente
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) connectionStatus.value = context.getString(R.string.status_server_error, e.message)
            } finally {
                isServerStreaming = false
                internalRecord?.stop(); internalRecord?.release()
                micRecord?.stop(); micRecord?.release()
                sendSocket?.close()
                stopBroadcastingPresence()
                if (isActive) connectionStatus.value = context.getString(R.string.status_server_stopped)
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startClient(
        context: Context,
        serverInfo: ServerInfo,
        sampleRate: Int,
        channelConfig: String,
        bufferSize: Int,
        sendMicrophone: Boolean,
        micPort: Int,
        onServerDisconnected: (() -> Unit)? = null  // chiamata quando il server si disconnette
    ) {
        stopStreaming(context)

        if (sendMicrophone) {
            micStreamingJob = scope.launchMicSenderJob(context, serverInfo, sampleRate, channelConfig, bufferSize, micPort)
        }

        streamingJob = scope.launch {
            try {
                if (!serverInfo.isMulticast) {
                    var audioTrack: AudioTrack? = null
                    var socket: BoundDatagramSocket? = null
                    try {
                        val selectorManager = SelectorManager(Dispatchers.IO)
                        val channelConfigOut = if (channelConfig == "STEREO") AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO

                        // =========================================================
                        // FIX 3 — Buffer ridotto da *8 a *2.
                        // Il valore *8 aggiungeva una latenza strutturale enorme
                        // (fino a ~700ms con bufferSize=6400 a 48kHz stereo 16bit).
                        // *2 mantiene abbastanza headroom per il jitter di rete
                        // senza introdurre un ritardo percepibile.
                        // =========================================================
                        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, AudioFormat.ENCODING_PCM_16BIT)
                        val playbackBufferSize = minBuffer.coerceAtLeast(bufferSize * 2) // ← FIX 3: era *8

                        // =========================================================
                        // FIX 1 — AudioTrack creato e avviato PRIMA di mandare HELLO.
                        // In origine veniva aperto solo dopo aver ricevuto l'ACK,
                        // aggiungendo ~50-200ms di inizializzazione hardware
                        // sul percorso critico della connessione.
                        // =========================================================
                        audioTrack = AudioTrack.Builder()
                            .setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                            )
                            .setAudioFormat(
                                AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(sampleRate)
                                    .setChannelMask(channelConfigOut)
                                    .build()
                            )
                            .setBufferSizeInBytes(playbackBufferSize)
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build()

                        audioTrack.play() // ← FIX 1: la linea audio è pronta prima dell'handshake

                        connectionStatus.value = context.getString(R.string.status_contacting_server, serverInfo.ip)
                        socket = aSocket(selectorManager).udp().bind()
                        val remoteAddress = InetSocketAddress(serverInfo.ip, serverInfo.port)
                        val helloPacket = buildPacket { writeText(NetworkSettings.CLIENT_HELLO_MESSAGE) }
                        socket.send(Datagram(helloPacket, remoteAddress))

                        connectionStatus.value = context.getString(R.string.status_waiting_for_ack)
                        val ackDatagram = withTimeout(15000) { socket.receive() }
                        val ackMsg = ackDatagram.packet.readText().trim()
                        if (ackMsg != "HELLO_ACK") {
                            throw Exception(context.getString(R.string.status_handshake_failed_unexpected_response, ackMsg))
                        }

                        // AudioTrack già pronto — nessun ritardo qui
                        connectionStatus.value = context.getString(R.string.status_streaming)

                        val buffer = ByteArray(bufferSize * 2)
                        var lastPingReceived = System.currentTimeMillis()
                        val pingTimeoutMs = 3000L
                        var serverDisconnected = false  // flag per distinguere disconnect da stop utente

                        // Watchdog: controlla ogni secondo se il server è ancora vivo
                        val watchdogJob = launch {
                            while (isActive) {
                                delay(1000)
                                if (System.currentTimeMillis() - lastPingReceived > pingTimeoutMs) {
                                    println("--- Server timeout: no PING for ${pingTimeoutMs}ms ---")
                                    serverDisconnected = true
                                    streamingJob?.cancel()
                                    break
                                }
                            }
                        }

                        try {
                            while (isActive) {
                                val datagram = socket.receive()
                                val packet = datagram.packet
                                val bytes = ByteArray(packet.remaining.toInt())
                                packet.readFully(bytes)
                                val text = bytes.toString(Charsets.UTF_8).trim()

                                when (text) {
                                    "PING" -> lastPingReceived = System.currentTimeMillis()
                                    "BYE"  -> {
                                        println("--- Received BYE from server ---")
                                        serverDisconnected = true
                                        streamingJob?.cancel()
                                        break
                                    }
                                    else -> if (bytes.isNotEmpty()) {
                                        audioTrack.write(bytes, 0, bytes.size, AudioTrack.WRITE_BLOCKING)
                                    }
                                }
                            }
                        } finally {
                            watchdogJob.cancel()
                            // Se è il server ad essersi disconnesso (non l'utente che ha premuto stop)
                            // invochiamo la callback che fa esattamente come il tasto stop nella UI
                            if (serverDisconnected) {
                                scope.launch(Dispatchers.Main) {
                                    stopStreaming(context)
                                    onServerDisconnected?.invoke()
                                }
                            }
                        }
                    } finally {
                        audioTrack?.stop(); audioTrack?.release()
                        socket?.close()
                    }
                } else {
                    // Multicast — nessuna modifica necessaria, era già ottimale
                    var audioTrack: AudioTrack? = null
                    var multicastSocket: MulticastSocket? = null
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val multicastLock = wifiManager.createMulticastLock("wifi_audio_streamer_multicast_lock")
                    try {
                        multicastLock.acquire()
                        connectionStatus.value = context.getString(R.string.status_joining_multicast)
                        val groupAddress = InetAddress.getByName(NetworkSettings.MULTICAST_GROUP_IP)
                        multicastSocket = MulticastSocket(serverInfo.port).apply {
                            getWifiNetworkInterface()?.let { networkInterface = it }
                            joinGroup(groupAddress)
                        }

                        val channelConfigOut = if (channelConfig == "STEREO") AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
                        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, AudioFormat.ENCODING_PCM_16BIT)
                        // FIX 3 applicato anche al multicast per consistenza
                        val playbackBufferSize = minBuffer.coerceAtLeast(bufferSize * 2)
                        audioTrack = AudioTrack.Builder()
                            .setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                            )
                            .setAudioFormat(
                                AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(sampleRate)
                                    .setChannelMask(channelConfigOut)
                                    .build()
                            )
                            .setBufferSizeInBytes(playbackBufferSize)
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build()

                        audioTrack.play()
                        connectionStatus.value = context.getString(R.string.status_streaming)

                        val buffer = ByteArray(bufferSize * 2)
                        val packet = DatagramPacket(buffer, buffer.size)
                        var serverDisconnected = false
                        while (isActive) {
                            multicastSocket.receive(packet)
                            if (packet.length > 0) {
                                // Controlla se è un BYE prima di scrivere sull'AudioTrack
                                if (packet.length == 3 && String(packet.data, 0, 3, Charsets.UTF_8) == "BYE") {
                                    println("--- Received BYE from multicast server ---")
                                    serverDisconnected = true
                                    break
                                }
                                audioTrack.write(packet.data, 0, packet.length, AudioTrack.WRITE_BLOCKING)
                            }
                        }

                        if (serverDisconnected) {
                            scope.launch(Dispatchers.Main) {
                                stopStreaming(context)
                                onServerDisconnected?.invoke()
                            }
                        }
                    } finally {
                        audioTrack?.stop(); audioTrack?.release()
                        try {
                            val groupAddress = InetAddress.getByName(NetworkSettings.MULTICAST_GROUP_IP)
                            multicastSocket?.leaveGroup(groupAddress)
                        } catch (_: Exception) {}
                        multicastSocket?.close()
                        if (multicastLock.isHeld) multicastLock.release()
                    }
                }
            } catch (e: BindException) {
                connectionStatus.value = context.getString(R.string.status_port_in_use)
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    connectionStatus.value = context.getString(R.string.status_client_error, e.message)
                }
            } finally {
                micStreamingJob?.cancel()
                micStreamingJob = null
                if (isActive) {
                    connectionStatus.value = context.getString(R.string.status_client_stopped)
                }
            }
        }
    }

    fun stopStreaming(context: Context) {
        streamingJob?.cancel()
        micStreamingJob?.cancel()
        streamingJob = null
        micStreamingJob = null

        originalMediaVolume?.let {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0)
            originalMediaVolume = null
        }

        stopBroadcastingPresence()

        connectionStatus.value = context.getString(R.string.status_idle)
    }

    fun stopAll() {
        stopBroadcastingPresence()
        stopListeningForDevices()
        scope.cancel()
    }
}