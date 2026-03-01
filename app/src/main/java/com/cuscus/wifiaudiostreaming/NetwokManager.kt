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
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.cancellation.CancellationException

data class ServerInfo(
    val ip: String,
    val isMulticast: Boolean,
    val port: Int,
    val isPasswordProtected: Boolean = false,
    val multicastGroupIp: String = "239.255.0.1"
)

@SuppressLint("MissingPermission")
object NetworkManager {

    val serverVolume = MutableStateFlow(1.0f)
    var isServerStreaming = false

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
        // IP multicast base: 239.255.0.x — l'ultimo ottetto è derivato dinamicamente
        // dall'IP locale del server (XOR dei due ottetti finali) per ridurre la
        // probabilità di collisione con altre app sulla stessa LAN.
        // L'utente può sovrascrivere con un valore custom nelle impostazioni.
        const val MULTICAST_BASE = "239.255.0"
        var customMulticastLastOctet: Int? = null  // null = usa derivazione automatica

        fun multicastGroupIp(localIp: String?): String {
            customMulticastLastOctet?.let { return "$MULTICAST_BASE.$it" }
            return if (localIp != null) {
                val parts = localIp.split(".")
                if (parts.size == 4) {
                    val octet3 = parts[2].toIntOrNull() ?: 0
                    val octet4 = parts[3].toIntOrNull() ?: 1
                    // XOR dei due ottetti finali, clampato in range 1..254
                    val derived = ((octet3 xor octet4) and 0xFF).let { if (it == 0) 1 else it }
                    "$MULTICAST_BASE.$derived"
                } else "$MULTICAST_BASE.1"
            } else "$MULTICAST_BASE.1"
        }
        // Auth control messages (SRP-6a handshake)
        const val AUTH_SRP_A  = "SRP_A:"
        const val AUTH_SRP_SB = "SRP_SB:"
        const val AUTH_SRP_M1 = "SRP_M1:"
        const val AUTH_SRP_M2 = "SRP_M2:"
        const val AUTH_DENIED = "AUTH_DENIED"
        const val AUTH_OK     = "AUTH_OK"
    }

    // Porta di controllo auth per multicast (streaming_port + 1)
    private fun authPort(streamingPort: Int) = streamingPort + 1

    // IP multicast corrente (calcolato una volta per sessione)
    @Volatile private var currentMulticastIp: String = "239.255.0.1"

    private fun resolveMulticastIp(context: Context): String {
        val localIp = getLocalIpAddress(context).takeIf { it != "Not connected" }
        return NetworkSettings.multicastGroupIp(localIp).also { currentMulticastIp = it }
    }

    val connectionStatus = MutableStateFlow("")
    val discoveredDevices = MutableStateFlow<Map<String, ServerInfo>>(emptyMap())

    // =========================================================
    // Hex helpers
    // =========================================================
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes(): ByteArray {
        check(length % 2 == 0) { "Odd hex string length" }
        return ByteArray(length / 2) { i -> Integer.parseInt(substring(i * 2, i * 2 + 2), 16).toByte() }
    }

    // =========================================================
    // SRP-6a Handshake — SERVER side
    // Eseguito dopo aver ricevuto CLIENT_HELLO_MESSAGE.
    // Ritorna la AES key di sessione, o null se auth fallita.
    // =========================================================
    private suspend fun serverSrpHandshake(
        context: Context,
        socket: BoundDatagramSocket,
        clientAddress: io.ktor.network.sockets.SocketAddress,
        onAuthFailed: (String) -> Unit
    ): SecretKeySpec? {
        return try {
            // 1. Ricevi A dal client
            val aMsg = withTimeout(10_000) { socket.receive() }.packet.readText().trim()
            if (!aMsg.startsWith(NetworkSettings.AUTH_SRP_A)) return null
            val clientA = java.math.BigInteger(aMsg.removePrefix(NetworkSettings.AUTH_SRP_A), 16)

            // 2. Crea sessione, calcola session key, invia salt:B
            val session = CryptoManager.createServerSession(context) ?: run {
                onAuthFailed("No password configured on server")
                return null
            }
            if (session.computeSessionKey(clientA) == null) {
                onAuthFailed("Invalid client public key A from $clientAddress")
                return null
            }
            val saltHex = session.salt.toHex()
            val bHex = session.serverPublicKey.toString(16)
            socket.send(Datagram(buildPacket { writeText("${NetworkSettings.AUTH_SRP_SB}$saltHex:$bHex") }, clientAddress))

            // 3. Ricevi M1 (proof client)
            val m1Msg = withTimeout(10_000) { socket.receive() }.packet.readText().trim()
            if (!m1Msg.startsWith(NetworkSettings.AUTH_SRP_M1)) return null
            val clientM1 = m1Msg.removePrefix(NetworkSettings.AUTH_SRP_M1).hexToBytes()

            // 4. Verifica M1
            if (!session.verifyClientProof(clientA, clientM1)) {
                socket.send(Datagram(buildPacket { writeText(NetworkSettings.AUTH_DENIED) }, clientAddress))
                onAuthFailed("Wrong password from $clientAddress")
                return null
            }

            // 5. Manda M2 (autenticazione mutua: il client verifica il server)
            val serverM2 = session.computeServerProof(clientA, clientM1)
            socket.send(Datagram(buildPacket { writeText("${NetworkSettings.AUTH_SRP_M2}${serverM2.toHex()}") }, clientAddress))

            session.getAesKey()
        } catch (e: Exception) {
            if (e !is CancellationException) println("SRP server handshake error: ${e.message}")
            null
        }
    }

    // =========================================================
    // SRP-6a Handshake — CLIENT side
    // Ritorna la AES key di sessione, o null se auth fallita/negata.
    // =========================================================
    private suspend fun clientSrpHandshake(
        password: String,
        socket: BoundDatagramSocket,
        serverAddress: io.ktor.network.sockets.SocketAddress,
        onAuthDenied: () -> Unit
    ): SecretKeySpec? {
        return try {
            val session = CryptoManager.ClientSession(password)

            // 1. Manda A
            socket.send(Datagram(
                buildPacket { writeText("${NetworkSettings.AUTH_SRP_A}${session.clientPublicKey.toString(16)}") },
                serverAddress
            ))

            // 2. Ricevi salt:B
            val sbMsg = withTimeout(10_000) { socket.receive() }.packet.readText().trim()
            if (!sbMsg.startsWith(NetworkSettings.AUTH_SRP_SB)) return null
            val parts = sbMsg.removePrefix(NetworkSettings.AUTH_SRP_SB).split(":")
            if (parts.size != 2) return null
            val salt = parts[0].hexToBytes()
            val serverB = java.math.BigInteger(parts[1], 16)
            if (session.computeSessionKey(salt, serverB) == null) return null

            // 3. Manda M1
            val clientM1 = session.computeClientProof(serverB)
            socket.send(Datagram(
                buildPacket { writeText("${NetworkSettings.AUTH_SRP_M1}${clientM1.toHex()}") },
                serverAddress
            ))

            // 4. Ricevi M2 o DENIED
            val replyMsg = withTimeout(10_000) { socket.receive() }.packet.readText().trim()
            if (replyMsg == NetworkSettings.AUTH_DENIED) {
                onAuthDenied()
                return null
            }
            if (!replyMsg.startsWith(NetworkSettings.AUTH_SRP_M2)) return null
            val serverM2 = replyMsg.removePrefix(NetworkSettings.AUTH_SRP_M2).hexToBytes()

            // 5. Verifica M2 — autenticazione mutua (protegge da rogue server)
            if (!session.verifyServerProof(serverM2, serverB, clientM1)) {
                println("SRP: server proof INVALID — possible rogue server!")
                return null
            }

            session.getAesKey()
        } catch (e: Exception) {
            if (e !is CancellationException) println("SRP client handshake error: ${e.message}")
            null
        }
    }

    // =========================================================
    // Network utilities
    // =========================================================
    fun getLocalIpAddress(context: Context): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ipInt = wifiInfo.ipAddress
        return if (ipInt == 0) "Not connected"
        else "${ipInt and 0xff}.${(ipInt shr 8) and 0xff}.${(ipInt shr 16) and 0xff}.${(ipInt shr 24) and 0xff}"
    }

    private fun getAllLocalIpAddresses(): Set<String> {
        val addresses = mutableSetOf<String>()
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                ni.inetAddresses?.toList()?.forEach { addr ->
                    if (!addr.isLoopbackAddress) addresses.add(addr.hostAddress ?: "")
                }
            }
        } catch (_: Exception) {}
        return addresses
    }

    private fun getWifiNetworkInterface(): NetworkInterface? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.firstOrNull { ni ->
                ni.isUp && !ni.isLoopback && ni.name.startsWith("wlan")
            }
        } catch (_: Exception) { null }
    }

    // =========================================================
    // Device discovery — listening
    // =========================================================
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
                // Il discovery viaggia SEMPRE su 239.255.0.1 (canale fisso di annuncio).
                // L'IP di streaming effettivo è comunicato nel payload del messaggio.
                val discoveryGroupAddress = InetAddress.getByName("239.255.0.1")
                socket = MulticastSocket(NetworkSettings.DISCOVERY_PORT).apply {
                    getWifiNetworkInterface()?.let { networkInterface = it }
                    joinGroup(discoveryGroupAddress)
                    soTimeout = 5000
                }
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive) {
                    try {
                        socket.receive(packet)
                        val remoteIp = packet.address.hostAddress ?: continue
                        val message = String(packet.data, 0, packet.length).trim()

                        if (remoteIp !in localIps && message.startsWith(NetworkSettings.DISCOVERY_MESSAGE)) {
                            // Formato: DISCOVERY;hostname;mode;port;locked[;multicastIp]
                            val parts = message.split(";")
                            if (parts.size >= 4) {
                                val hostname = parts[1]
                                val isMulticast = parts[2].equals("MULTICAST", ignoreCase = true)
                                val port = parts[3].toIntOrNull() ?: continue
                                val isPasswordProtected = parts.getOrNull(4)?.equals("LOCKED", ignoreCase = true) == true
                                // Usa l'IP multicast annunciato dal server (o fallback 239.255.0.1)
                                val multicastGroupIp = parts.getOrNull(5)?.takeIf { it.startsWith("239.") }
                                    ?: "239.255.0.1"

                                if (!discoveredDevices.value.containsKey(hostname)) {
                                    val serverInfo = ServerInfo(
                                        ip = remoteIp,
                                        isMulticast = isMulticast,
                                        port = port,
                                        isPasswordProtected = isPasswordProtected,
                                        multicastGroupIp = multicastGroupIp
                                    )
                                    discoveredDevices.value += (hostname to serverInfo)
                                    println("Discovered: $hostname @ $remoteIp (locked=$isPasswordProtected, mcast=$multicastGroupIp)")
                                }
                            }
                        }
                    } catch (_: SocketTimeoutException) { continue }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) println("Discovery listen error: ${e.message}")
            } finally {
                try {
                    socket?.leaveGroup(InetAddress.getByName("239.255.0.1"))
                    socket?.close()
                } catch (_: Exception) {}
                if (multicastLock.isHeld) multicastLock.release()
            }
        }
    }

    fun stopListeningForDevices() {
        listeningJob?.cancel()
        listeningJob = null
    }

    // =========================================================
    // Device discovery — broadcasting
    // =========================================================
    fun startBroadcastingPresence(
        context: Context,
        isMulticast: Boolean,
        streamingPort: Int,
        isPasswordProtected: Boolean = false
    ) {
        if (broadcastingJob?.isActive == true) return
        broadcastingJob = scope.launch {
            val deviceName = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
                    Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
                        ?: "${Build.MANUFACTURER} ${Build.MODEL}"
                else "${Build.MANUFACTURER} ${Build.MODEL}"
            } catch (_: Exception) { "${Build.MANUFACTURER} ${Build.MODEL}" }

            // Risolvi l'IP multicast dalla rete locale (lo includiamo nel messaggio
            // così il client può fare join sull'IP corretto, non su uno hardcoded)
            val multicastIp = resolveMulticastIp(context)
            val mode = if (isMulticast) "MULTICAST" else "UNICAST"
            val locked = if (isPasswordProtected) "LOCKED" else "OPEN"
            // Formato: DISCOVERY;hostname;mode;port;locked[;multicastIp]
            val message = "${NetworkSettings.DISCOVERY_MESSAGE};$deviceName;$mode;$streamingPort;$locked;$multicastIp"
            val messageBytes = message.toByteArray()

            var socket: MulticastSocket? = null
            try {
                // Invia sempre su 239.255.0.1 per il discovery (IP fisso di annuncio)
                // ma include l'IP di streaming effettivo nel payload
                val discoveryGroup = InetAddress.getByName("239.255.0.1")
                socket = MulticastSocket().apply {
                    timeToLive = 4
                    getWifiNetworkInterface()?.let { networkInterface = it }
                }
                val packet = DatagramPacket(messageBytes, messageBytes.size, discoveryGroup, NetworkSettings.DISCOVERY_PORT)
                while (isActive) {
                    socket.send(packet)
                    delay(2000)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) println("Broadcasting error: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    fun stopBroadcastingPresence() {
        broadcastingJob?.cancel()
        broadcastingJob = null
    }

    // =========================================================
    // Mic sender job (client → server reverse channel)
    // =========================================================
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
            micRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfigIn, AudioFormat.ENCODING_PCM_16BIT, micBufferSize)
            socket = DatagramSocket()
            val destinationAddress = InetAddress.getByName(serverInfo.ip)
            micRecord.startRecording()
            val buffer = ByteArray(micBufferSize)
            while (isActive) {
                val bytesRead = micRecord.read(buffer, 0, buffer.size)
                if (bytesRead > 0) socket.send(DatagramPacket(buffer, bytesRead, destinationAddress, micPort))
            }
        } catch (e: SecurityException) {
            connectionStatus.value = context.getString(R.string.status_mic_permission_denied)
        } catch (e: Exception) {
            if (e !is CancellationException) connectionStatus.value = context.getString(R.string.status_mic_send_error, e.message)
        } finally {
            micRecord?.stop(); micRecord?.release(); socket?.close()
        }
    }

    // =========================================================
    // SERVER — start streaming audio
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
        streamingPort: Int,
        isPasswordProtected: Boolean = false
    ) {
        if (streamingJob?.isActive == true) return
        startBroadcastingPresence(context, isMulticast, streamingPort, isPasswordProtected)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        originalMediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (streamInternal) audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)

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

                // Streaming loop — cifra con cipherSession se presente,
                // wrappa con sequence number anche in modalità non cifrata.
                suspend fun streamingLoop(
                    targetAddress: io.ktor.network.sockets.SocketAddress,
                    bufSize: Int,
                    clientAlive: AtomicBoolean? = null,
                    cipherSession: CryptoManager.CipherSession? = null,
                    plainSession: CryptoManager.PlainSession? = null
                ) {
                    val internalBuffer = if (streamInternal) ByteArray(bufSize) else null
                    val micBuffer = if (streamMic) ByteArray(bufSize) else null
                    val mixedBuffer = ByteArray(bufSize)

                    // Calcola quanti byte compongono un frame intero
                    val frameSize = if (channelConfig == "STEREO") 4 else 2

                    while (isActive && clientAlive?.get() != false) {
                        val internalBytes = internalRecord?.read(internalBuffer!!, 0, internalBuffer.size, AudioRecord.READ_BLOCKING) ?: 0
                        val micBytes = micRecord?.read(micBuffer!!, 0, micBuffer.size, AudioRecord.READ_BLOCKING) ?: 0
                        if (internalBytes <= 0 && micBytes <= 0) continue

                        val rawBytesToProcess = when {
                            streamInternal && streamMic -> minOf(internalBytes, micBytes)
                            streamInternal -> internalBytes
                            else -> micBytes
                        }

                        // <-- FIX: Forza i byte a un multiplo del frameSize tagliando i byte spuri
                        val bytesToProcess = (rawBytesToProcess / frameSize) * frameSize
                        if (bytesToProcess <= 0) continue

                        val bufferToSend: ByteArray? = when {
                            streamInternal && streamMic -> {
                                val iShorts = ByteBuffer.wrap(internalBuffer!!, 0, bytesToProcess).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                val mShorts = ByteBuffer.wrap(micBuffer!!, 0, bytesToProcess).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                for (i in 0 until bytesToProcess / 2) {
                                    val mixed = (iShorts[i].toInt() + mShorts[i].toInt()).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                                    mixedBuffer[2 * i] = (mixed.toInt() and 0xff).toByte()
                                    mixedBuffer[2 * i + 1] = (mixed.toInt() shr 8 and 0xff).toByte()
                                }
                                mixedBuffer
                            }
                            streamInternal -> internalBuffer!!
                            streamMic -> micBuffer!!
                            else -> null
                        }

                        if (bufferToSend != null) {
                            val vol = serverVolume.value
                            if (vol != 1.0f) {
                                val shorts = ByteBuffer.wrap(bufferToSend, 0, bytesToProcess).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                for (i in 0 until bytesToProcess / 2) {
                                    val s = (shorts[i].toInt() * vol).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                    bufferToSend[2 * i] = (s and 0xff).toByte()
                                    bufferToSend[2 * i + 1] = ((s shr 8) and 0xff).toByte()
                                }
                            }
                            val raw = bufferToSend.copyOf(bytesToProcess)
                            // Cifra (con SEQ) o wrappa con solo SEQ per il jitter buffer del client
                            val payload = when {
                                cipherSession != null -> cipherSession.encrypt(raw)
                                plainSession != null  -> plainSession.wrap(raw)
                                else -> raw
                            }
                            try {
                                sendSocket?.send(Datagram(buildPacket { writeFully(payload) }, targetAddress))
                            } catch (_: Exception) {
                                clientAlive?.set(false)
                                break
                            }
                        }
                    }
                }

                // ─────────────────────────────────────────────
                // MULTICAST SERVER
                // ─────────────────────────────────────────────
                if (isMulticast) {
                    val multicastIp = resolveMulticastIp(context)
                    val targetAddress = io.ktor.network.sockets.InetSocketAddress(multicastIp, streamingPort)
                    sendSocket = aSocket(selectorManager).udp().bind()
                    setupAudioRecorders(bufferSize)

                    // CipherSession condivisa per tutti i client multicast autenticati.
                    // Il server distribuisce: AES key cifrata con SRP key individuale + sessionPrefix (4B).
                    var multicastCipherSession: CryptoManager.CipherSession? = null
                    if (isPasswordProtected) {
                        val sharedAesKey = SecretKeySpec(ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }, "AES")
                        multicastCipherSession = CryptoManager.createCipherSession(sharedAesKey)
                        val sharedKeyBytes = sharedAesKey.encoded
                        val sessionPrefix = multicastCipherSession.getSessionPrefix()

                        val authAddr = io.ktor.network.sockets.InetSocketAddress("0.0.0.0", authPort(streamingPort))
                        val authSocket = aSocket(SelectorManager(Dispatchers.IO)).udp().bind(authAddr) { reuseAddress = true }
                        launch {
                            while (isActive) {
                                try {
                                    val helloD = authSocket.receive()
                                    if (helloD.packet.readText().trim() != NetworkSettings.CLIENT_HELLO_MESSAGE) continue
                                    val clientAddr = helloD.address
                                    launch {
                                        val srpKey = serverSrpHandshake(context, authSocket, clientAddr) { msg ->
                                            connectionStatus.value = context.getString(R.string.status_auth_failed_log, msg)
                                        }
                                        if (srpKey != null) {
                                            val srpSession = CryptoManager.createCipherSession(srpKey)
                                            val encKey = srpSession.encrypt(sharedKeyBytes)
                                            // [encKey length 4B] [encKey] [sessionPrefix 4B]
                                            val payload = ByteArray(4 + encKey.size + 4)
                                            payload[0] = (encKey.size shr 24).toByte()
                                            payload[1] = (encKey.size shr 16).toByte()
                                            payload[2] = (encKey.size shr 8).toByte()
                                            payload[3] = encKey.size.toByte()
                                            System.arraycopy(encKey, 0, payload, 4, encKey.size)
                                            System.arraycopy(sessionPrefix, 0, payload, 4 + encKey.size, 4)
                                            authSocket.send(Datagram(buildPacket { writeFully(payload) }, clientAddr))
                                            println("AUTH OK (multicast): sent session to $clientAddr")
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                            authSocket.close()
                        }
                    }

                    try {
                        if (isPasswordProtected) {
                            streamingLoop(targetAddress, bufferSize, cipherSession = multicastCipherSession)
                        } else {
                            streamingLoop(targetAddress, bufferSize, plainSession = CryptoManager.createPlainSession())
                        }
                    } finally {
                        try {
                            sendSocket?.send(Datagram(buildPacket { writeText("BYE") }, targetAddress))
                            println("--- Sent BYE to multicast group ---")
                        } catch (_: Exception) {}
                    }

                    // ─────────────────────────────────────────────
                    // UNICAST SERVER — loop: torna in waiting dopo ogni disconnect
                    // ─────────────────────────────────────────────
                } else {
                    setupAudioRecorders(bufferSize)
                    val localAddress = io.ktor.network.sockets.InetSocketAddress("0.0.0.0", streamingPort)
                    sendSocket = aSocket(SelectorManager(Dispatchers.IO)).udp().bind(localAddress) { reuseAddress = true }

                    while (isActive) {
                        startBroadcastingPresence(context, isMulticast = false, streamingPort, isPasswordProtected)
                        connectionStatus.value = context.getString(R.string.status_waiting_for_client, streamingPort)

                        val helloD = sendSocket.receive()
                        val clientAddress = helloD.address
                        if (helloD.packet.readText().trim() != NetworkSettings.CLIENT_HELLO_MESSAGE) continue

                        connectionStatus.value = context.getString(R.string.status_client_connected, clientAddress)
                        stopBroadcastingPresence()

                        // SRP Auth → CipherSession oppure PlainSession
                        var cipherSession: CryptoManager.CipherSession? = null
                        var serverPlainSession: CryptoManager.PlainSession? = null
                        if (isPasswordProtected) {
                            val aesKey = serverSrpHandshake(context, sendSocket, clientAddress) { msg ->
                                connectionStatus.value = context.getString(R.string.status_auth_failed_log, msg)
                            }
                            if (aesKey == null) continue
                            cipherSession = CryptoManager.createCipherSession(aesKey)
                            // Invia il sessionPrefix al client (4 byte non segreti):
                            // serve al client per ricostruire gli stessi IV che userà il server
                            sendSocket.send(Datagram(buildPacket { writeFully(cipherSession.getSessionPrefix()) }, clientAddress))
                        } else {
                            sendSocket.send(Datagram(buildPacket { writeText(NetworkSettings.AUTH_OK) }, clientAddress))
                            serverPlainSession = CryptoManager.createPlainSession()
                        }

                        // Drain AudioRecord
                        val drainBuf = ByteArray(bufferSize)
                        var drained: Int
                        do {
                            drained = internalRecord?.read(drainBuf, 0, drainBuf.size, AudioRecord.READ_NON_BLOCKING) ?: 0
                            micRecord?.read(drainBuf, 0, drainBuf.size, AudioRecord.READ_NON_BLOCKING)
                        } while (drained > 0)

                        sendSocket.send(Datagram(buildPacket { writeText("HELLO_ACK") }, clientAddress))

                        val clientAlive = AtomicBoolean(true)
                        val pingJob = launch {
                            var failures = 0
                            while (isActive && clientAlive.get()) {
                                delay(1000)
                                try { sendSocket.send(Datagram(buildPacket { writeText("PING") }, clientAddress)); failures = 0 }
                                catch (_: Exception) { if (++failures >= 3) clientAlive.set(false) }
                            }
                        }
                        try {
                            streamingLoop(clientAddress, bufferSize, clientAlive, cipherSession, serverPlainSession)
                        } finally {
                            pingJob.cancel()
                            if (clientAlive.get()) {
                                try { sendSocket.send(Datagram(buildPacket { writeText("BYE") }, clientAddress)) } catch (_: Exception) {}
                            }
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

    // =========================================================
    // CLIENT — connect to server
    // =========================================================
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startClient(
        context: Context,
        serverInfo: ServerInfo,
        sampleRate: Int,
        channelConfig: String,
        bufferSize: Int,
        sendMicrophone: Boolean,
        micPort: Int,
        password: String? = null,
        onServerDisconnected: (() -> Unit)? = null,
        onAuthDenied: (() -> Unit)? = null
    ) {
        stopStreaming(context)
        if (sendMicrophone) {
            micStreamingJob = scope.launchMicSenderJob(context, serverInfo, sampleRate, channelConfig, bufferSize, micPort)
        }

        streamingJob = scope.launch {
            try {
                // ─────────────────────────────────────────────
                // UNICAST CLIENT
                // ─────────────────────────────────────────────
                if (!serverInfo.isMulticast) {
                    var audioTrack: AudioTrack? = null
                    var socket: BoundDatagramSocket? = null
                    try {
                        val selectorManager = SelectorManager(Dispatchers.IO)
                        val channelConfigOut = if (channelConfig == "STEREO") AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
                        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, AudioFormat.ENCODING_PCM_16BIT)
                        val playbackBufferSize = minBuffer.coerceAtLeast(bufferSize * 2)

                        // FIX 1: AudioTrack aperto prima dell'handshake
                        audioTrack = AudioTrack.Builder()
                            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(channelConfigOut).build())
                            .setBufferSizeInBytes(playbackBufferSize)
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build()
                        audioTrack.play()

                        socket = aSocket(selectorManager).udp().bind()
                        val remoteAddress = io.ktor.network.sockets.InetSocketAddress(serverInfo.ip, serverInfo.port)

                        // Manda HELLO
                        connectionStatus.value = context.getString(R.string.status_contacting_server, serverInfo.ip)
                        socket.send(Datagram(buildPacket { writeText(NetworkSettings.CLIENT_HELLO_MESSAGE) }, remoteAddress))

                        // Auth → CipherSession con sessionPrefix del server
                        var cipherSession: CryptoManager.CipherSession? = null
                        var plainSession: CryptoManager.PlainSession? = null
                        if (serverInfo.isPasswordProtected) {
                            val pwd = password
                            if (pwd.isNullOrEmpty()) {
                                connectionStatus.value = context.getString(R.string.status_auth_required)
                                return@launch
                            }
                            connectionStatus.value = context.getString(R.string.status_authenticating)
                            val aesKey = clientSrpHandshake(pwd, socket, remoteAddress) {
                                connectionStatus.value = context.getString(R.string.status_auth_denied)
                                scope.launch(Dispatchers.Main) { onAuthDenied?.invoke() }
                            }
                            if (aesKey == null) return@launch
                            // Ricevi il sessionPrefix (4 byte) dal server
                            val prefixBytes = withTimeout(5_000) { socket.receive() }.let {
                                val b = ByteArray(it.packet.remaining.toInt()); it.packet.readFully(b); b
                            }
                            if (prefixBytes.size != CryptoManager.NONCE_BYTES) {
                                connectionStatus.value = "Auth error: bad session prefix"
                                return@launch
                            }
                            // Crea una receive-session sincronizzata col nonce del server
                            cipherSession = CryptoManager.createCipherSession(aesKey).createPeerSession(prefixBytes)
                        } else {
                            val authMsg = withTimeout(10_000) { socket.receive() }.packet.readText().trim()
                            if (authMsg != NetworkSettings.AUTH_OK) throw Exception("Unexpected auth response: $authMsg")
                            // Modalità aperta: jitter buffer senza cifratura
                            plainSession = CryptoManager.createPlainSession()
                        }

                        // Aspetta HELLO_ACK
                        connectionStatus.value = context.getString(R.string.status_waiting_for_ack)
                        val ackMsg = withTimeout(15_000) { socket.receive() }.packet.readText().trim()
                        if (ackMsg != "HELLO_ACK") throw Exception(context.getString(R.string.status_handshake_failed_unexpected_response, ackMsg))

                        connectionStatus.value = context.getString(R.string.status_streaming)

                        var lastPingReceived = System.currentTimeMillis()
                        val pingTimeoutMs = 3000L
                        var serverDisconnected = false
                        val serverAlive = AtomicBoolean(true)

                        val watchdogJob = launch {
                            while (isActive && serverAlive.get()) {
                                delay(1000)
                                if (System.currentTimeMillis() - lastPingReceived > pingTimeoutMs) {
                                    serverDisconnected = true; serverAlive.set(false)
                                }
                            }
                        }

                        try {
                            while (isActive && serverAlive.get()) {
                                val rawBytes = socket.receive().let {
                                    val b = ByteArray(it.packet.remaining.toInt()); it.packet.readFully(b); b
                                }
                                val text = rawBytes.toString(Charsets.UTF_8).trim()
                                when (text) {
                                    "PING" -> lastPingReceived = System.currentTimeMillis()
                                    "BYE"  -> { serverDisconnected = true; serverAlive.set(false) }
                                    else   -> {
                                        // receive() gestisce il jitter buffer e ritorna frame in ordine
                                        val frames = cipherSession?.receive(rawBytes)
                                            ?: plainSession?.receive(rawBytes)
                                            ?: listOf(rawBytes)
                                        for (audio in frames) {
                                            if (audio.isNotEmpty()) audioTrack.write(audio, 0, audio.size, AudioTrack.WRITE_BLOCKING)
                                        }
                                    }
                                }
                            }
                        } finally {
                            watchdogJob.cancel()
                            if (serverDisconnected) scope.launch(Dispatchers.Main) {
                                stopStreaming(context)
                                onServerDisconnected?.invoke()
                            }
                        }
                    } finally {
                        audioTrack?.stop(); audioTrack?.release()
                        socket?.close()
                    }

                    // ─────────────────────────────────────────────
                    // MULTICAST CLIENT
                    // ─────────────────────────────────────────────
                } else {
                    var audioTrack: AudioTrack? = null
                    var multicastSocket: MulticastSocket? = null
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val multicastLock = wifiManager.createMulticastLock("wifi_audio_streamer_multicast_lock")
                    try {
                        multicastLock.acquire()

                        // Auth prima di unirsi al gruppo
                        var cipherSession: CryptoManager.CipherSession? = null
                        if (serverInfo.isPasswordProtected) {
                            val pwd = password
                            if (pwd.isNullOrEmpty()) {
                                connectionStatus.value = context.getString(R.string.status_auth_required)
                                return@launch
                            }
                            connectionStatus.value = context.getString(R.string.status_authenticating)
                            val authSocket = aSocket(SelectorManager(Dispatchers.IO)).udp().bind()
                            val authAddress = io.ktor.network.sockets.InetSocketAddress(serverInfo.ip, authPort(serverInfo.port))
                            try {
                                authSocket.send(Datagram(buildPacket { writeText(NetworkSettings.CLIENT_HELLO_MESSAGE) }, authAddress))
                                val srpKey = clientSrpHandshake(pwd, authSocket, authAddress) {
                                    connectionStatus.value = context.getString(R.string.status_auth_denied)
                                    scope.launch(Dispatchers.Main) { onAuthDenied?.invoke() }
                                }
                                if (srpKey == null) return@launch

                                // Ricevi payload: [encKeyLen 4B] [encKey] [sessionPrefix 4B]
                                val payload = withTimeout(10_000) { authSocket.receive() }.let {
                                    val b = ByteArray(it.packet.remaining.toInt()); it.packet.readFully(b); b
                                }
                                val encKeyLen = ((payload[0].toInt() and 0xFF) shl 24) or
                                        ((payload[1].toInt() and 0xFF) shl 16) or
                                        ((payload[2].toInt() and 0xFF) shl 8)  or
                                        (payload[3].toInt() and 0xFF)
                                if (payload.size < 4 + encKeyLen + 4) {
                                    connectionStatus.value = context.getString(R.string.status_auth_denied)
                                    return@launch
                                }
                                // Decifra la shared key usando la nostra CipherSession SRP
                                val srpSession = CryptoManager.createCipherSession(srpKey)
                                val encKey = payload.copyOfRange(4, 4 + encKeyLen)
                                val sharedKeyBytes = srpSession.decrypt(encKey) ?: run {
                                    connectionStatus.value = context.getString(R.string.status_auth_denied)
                                    return@launch
                                }
                                val sessionPrefix = payload.copyOfRange(4 + encKeyLen, 4 + encKeyLen + 4)
                                // Ricostruisce una CipherSession di decrypt sincronizzata col server
                                val sharedAesKey = SecretKeySpec(sharedKeyBytes, "AES")
                                cipherSession = CryptoManager.createCipherSession(sharedAesKey).createPeerSession(sessionPrefix)
                            } finally {
                                authSocket.close()
                            }
                        }

                        connectionStatus.value = context.getString(R.string.status_joining_multicast)
                        val groupAddress = InetAddress.getByName(serverInfo.multicastGroupIp)
                        multicastSocket = MulticastSocket(serverInfo.port).apply {
                            getWifiNetworkInterface()?.let { networkInterface = it }
                            joinGroup(groupAddress)
                        }

                        val channelConfigOut = if (channelConfig == "STEREO") AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
                        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, AudioFormat.ENCODING_PCM_16BIT)
                        val playbackBufferSize = minBuffer.coerceAtLeast(bufferSize * 2)
                        audioTrack = AudioTrack.Builder()
                            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(channelConfigOut).build())
                            .setBufferSizeInBytes(playbackBufferSize)
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build()
                        audioTrack.play()
                        connectionStatus.value = context.getString(R.string.status_streaming)

                        // Buffer abbastanza grande: audio + overhead cifratura (SEQ 8B + IV 12B + tag 16B)
                        val buf = ByteArray(bufferSize * 2 + CryptoManager.SEQ_BYTES + CryptoManager.IV_BYTES + CryptoManager.TAG_BYTES)
                        val packet = DatagramPacket(buf, buf.size)
                        var serverDisconnected = false

                        // PlainSession per jitter buffer in modalità non cifrata
                        val plainSession = if (cipherSession == null) CryptoManager.createPlainSession() else null

                        while (isActive) {
                            multicastSocket.receive(packet)
                            val rawBytes = packet.data.copyOf(packet.length)
                            if (packet.length == 3 && String(rawBytes, Charsets.UTF_8) == "BYE") {
                                serverDisconnected = true; break
                            }
                            // receive() gestisce jitter buffer e ritorna frame in ordine
                            val frames = cipherSession?.receive(rawBytes)
                                ?: plainSession?.receive(rawBytes)
                                ?: listOf(rawBytes)
                            for (audio in frames) {
                                if (audio.isNotEmpty()) audioTrack.write(audio, 0, audio.size, AudioTrack.WRITE_BLOCKING)
                            }
                        }

                        if (serverDisconnected) scope.launch(Dispatchers.Main) {
                            stopStreaming(context)
                            onServerDisconnected?.invoke()
                        }
                    } finally {
                        audioTrack?.stop(); audioTrack?.release()
                        try {
                            multicastSocket?.leaveGroup(InetAddress.getByName(serverInfo.multicastGroupIp))
                        } catch (_: Exception) {}
                        multicastSocket?.close()
                        if (multicastLock.isHeld) multicastLock.release()
                    }
                }
            } catch (e: BindException) {
                connectionStatus.value = context.getString(R.string.status_port_in_use)
            } catch (e: Exception) {
                if (e !is CancellationException) connectionStatus.value = context.getString(R.string.status_client_error, e.message)
            } finally {
                micStreamingJob?.cancel()
                micStreamingJob = null
                if (isActive) connectionStatus.value = context.getString(R.string.status_client_stopped)
            }
        }
    }

    // =========================================================
    // Stop & cleanup
    // =========================================================
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