package com.example.wifiaudiostreaming

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
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.cancellation.CancellationException

data class ServerInfo(val ip: String, val isMulticast: Boolean, val port: Int)

@SuppressLint("MissingPermission")
object NetworkManager {

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

    // --- (Le funzioni da getAllLocalIpAddresses a startServerAudio rimangono invariate) ---
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

    fun startListeningForDevices(context: Context) {
        if (listeningJob?.isActive == true) return
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        // Il MulticastLock è FONDAMENTALE per assicurare che la radio Wi-Fi non vada
        // in risparmio energetico, bloccando la ricezione dei pacchetti multicast.
        val multicastLock = wifiManager.createMulticastLock("wifi_audio_streamer_discovery_lock")
        multicastLock.setReferenceCounted(true)

        listeningJob = scope.launch {
            var socket: MulticastSocket? = null
            try {
                multicastLock.acquire()
                val localIps = getAllLocalIpAddresses()
                val groupAddress = InetAddress.getByName(NetworkSettings.MULTICAST_GROUP_IP)

                // Crea un MulticastSocket sulla porta di discovery
                socket = MulticastSocket(NetworkSettings.DISCOVERY_PORT).apply {
                    // Si iscrive al gruppo per ricevere i pacchetti
                    joinGroup(groupAddress)
                    // Imposta un timeout per non bloccare il ciclo indefinitamente
                    // e permettere il controllo di `isActive`
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

                                // Aggiungi solo se non è già stato scoperto
                                if (!discoveredDevices.value.containsKey(hostname)) {
                                    val serverInfo = ServerInfo(ip = remoteIp, isMulticast = isMulticast, port = port)
                                    discoveredDevices.value += (hostname to serverInfo)
                                    println("Discovered server: $hostname at $remoteIp")
                                }
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        // Il timeout è normale, serve solo per sbloccare la receive e controllare `isActive`
                        continue
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) println("Listening error: ${e.message}")
            } finally {
                try {
                    // Abbandona il gruppo e chiudi il socket
                    socket?.leaveGroup(InetAddress.getByName(NetworkSettings.MULTICAST_GROUP_IP))
                    socket?.close()
                } catch (e: Exception) {
                    println("Error closing multicast socket: ${e.message}")
                }
                if (multicastLock.isHeld) {
                    multicastLock.release()
                }
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
                    Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) ?: "${Build.MANUFACTURER} ${Build.MODEL}"
                } else {
                    "${Build.MANUFACTURER} ${Build.MODEL}"
                }
            } catch (e: Exception) { "${Build.MANUFACTURER} ${Build.MODEL}" }

            val mode = if (isMulticast) "MULTICAST" else "UNICAST"
            val message = "${NetworkSettings.DISCOVERY_MESSAGE};$deviceName;$mode;$streamingPort"

            val selectorManager = SelectorManager(Dispatchers.IO)
            // Indirizzo di destinazione modificato per il multicast
            val multicastGroupAddress = InetSocketAddress(NetworkSettings.MULTICAST_GROUP_IP, NetworkSettings.DISCOVERY_PORT)
            val socket = aSocket(selectorManager).udp().bind(InetSocketAddress("0.0.0.0", 0))

            try {
                while (isActive) {
                    val packet = buildPacket { writeText(message) }
                    // Invia il datagramma al gruppo multicast invece che in broadcast
                    socket.send(Datagram(packet, multicastGroupAddress))
                    delay(3000)
                }
            } catch(e: Exception) {
                if (e !is CancellationException) println("Broadcasting error: ${e.message}")
            } finally {
                socket.close()
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

                suspend fun streamingLoop(targetAddress: SocketAddress, bufSize: Int) {
                    val internalBuffer = if (streamInternal) ByteArray(bufSize) else null
                    val micBuffer = if (streamMic) ByteArray(bufSize) else null
                    val mixedBuffer = ByteArray(bufSize)
                    while(isActive) {
                        val internalBytes = internalRecord?.read(internalBuffer!!, 0, internalBuffer.size, AudioRecord.READ_BLOCKING) ?: 0
                        val micBytes = micRecord?.read(micBuffer!!, 0, micBuffer.size, AudioRecord.READ_BLOCKING) ?: 0

                        if (internalBytes <= 0 && micBytes <= 0) continue

                        val bytesToProcess = when {
                            streamInternal && streamMic -> minOf(internalBytes, micBytes)
                            streamInternal -> internalBytes
                            else -> micBytes
                        }

                        val packet = buildPacket {
                            if (streamInternal && streamMic && bytesToProcess > 0) {
                                val internalShorts = ByteBuffer.wrap(internalBuffer!!, 0, bytesToProcess).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                val micShorts = ByteBuffer.wrap(micBuffer!!, 0, bytesToProcess).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                for (i in 0 until bytesToProcess / 2) {
                                    val mixedSample = internalShorts[i].toInt() + micShorts[i].toInt()
                                    val clippedSample = mixedSample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                                    mixedBuffer[2 * i] = (clippedSample.toInt() and 0xff).toByte()
                                    mixedBuffer[2 * i + 1] = (clippedSample.toInt() shr 8 and 0xff).toByte()
                                }
                                writeFully(mixedBuffer, 0, bytesToProcess)
                            } else if (streamInternal && internalBytes > 0) {
                                writeFully(internalBuffer!!, 0, internalBytes)
                            } else if (streamMic && micBytes > 0) {
                                writeFully(micBuffer!!, 0, micBytes)
                            }
                        }
                        if (packet.remaining > 0) {
                            sendSocket?.send(Datagram(packet, targetAddress))
                        }
                    }
                }

                if (isMulticast) {
                    val targetAddress = InetSocketAddress(NetworkSettings.MULTICAST_GROUP_IP, streamingPort)
                    sendSocket = aSocket(selectorManager).udp().bind()
                    setupAudioRecorders(bufferSize)
                    streamingLoop(targetAddress, bufferSize)
                } else {
                    connectionStatus.value = context.getString(R.string.status_waiting_for_client, streamingPort)
                    val localAddress = InetSocketAddress("0.0.0.0", streamingPort)
                    sendSocket = aSocket(SelectorManager(Dispatchers.IO)).udp().bind(localAddress) { reuseAddress = true }

                    val clientDatagram = sendSocket.receive()
                    val clientAddress = clientDatagram.address
                    val message = clientDatagram.packet.readText().trim()

                    if (message == NetworkSettings.CLIENT_HELLO_MESSAGE) {
                        val ackPacket = buildPacket { writeText("HELLO_ACK") }
                        sendSocket.send(Datagram(ackPacket, clientAddress))
                        connectionStatus.value = context.getString(R.string.status_client_connected, clientAddress)

                        setupAudioRecorders(bufferSize)
                        streamingLoop(clientAddress, bufferSize)
                    } else {
                        connectionStatus.value = context.getString(R.string.status_handshake_failed_from_client, clientAddress)
                        sendSocket.close()
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) connectionStatus.value = context.getString(R.string.status_server_error, e.message)
            } finally {
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
        micPort: Int
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
                        connectionStatus.value = context.getString(R.string.status_contacting_server, serverInfo.ip)
                        val remoteAddress = InetSocketAddress(serverInfo.ip, serverInfo.port)
                        socket = aSocket(selectorManager).udp().bind()
                        val helloPacket = buildPacket { writeText(NetworkSettings.CLIENT_HELLO_MESSAGE) }
                        socket.send(Datagram(helloPacket, remoteAddress))

                        connectionStatus.value = context.getString(R.string.status_waiting_for_ack)
                        val ackDatagram = withTimeout(5000) { socket.receive() }
                        val ackMsg = ackDatagram.packet.readText().trim()
                        if (ackMsg != "HELLO_ACK") {
                            throw Exception(context.getString(R.string.status_handshake_failed_unexpected_response, ackMsg))
                        }

                        val channelConfigOut = if (channelConfig == "STEREO") AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
                        val playbackBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(bufferSize * 8)
                        audioTrack = AudioTrack.Builder()
                            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(channelConfigOut).build())
                            .setBufferSizeInBytes(playbackBufferSize).setTransferMode(AudioTrack.MODE_STREAM).build()

                        audioTrack.play()
                        connectionStatus.value = context.getString(R.string.status_streaming)

                        val buffer = ByteArray(bufferSize * 2)
                        while (isActive) {
                            val datagram = socket.receive()
                            val packet = datagram.packet
                            val bytesRead = packet.readAvailable(buffer)
                            if (bytesRead > 0) {
                                audioTrack.write(buffer, 0, bytesRead, AudioTrack.WRITE_BLOCKING)
                            }
                        }
                    } finally {
                        audioTrack?.stop(); audioTrack?.release()
                        socket?.close()
                    }
                } else {
                    var audioTrack: AudioTrack? = null
                    var multicastSocket: MulticastSocket? = null
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val multicastLock = wifiManager.createMulticastLock("wifi_audio_streamer_multicast_lock")
                    try {
                        multicastLock.acquire()
                        connectionStatus.value = context.getString(R.string.status_joining_multicast)
                        val groupAddress = InetAddress.getByName(NetworkSettings.MULTICAST_GROUP_IP)
                        multicastSocket = MulticastSocket(serverInfo.port)
                        multicastSocket.joinGroup(groupAddress)

                        val channelConfigOut = if (channelConfig == "STEREO") AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
                        val playbackBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(bufferSize * 8)
                        audioTrack = AudioTrack.Builder()
                            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(channelConfigOut).build())
                            .setBufferSizeInBytes(playbackBufferSize).setTransferMode(AudioTrack.MODE_STREAM).build()

                        audioTrack.play()
                        connectionStatus.value = context.getString(R.string.status_streaming)

                        val buffer = ByteArray(bufferSize * 2)
                        val packet = DatagramPacket(buffer, buffer.size)
                        while (isActive) {
                            multicastSocket.receive(packet)
                            if (packet.length > 0) {
                                audioTrack.write(packet.data, 0, packet.length, AudioTrack.WRITE_BLOCKING)
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
