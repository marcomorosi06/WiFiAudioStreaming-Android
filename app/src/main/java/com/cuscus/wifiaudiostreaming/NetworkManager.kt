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
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.media.projection.MediaProjection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.glance.appwidget.state.updateAppWidgetState
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import com.cuscus.wifiaudiostreaming.data.SettingsDataStore
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

data class ServerInfo(
    val ip: String,
    val isMulticast: Boolean,
    val port: Int,
    val securityMode: String? = null,
    val encrypted: Boolean = false,
    val serverSendsMic: Boolean = false,
    val serverWantsMic: Boolean = false,
    /** Formato audio annunciato dal server nel beacon di discovery, se presente. */
    val audioFormat: StreamAudioFormat? = null,
    /** Istante dell'ultimo beacon ricevuto: serve a far scadere i server spariti. */
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * Formato audio dichiarato da un server WFAS (campi sr/ch/bd del beacon).
 * La pipeline di riproduzione e' PCM 16 bit: qualunque altra profondita' non e'
 * riproducibile e va rifiutata invece di essere interpretata a caso.
 */
data class StreamAudioFormat(
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int
) {
    val isPlayable: Boolean
        get() = bitDepth == 16 &&
                channels in 1..2 &&
                sampleRate in 4000..192000

    fun describe(): String = "$sampleRate Hz, " +
            (if (channels == 1) "mono" else "stereo") + ", $bitDepth bit"

    companion object {
        /** Estrae sr/ch/bd da un beacon gia' diviso su ';'. Null se assenti o non numerici. */
        fun fromBeaconParts(parts: List<String>): StreamAudioFormat? {
            val sr = parts.firstOrNull { it.startsWith("sr=") }?.removePrefix("sr=")
                ?.toFloatOrNull()?.toInt() ?: return null
            val ch = parts.firstOrNull { it.startsWith("ch=") }?.removePrefix("ch=")
                ?.toIntOrNull() ?: return null
            val bd = parts.firstOrNull { it.startsWith("bd=") }?.removePrefix("bd=")
                ?.toIntOrNull() ?: 16
            return StreamAudioFormat(sr, ch, bd)
        }
    }
}

data class ProtocolMismatch(val localVersion: Int, val remoteVersion: Int)

@SuppressLint("MissingPermission")
object NetworkManager {

    private const val TAG = "WFAS_DBG"

    /**
     * Un server annuncia la propria presenza ogni 3 secondi. Tolleriamo tre
     * beacon persi prima di considerarlo sparito, cosi' un pacchetto smarrito
     * non lo fa lampeggiare dentro e fuori dalla lista.
     */
    private const val DISCOVERY_TTL_MS = 10_000L

    // --- GESTIONE VOLUME SERVER ANDROID ---
    val serverVolume = MutableStateFlow(1.0f)
    var isServerStreaming = false
    @Volatile var serverStreamsMic = false
    // --------------------------------------

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var donationTimerJob: Job? = null

    fun startDonationTimer(context: Context) {
        donationTimerJob?.cancel()
        val appCtx = context.applicationContext
        donationTimerJob = scope.launch {
            delay(3 * 60 * 1000L)
            com.cuscus.wifiaudiostreaming.data.SettingsDataStore(appCtx).setDonationQualified(true)
        }
    }
    fun cancelDonationTimer() { donationTimerJob?.cancel(); donationTimerJob = null }

    private var streamingJob: Job? = null
    private var listeningJob: Job? = null
    private var broadcastingJob: Job? = null
    private var originalMediaVolume: Int? = null
    private var micStreamingJob: Job? = null
    @Volatile private var rtpPcmQueue: java.util.concurrent.ArrayBlockingQueue<ByteArray>? = null
    private var rtpJob: Job? = null

    @Volatile private var httpPcmQueue: java.util.concurrent.ArrayBlockingQueue<ByteArray>? = null

    @Volatile private var activeInternalRecord: AudioRecord? = null
    @Volatile private var activeMicRecord: AudioRecord? = null
    private var httpJob: Job? = null
    private var httpServerSocket: java.net.ServerSocket? = null

    private object NetworkSettings {
        const val DISCOVERY_PORT = 9091
        const val DISCOVERY_MESSAGE = "WIFI_AUDIO_STREAMER_DISCOVERY"
        const val CLIENT_HELLO_MESSAGE = "HELLO_FROM_CLIENT"
        const val MULTICAST_GROUP_IP = "239.255.0.1"
        const val INCOMPATIBLE_PREFIX = "WFAS_INCOMPATIBLE"
        const val HELLO_ACK_PREFIX = "HELLO_ACK"
        const val PENDING_MESSAGE = "WFAS_PENDING"
        const val AUTH_REQUIRED_PREFIX = "WFAS_AUTH_REQUIRED"
        const val UNAUTHORIZED_MESSAGE = "WFAS_UNAUTHORIZED"

        /**
         * Il server unicast serve un client alla volta: a chiunque altro bussi
         * mentre e' occupato risponde subito questo, invece di lasciarlo
         * ritentare a vuoto per 30 secondi. I client che non lo conoscono
         * semplicemente lo ignorano e vanno in timeout come prima.
         */
        const val BUSY_MESSAGE = "WFAS_BUSY"
    }

    const val WFAS_PROTOCOL_VERSION = 2

    @Volatile var securityMode: String = "OFF"
    @Volatile var authKey: String = ""
    @Volatile var encryptionEnabled: Boolean = false
    fun configureSecurity(mode: String, key: String, encrypt: Boolean = false) {
        securityMode = mode; authKey = key; encryptionEnabled = encrypt
    }

    @Volatile private var audioPrewarmed = false
    fun prewarmAudio() {
        if (audioPrewarmed) return
        audioPrewarmed = true
        Thread {
            runCatching {
                val sr = 48000
                val ch = AudioFormat.CHANNEL_OUT_STEREO
                val minBuf = AudioTrack.getMinBufferSize(sr, ch, AudioFormat.ENCODING_PCM_16BIT)
                if (minBuf <= 0) return@runCatching
                val builder = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sr)
                            .setChannelMask(ch)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                }
                val track = builder.build()
                track.write(ByteArray(minBuf), 0, minBuf, AudioTrack.WRITE_BLOCKING)
                track.play()
                Thread.sleep(80)
                runCatching { track.stop() }
                track.release()
            }
        }.apply { isDaemon = true; start() }
    }
    // Pre-shared key used only when acting as a CLIENT (scripting). The interactive
    // client leaves this empty and gets the key from the on-connect dialog.
    @Volatile var clientPresharedKey: String = ""
    @Volatile var micSendDir: WfasCrypto.Dir? = null
    var onAuthRequest: ((peer: String) -> Boolean)? = null

    @Volatile var keyPromptEnabled: Boolean = false
    val pendingKeyRequest = MutableStateFlow<Boolean?>(null)
    private var keyDeferred: CompletableDeferred<String?>? = null
    suspend fun requestKeyFromUi(wrong: Boolean): String? {
        if (!keyPromptEnabled) return null
        val d = CompletableDeferred<String?>()
        keyDeferred = d
        pendingKeyRequest.value = wrong
        return try { d.await() } finally { pendingKeyRequest.value = null }
    }
    fun submitKey(key: String?) { keyDeferred?.complete(key); keyDeferred = null }

    @Volatile var authPromptEnabled: Boolean = false
    val pendingAuthRequest = MutableStateFlow<String?>(null)
    private var authDeferred: CompletableDeferred<Boolean>? = null
    suspend fun requestAuthFromUi(peer: String): Boolean {
        if (!authPromptEnabled) return true
        val d = CompletableDeferred<Boolean>()
        authDeferred = d
        pendingAuthRequest.value = peer
        return try {
            withTimeoutOrNull(60_000) { d.await() } ?: false
        } finally {
            pendingAuthRequest.value = null
        }
    }
    fun submitAuth(allow: Boolean) { authDeferred?.complete(allow); authDeferred = null }

    private const val MIC_HEADER_SIZE = 10
    private const val MIC_MAGIC_0: Byte = 0x57
    private const val MIC_MAGIC_1: Byte = 0x46

    private fun writeMicHeader(dst: ByteArray, seq: Int, silence: Boolean) {
        dst[0] = MIC_MAGIC_0
        dst[1] = MIC_MAGIC_1
        dst[2] = WFAS_PROTOCOL_VERSION.toByte()
        dst[3] = if (silence) 0x01 else 0x00
        dst[4] = ((seq shr 8) and 0xFF).toByte()
        dst[5] = (seq and 0xFF).toByte()
        dst[6] = 0; dst[7] = 0; dst[8] = 0; dst[9] = 0
    }

    val protocolMismatch = MutableStateFlow<ProtocolMismatch?>(null)

    private fun clientHelloMessage(): String = "${NetworkSettings.CLIENT_HELLO_MESSAGE};v=$WFAS_PROTOCOL_VERSION"
    private fun helloAckMessage(): String = "${NetworkSettings.HELLO_ACK_PREFIX};v=$WFAS_PROTOCOL_VERSION"
    private fun incompatibleMessage(): String = "${NetworkSettings.INCOMPATIBLE_PREFIX};v=$WFAS_PROTOCOL_VERSION"

    private fun parseProtocolVersion(message: String): Int =
        message.split(";").firstOrNull { it.startsWith("v=") }
            ?.removePrefix("v=")?.trim()?.toIntOrNull() ?: 0

    private fun signalProtocolMismatch(remoteVersion: Int) {
        if (protocolMismatch.value == null) {
            protocolMismatch.value = ProtocolMismatch(WFAS_PROTOCOL_VERSION, remoteVersion)
        }
    }

    fun clearProtocolMismatch() { protocolMismatch.value = null }

    val connectionStatus = MutableStateFlow("")
    val discoveredDevices = MutableStateFlow<Map<String, ServerInfo>>(emptyMap())
    val isStreamingCurrent = MutableStateFlow(false)
    val isMicMuted = MutableStateFlow(false)
    var autoConnectOwnsListening = false
    val lastSeenDevices = mutableMapOf<String, Pair<ServerInfo, Long>>()

    // ── Riduzione del rumore lato ricevitore (opt-in, modalita' sviluppatore) ──
    private val noiseReducer = com.cuscus.wifiaudiostreaming.dsp.NoiseReducer()
    @Volatile private var nrEnabled = false
    private var nrScratch = ShortArray(0)

    /** Modificabile a caldo: il flusso in riproduzione non va riavviato. */
    fun setNoiseReduction(enabled: Boolean, strengthPercent: Int) {
        noiseReducer.setStrength(strengthPercent.coerceIn(0, 100) / 100f)
        if (enabled && !nrEnabled) noiseReducer.reset()
        nrEnabled = enabled
        Log.d(TAG, "[NR] enabled=$enabled strength=$strengthPercent%")
    }

    private suspend fun prepareNoiseReducer(context: Context, sampleRate: Int, channels: Int) {
        // suspend, non runBlocking: viene chiamata da dentro una coroutine e
        // bloccare quel thread potrebbe incastrare il dispatcher.
        val s = runCatching { SettingsDataStore(context).settingsFlow.first() }.getOrNull()
        val on = s != null && s.developerMode && s.noiseReductionEnabled
        noiseReducer.init(sampleRate, channels)
        noiseReducer.setStrength((s?.noiseReductionStrength ?: 50).coerceIn(0, 100) / 100f)
        noiseReducer.reset()
        nrEnabled = on
        Log.d(TAG, "[NR] prepare: on=$on sr=$sampleRate ch=$channels")
    }

    /**
     * Applica il denoiser a PCM 16 bit little endian, in place.
     * Non fa nulla se disattivato: il percorso normale resta a costo zero.
     */
    private fun denoiseInPlace(buf: ByteArray, offset: Int, len: Int) {
        if (!nrEnabled || len < 2) return
        val samples = len / 2
        if (nrScratch.size < samples) nrScratch = ShortArray(samples)
        val sc = nrScratch
        var bi = offset
        for (i in 0 until samples) {
            sc[i] = ((buf[bi].toInt() and 0xFF) or (buf[bi + 1].toInt() shl 8)).toShort()
            bi += 2
        }
        noiseReducer.process(sc, 0, samples)
        bi = offset
        for (i in 0 until samples) {
            val v = sc[i].toInt()
            buf[bi] = (v and 0xFF).toByte()
            buf[bi + 1] = ((v shr 8) and 0xFF).toByte()
            bi += 2
        }
    }

    @SuppressLint("DefaultLocale", "MissingPermission")
    fun getLocalIpAddress(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val transportPriority = listOf(
                    NetworkCapabilities.TRANSPORT_VPN,
                    NetworkCapabilities.TRANSPORT_WIFI,
                    NetworkCapabilities.TRANSPORT_ETHERNET
                )
                for (transport in transportPriority) {
                    for (network in cm.allNetworks) {
                        val nc = cm.getNetworkCapabilities(network) ?: continue
                        if (!nc.hasTransport(transport)) continue
                        val lp = cm.getLinkProperties(network) ?: continue
                        for (la in lp.linkAddresses) {
                            val addr = la.address
                            if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                                return addr.hostAddress ?: continue
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
        } else {
            try {
                @Suppress("DEPRECATION")
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
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
            } catch (e: Exception) {}
        }
        try {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { intf ->
                if (!intf.isLoopback && intf.isUp) {
                    intf.inetAddresses?.toList()?.forEach { addr ->
                        if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                            val h = addr.hostAddress ?: return@forEach
                            if (!h.startsWith("192.168.112") && !h.startsWith("192.168.42")) {
                                return h
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return "0.0.0.0"
    }

    @SuppressLint("MissingPermission")
    private fun isVpnActive(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.allNetworks.any { network ->
                cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        } catch (e: Exception) { false }
    }

    @SuppressLint("MissingPermission")
    private fun getWifiNetworkObject(context: Context): android.net.Network? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.allNetworks.firstOrNull { network ->
                val nc = cm.getNetworkCapabilities(network) ?: return@firstOrNull false
                nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
        } catch (e: Exception) { null }
    }

    suspend fun probeIsMulticast(ip: String, port: Int): Boolean {
        return try {
            var isUnicast = false
            withTimeout(1000) { // Aspetta massimo 1 secondo
                aSocket(SelectorManager(Dispatchers.IO)).udp().bind().use { sock ->
                    val remoteAddress = InetSocketAddress(ip, port)
                    sock.send(Datagram(buildPacket { writeText("MODE_PROBE") }, remoteAddress))
                    val ack = sock.receive()
                    if (ack.packet.readText().trim() == "UNICAST") {
                        isUnicast = true
                    }
                }
            }
            !isUnicast
        } catch (e: Exception) {
            true // Se va in timeout, assumiamo Multicast
        }
    }

    private fun CoroutineScope.launchRtpSidecar(
        sampleRate: Int,
        channels: Int,
        port: Int,
        isMulticast: Boolean,
        clientIp: String?, // Usato se siamo in Unicast
        wifiIface: NetworkInterface?
    ) = launch(Dispatchers.IO) {
        val queue = java.util.concurrent.ArrayBlockingQueue<ByteArray>(25)
        rtpPcmQueue = queue

        var sequenceNumber = (Math.random() * 65535).toInt()
        var rtpTimestamp = (Math.random() * Int.MAX_VALUE).toLong()
        val ssrc = (Math.random() * Int.MAX_VALUE).toLong()

        val socket = if (isMulticast) {
            MulticastSocket().apply {
                timeToLive = 4
                wifiIface?.let { networkInterface = it }
            }
        } else DatagramSocket()

        val destAddress = if (isMulticast) {
            InetAddress.getByName("239.255.0.1")
        } else {
            InetAddress.getByName(clientIp ?: "255.255.255.255")
        }

        try {
            while (isActive) {
                val pcmLeBytes = queue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                val maxPayloadSize = 1400
                var offset = 0

                while (offset < pcmLeBytes.size) {
                    val chunkSize = minOf(maxPayloadSize, pcmLeBytes.size - offset)
                    val rtpPacket = ByteArray(12 + chunkSize)
                    val buf = java.nio.ByteBuffer.wrap(rtpPacket).order(java.nio.ByteOrder.BIG_ENDIAN)

                    // Header RTP
                    buf.put(0x80.toByte()); buf.put(96.toByte())
                    buf.putShort((sequenceNumber and 0xFFFF).toShort())
                    buf.putInt((rtpTimestamp and 0xFFFFFFFFL).toInt())
                    buf.putInt((ssrc and 0xFFFFFFFFL).toInt())

                    // Conversione Little-Endian -> Big-Endian (Network Byte Order)
                    val leBuf = java.nio.ByteBuffer.wrap(pcmLeBytes, offset, chunkSize).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val beBuf = buf.asShortBuffer()
                    while (leBuf.hasRemaining()) beBuf.put(leBuf.get())

                    runCatching { socket.send(DatagramPacket(rtpPacket, rtpPacket.size, destAddress, port)) }

                    sequenceNumber = (sequenceNumber + 1) and 0xFFFF
                    rtpTimestamp += (chunkSize / 2 / channels)
                    offset += chunkSize
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) e.printStackTrace()
        } finally {
            socket.close()
            rtpPcmQueue = null
        }
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

    private fun getWifiNetworkInterface(preferredName: String = "Auto"): NetworkInterface? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()

            if (preferredName != "Auto") {
                // Se l'utente ha forzato una scheda, ignoriamo i filtri (fondamentale per le VPN)
                interfaces.firstOrNull { it.displayName == preferredName || it.name == preferredName }
            } else {
                interfaces.firstOrNull { iface ->
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
            }
        } catch (e: Exception) {
            null
        }
    }

    fun startListeningForDevices(context: Context, networkInterfaceName: String = "Auto") {
        if (isListeningActive()) return
        discoveredDevices.value = emptyMap()
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicastLock = wifiManager.createMulticastLock("wifi_audio_streamer_discovery_lock")
        multicastLock.setReferenceCounted(true)

        // Un server che sparisce senza dire BYE (app chiusa, WiFi staccato, crash)
        // resterebbe in lista per sempre. Il beacon arriva ogni 3s: dopo DISCOVERY_TTL_MS
        // senza notizie lo consideriamo andato.
        scope.launch {
            while (isActive) {
                delay(2000)
                val now = System.currentTimeMillis()
                val current = discoveredDevices.value
                val alive = current.filterValues { now - it.lastSeen <= DISCOVERY_TTL_MS }
                if (alive.size != current.size) {
                    val gone = current.keys - alive.keys
                    Log.d(TAG, "[DISCOVERY] Scaduti (nessun beacon da ${DISCOVERY_TTL_MS}ms): $gone")
                    discoveredDevices.value = alive
                }
            }
        }

        listeningJob = scope.launch {
            multicastLock.acquire()
            try {
                while (isActive) {
                    var socket: MulticastSocket? = null
                    try {
                        val localIps = getAllLocalIpAddresses()
                        val groupAddress = InetAddress.getByName(NetworkSettings.MULTICAST_GROUP_IP)

                        socket = MulticastSocket(NetworkSettings.DISCOVERY_PORT).apply {
                            getWifiNetworkInterface(networkInterfaceName)?.let { networkInterface = it }
                            joinGroup(groupAddress)
                            soTimeout = 5000
                        }

                        val buffer = ByteArray(1024)
                        val packet = DatagramPacket(buffer, buffer.size)

                        while (isActive) {
                            try {
                                packet.length = buffer.size
                                socket.receive(packet)
                                val remoteIp = packet.address.hostAddress
                                val message = String(packet.data, 0, packet.length).trim()

                                if (remoteIp != null && remoteIp !in localIps && message.startsWith(NetworkSettings.DISCOVERY_MESSAGE)) {
                                    Log.d(TAG, "[DISCOVERY] Received from $remoteIp: $message")
                                    val parts = message.split(";")
                                    if (parts.size >= 4) {
                                        val hostname = parts[1]

                                        if (message.contains("BYE")) {
                                            Log.d(TAG, "[DISCOVERY] BYE from $hostname, removing from list")
                                            discoveredDevices.value = discoveredDevices.value - hostname
                                        } else {
                                            val isMulticast = parts[2].equals("MULTICAST", ignoreCase = true)
                                            val port = parts[3].toIntOrNull() ?: continue
                                            val authMode = parts.firstOrNull { it.startsWith("auth=") }
                                                ?.removePrefix("auth=")?.uppercase()
                                            val encrypted = parts.firstOrNull { it.startsWith("enc=") }
                                                ?.removePrefix("enc=") == "1"
                                            val micTok = parts.firstOrNull { it.startsWith("mic=") }?.removePrefix("mic=")
                                            val advertisedFormat = StreamAudioFormat.fromBeaconParts(parts)
                                            val serverInfo = ServerInfo(
                                                ip = remoteIp, isMulticast = isMulticast, port = port,
                                                securityMode = authMode, encrypted = encrypted,
                                                serverSendsMic = micTok?.contains("tx") == true,
                                                serverWantsMic = micTok?.contains("rx") == true,
                                                audioFormat = advertisedFormat,
                                                lastSeen = System.currentTimeMillis()
                                            )
                                            Log.d(TAG, "[DISCOVERY] Found server: hostname=$hostname ip=$remoteIp isMulticast=$isMulticast port=$port")

                                            val currentMap = discoveredDevices.value
                                            val known = currentMap[hostname]
                                            // lastSeen cambia a ogni beacon: confrontarlo
                                            // farebbe riemettere la lista ogni 3s. Si
                                            // aggiorna quando cambia qualcosa di reale, o
                                            // periodicamente per tenere vivo il TTL.
                                            val sameData = known != null &&
                                                known.copy(lastSeen = serverInfo.lastSeen) == serverInfo
                                            val staleTimestamp = known != null &&
                                                serverInfo.lastSeen - known.lastSeen > DISCOVERY_TTL_MS / 3
                                            if (!sameData || staleTimestamp) {
                                                discoveredDevices.value = currentMap + (hostname to serverInfo)
                                            }
                                        }
                                    }
                                }
                            } catch (e: SocketTimeoutException) {
                                continue
                            } catch (e: Exception) {
                                if (e !is CancellationException) break
                            }
                        }
                    } catch (e: Exception) {
                        delay(5000)
                    } finally {
                        try {
                            socket?.leaveGroup(InetAddress.getByName(NetworkSettings.MULTICAST_GROUP_IP))
                            socket?.close()
                        } catch (e: Exception) {}
                    }
                }
            } finally {
                if (multicastLock.isHeld) multicastLock.release()
            }
        }
    }

    fun startBroadcastingPresence(
        context: Context,
        isMulticast: Boolean,
        streamingPort: Int,
        networkInterfaceName: String = "Auto",
        rtpEnabled: Boolean = false,
        audioFormat: StreamAudioFormat? = null
    ) {
        if (broadcastingJob?.isActive == true) return
        Log.d(TAG, "[BROADCAST] startBroadcastingPresence: isMulticast=$isMulticast port=$streamingPort iface=$networkInterfaceName rtp=$rtpEnabled")
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
            val protocolsStr = if (rtpEnabled) "protocols=WFAS,RTP" else "protocols=WFAS"
            val micStr = if (serverStreamsMic) ";mic=tx" else ""
            // Il formato va annunciato: senza, un ricevitore conforme non puo'
            // adottarlo e ricadrebbe sulle proprie impostazioni locali.
            val audioStr = audioFormat?.let {
                ";sr=${it.sampleRate};ch=${it.channels};bd=${it.bitDepth}"
            } ?: ""
            val staticPrefix = "${NetworkSettings.DISCOVERY_MESSAGE};$deviceName;$mode;$streamingPort;$protocolsStr$audioStr"

            val groupAddress = InetAddress.getByName(NetworkSettings.MULTICAST_GROUP_IP)
            val wifiIface = getWifiNetworkInterface(networkInterfaceName)

            var socket: MulticastSocket? = null
            try {
                socket = MulticastSocket().apply {
                    timeToLive = 4
                    wifiIface?.let { networkInterface = it }
                }

                while (isActive) {
                    try {
                        val encOn = encryptionEnabled && securityMode.equals("KEY", ignoreCase = true)
                        val secStr = ";auth=$securityMode;enc=${if (encOn) 1 else 0}"
                        val message = "$staticPrefix$secStr$micStr"
                        val messageBytes = message.toByteArray()
                        val packet = DatagramPacket(
                            messageBytes,
                            messageBytes.size,
                            groupAddress,
                            NetworkSettings.DISCOVERY_PORT
                        )
                        socket.send(packet)
                        Log.d(TAG, "[BROADCAST] Sent: $message  iface=${wifiIface?.name ?: "default"}")
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

    /**
     * Manda subito un beacon BYE: i client tolgono il server dalla lista senza
     * aspettare la scadenza. Best effort, un pacchetto perso non e' un problema
     * perche' comunque il TTL fa il resto.
     */
    fun announceServerGone(context: Context, networkInterfaceName: String = "Auto") {
        scope.launch {
            runCatching {
                val deviceName = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
                            ?: "${Build.MANUFACTURER} ${Build.MODEL}"
                    } else {
                        "${Build.MANUFACTURER} ${Build.MODEL}"
                    }
                } catch (e: Exception) { "${Build.MANUFACTURER} ${Build.MODEL}" }

                val message = "${NetworkSettings.DISCOVERY_MESSAGE};$deviceName;BYE;0"
                val bytes = message.toByteArray()
                val group = InetAddress.getByName(NetworkSettings.MULTICAST_GROUP_IP)
                MulticastSocket().use { sock ->
                    sock.timeToLive = 4
                    getWifiNetworkInterface(networkInterfaceName)?.let { sock.networkInterface = it }
                    repeat(2) {
                        sock.send(DatagramPacket(bytes, bytes.size, group, NetworkSettings.DISCOVERY_PORT))
                        delay(120)
                    }
                }
                Log.d(TAG, "[BROADCAST] BYE annunciato per '$deviceName'")
            }.onFailure { Log.w(TAG, "[BROADCAST] BYE non inviato: ${it.message}") }
        }
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
        var aec: AcousticEchoCanceler? = null
        var ns: NoiseSuppressor? = null
        var agc: AutomaticGainControl? = null

        try {
            val channelConfigIn = if (channelConfig == "STEREO") AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, AudioFormat.ENCODING_PCM_16BIT)
            var micBufferSize = bufferSize.coerceAtLeast(minBufferSize)

            val frameSize = if (channelConfig == "STEREO") 4 else 2
            if (micBufferSize % frameSize != 0) {
                micBufferSize += frameSize - (micBufferSize % frameSize)
            }

            fun buildRecord(source: Int): AudioRecord? = runCatching {
                val rec = AudioRecord(source, sampleRate, channelConfigIn, AudioFormat.ENCODING_PCM_16BIT, micBufferSize)
                if (rec.state == AudioRecord.STATE_INITIALIZED) rec
                else { runCatching { rec.release() }; null }
            }.getOrNull()

            micRecord = buildRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                ?: buildRecord(MediaRecorder.AudioSource.MIC)
                ?: throw IllegalStateException("AudioRecord init failed")

            val sessionId = micRecord.audioSessionId
            if (AcousticEchoCanceler.isAvailable()) {
                aec = runCatching { AcousticEchoCanceler.create(sessionId)?.apply { enabled = true } }.getOrNull()
            }
            if (NoiseSuppressor.isAvailable()) {
                ns = runCatching { NoiseSuppressor.create(sessionId)?.apply { enabled = true } }.getOrNull()
            }
            if (AutomaticGainControl.isAvailable()) {
                agc = runCatching { AutomaticGainControl.create(sessionId)?.apply { enabled = true } }.getOrNull()
            }
            Log.d(TAG, "[MIC_SEND] session=$sessionId aec=${aec?.enabled} ns=${ns?.enabled} agc=${agc?.enabled}")

            socket = DatagramSocket().apply { sendBufferSize = 1 shl 20 }
            val destinationAddress = InetAddress.getByName(serverInfo.ip)

            micRecord.startRecording()
            println("Invio microfono a ${serverInfo.ip}:$micPort")

            val chunkBytes = SettingsDataStore(context).settingsFlow.first().maxPayloadBytes.coerceIn(256, 1400 - MIC_HEADER_SIZE - WfasCrypto.AEAD_OVERHEAD)
            val buffer = ByteArray(micBufferSize)
            val packetBuffer = ByteArray(MIC_HEADER_SIZE + chunkBytes)
            var seq = 0
            var lastMutedSent = false
            fun sendMicPacket(silence: Boolean, src: ByteArray?, off: Int, len: Int) {
                val md = micSendDir
                if (md != null) {
                    val payload = if (!silence && src != null && len > 0) src.copyOfRange(off, off + len) else ByteArray(0)
                    val enc = WfasCrypto.encryptPacket(md, seq, 0, silence, payload)
                    runCatching { socket!!.send(DatagramPacket(enc, enc.size, destinationAddress, micPort)) }
                } else {
                    writeMicHeader(packetBuffer, seq, silence)
                    val plen = if (silence) MIC_HEADER_SIZE else MIC_HEADER_SIZE + len
                    if (!silence && src != null && len > 0) System.arraycopy(src, off, packetBuffer, MIC_HEADER_SIZE, len)
                    runCatching { socket!!.send(DatagramPacket(packetBuffer, plen, destinationAddress, micPort)) }
                }
                seq = (seq + 1) and 0xFFFF
            }
            while (isActive) {
                if (isMicMuted.value) {
                    if (!lastMutedSent) {
                        sendMicPacket(true, null, 0, 0)
                        lastMutedSent = true
                    }
                    micRecord.read(buffer, 0, buffer.size)
                    continue
                }
                lastMutedSent = false
                val bytesRead = micRecord.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    var offset = 0
                    while (offset < bytesRead) {
                        val remaining = bytesRead - offset
                        var chunk = if (remaining > chunkBytes) chunkBytes else remaining
                        chunk -= chunk % 2
                        if (chunk <= 0) break
                        sendMicPacket(false, buffer, offset, chunk)
                        offset += chunk
                    }
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
            runCatching { aec?.release() }
            runCatching { ns?.release() }
            runCatching { agc?.release() }
            runCatching { micRecord?.stop() }
            runCatching { micRecord?.release() }
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
        streamingPort: Int,
        networkInterfaceName: String = "Auto",
        rtpEnabled: Boolean = false,
        rtpPort: Int = 9094,
        httpEnabled: Boolean = false,
        httpPort: Int = 8080,
        onClientDisconnected: (() -> Unit)? = null
    ) {
        if (streamingJob?.isActive == true) return
        serverStreamsMic = streamMic
        startDonationTimer(context)
        Log.d(TAG, "[SERVER] startServerAudio: isMulticast=$isMulticast port=$streamingPort sr=$sampleRate ch=$channelConfig buf=$bufferSize streamInternal=$streamInternal streamMic=$streamMic")
        val serverFormat = StreamAudioFormat(
            sampleRate = sampleRate,
            channels = if (channelConfig == "STEREO") 2 else 1,
            bitDepth = 16
        )
        startBroadcastingPresence(context, isMulticast, streamingPort, networkInterfaceName, rtpEnabled, serverFormat)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        originalMediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (streamInternal) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        }

        streamingJob = scope.launch {
            isServerStreaming = true
            Log.d(TAG, "[SERVER] streamingJob started")
            var internalRecord: AudioRecord? = null
            var micRecord: AudioRecord? = null
            var sendSocket: BoundDatagramSocket? = null
            var hasError = false

            try {
                val selectorManager = SelectorManager(Dispatchers.IO)
                val channelConfigIn = if (channelConfig == "STEREO") AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfigIn)
                    .build()

                val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, AudioFormat.ENCODING_PCM_16BIT)
                var safeBufferSize = bufferSize.coerceAtLeast(minBufferSize)
                val frameSize = if (channelConfig == "STEREO") 4 else 2
                if (safeBufferSize % frameSize != 0) {
                    safeBufferSize += frameSize - (safeBufferSize % frameSize)
                }

                fun setupAudioRecorders(bufSize: Int) {
                    Log.d(TAG, "[SERVER] setupAudioRecorders: bufSize=$bufSize streamInternal=$streamInternal streamMic=$streamMic projectionNull=${projection == null}")
                    if (streamInternal && projection != null) {
                        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                            .build()
                        activeInternalRecord = AudioRecord.Builder()
                            .setAudioFormat(audioFormat)
                            .setBufferSizeInBytes(bufSize)
                            .setAudioPlaybackCaptureConfig(config)
                            .build()
                        activeInternalRecord?.startRecording()
                        Log.d(TAG, "[SERVER] internalRecord state=${activeInternalRecord?.state} recordingState=${activeInternalRecord?.recordingState}")
                    }
                    if (streamMic) {
                        activeMicRecord = AudioRecord.Builder()
                            .setAudioSource(MediaRecorder.AudioSource.MIC)
                            .setAudioFormat(audioFormat)
                            .setBufferSizeInBytes(bufSize)
                            .build()
                        activeMicRecord?.startRecording()
                        Log.d(TAG, "[SERVER] micRecord state=${activeMicRecord?.state} recordingState=${activeMicRecord?.recordingState}")
                    }
                    if (!streamInternal && !streamMic) {
                        Log.w(TAG, "[SERVER] ATTENZIONE: nessuna sorgente audio abilitata!")
                    }
                }

                val channels = if (channelConfig == "STEREO") 2 else 1
                val wifiIface = getWifiNetworkInterface(networkInterfaceName)

                // ── Queue: producer → UDP sender ───────────────────────────────────────
                val udpAudioQueue = java.util.concurrent.ArrayBlockingQueue<Pair<ByteArray, Int>>(30)

                fun launchAudioProducer(): Job = launch {
                    val internalBuf = if (streamInternal) ByteArray(safeBufferSize) else null
                    val micBuf      = if (streamMic)      ByteArray(safeBufferSize) else null
                    val mixedBuf    = ByteArray(safeBufferSize)
                    val fSize       = frameSize
                    val bufMs       = (safeBufferSize.toLong() * 1000L) / (sampleRate.toLong() * channels * 2)
                    var producerLoopCount = 0L
                    var producerTotalBytes = 0L

                    Log.d(TAG, "[PRODUCER] avviato: streamInternal=$streamInternal projNull=${projection == null} internalRecordState=${activeInternalRecord?.state} micRecordState=${activeMicRecord?.state} safeBufferSize=$safeBufferSize bufMs=$bufMs")
                    println("DEBUG_WFAS: streamInternal=$streamInternal")
                    println("DEBUG_WFAS: projectionIsNull=${projection == null}")
                    println("DEBUG_WFAS: internalRecordState=${activeInternalRecord?.state}")

                    while (isActive) {
                        val iBytes = activeInternalRecord?.read(internalBuf!!, 0, internalBuf.size, AudioRecord.READ_NON_BLOCKING) ?: 0
                        val mBytes = activeMicRecord?.read(micBuf!!, 0, micBuf.size, AudioRecord.READ_NON_BLOCKING) ?: 0

                        if (iBytes < 0) {
                            Log.e(TAG, "[PRODUCER] iBytes ERROR=$iBytes (AudioRecord error code)")
                            println("DEBUG_WFAS: iBytes ERROR = $iBytes")
                        }
                        if (mBytes < 0) Log.e(TAG, "[PRODUCER] mBytes ERROR=$mBytes (AudioRecord error code)")
                        if (iBytes == 0 && streamInternal) println("DEBUG_WFAS: iBytes è 0")

                        val ei = iBytes.coerceAtLeast(0)
                        val em = mBytes.coerceAtLeast(0)
                        if (ei == 0 && em == 0) {
                            delay(bufMs.coerceAtLeast(10L))
                            continue
                        }

                        val bothOk = streamInternal && streamMic && ei > 0 && em > 0
                        val n = when {
                            bothOk                   -> minOf(ei, em)
                            streamInternal && ei > 0 -> ei
                            streamMic      && em > 0 -> em
                            else                     -> 0
                        }
                        if (n == 0) continue
                        val aligned = n - (n % fSize)
                        if (aligned == 0) continue

                        val raw: ByteArray = if (bothOk) {
                            val iShorts = ByteBuffer.wrap(internalBuf!!, 0, aligned).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val mShorts = ByteBuffer.wrap(micBuf!!,      0, aligned).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            for (i in 0 until aligned / 2) {
                                val s = (iShorts[i].toInt() + mShorts[i].toInt())
                                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                                mixedBuf[2 * i]     = (s.toInt() and 0xff).toByte()
                                mixedBuf[2 * i + 1] = (s.toInt() shr 8 and 0xff).toByte()
                            }
                            mixedBuf
                        } else if (streamInternal && ei > 0) internalBuf!! else micBuf!!

                        val vol = serverVolume.value
                        if (vol != 1.0f) {
                            val sb = ByteBuffer.wrap(raw, 0, aligned).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            for (i in 0 until aligned / 2) {
                                val s = (sb[i].toInt() * vol).toInt()
                                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                raw[2 * i]     = (s and 0xff).toByte()
                                raw[2 * i + 1] = ((s shr 8) and 0xff).toByte()
                            }
                        }

                        val chunk = raw.copyOf(aligned)
                        httpPcmQueue?.let { if (it.remainingCapacity() > 0) it.offer(chunk) }
                        rtpPcmQueue?.let  { if (it.remainingCapacity() > 0) it.offer(chunk) }
                        val offered = udpAudioQueue.remainingCapacity() > 0
                        if (offered) udpAudioQueue.offer(Pair(chunk, aligned))

                        producerLoopCount++
                        producerTotalBytes += aligned
                        if (producerLoopCount == 1L || producerLoopCount % 300L == 0L) {
                            Log.d(TAG, "[PRODUCER] loop #$producerLoopCount iBytes=$ei mBytes=$em aligned=$aligned offered=$offered queueSize=${udpAudioQueue.size} totalBytes=$producerTotalBytes")
                        }
                    }
                    Log.d(TAG, "[PRODUCER] loop terminato dopo $producerLoopCount iterazioni, totalBytes=$producerTotalBytes")
                }

                // ── UDP SENDER ────────────────────────────────────────────────────────
                // Legge dalla coda prodotta dall'AudioProducer, incapsula nel formato
                // WFAS e invia via socket UDP. Non tocca AudioRecord direttamente.
                suspend fun streamingLoop(
                    targetAddress: SocketAddress,
                    clientAlive: java.util.concurrent.atomic.AtomicBoolean? = null,
                    sendDir: WfasCrypto.Dir? = null,
                    beacon: ByteArray? = null
                ) {
                    val fSz = channels * 2
                    val safeMtuSize = 1400
                    val headerSize = 10
                    var maxBytesPerPacket = SettingsDataStore(context).settingsFlow.first().maxPayloadBytes.coerceIn(256, safeMtuSize - headerSize)
                    maxBytesPerPacket -= (maxBytesPerPacket % fSz)
                    if (sendDir != null) {
                        maxBytesPerPacket -= WfasCrypto.AEAD_OVERHEAD
                        maxBytesPerPacket -= (maxBytesPerPacket % fSz)
                        if (maxBytesPerPacket < fSz) maxBytesPerPacket = fSz
                    }
                    var seqNumber = 0
                    var samplePosition = 0L
                    var loopCount = 0L
                    var sentPackets = 0L
                    var lastBeacon = 0L

                    Log.d(TAG, "[UDP_SENDER] streamingLoop avviato verso $targetAddress maxBytesPerPacket=$maxBytesPerPacket")

                    while (isActive && clientAlive?.get() != false) {
                        if (beacon != null && System.currentTimeMillis() - lastBeacon >= 400L) {
                            runCatching { sendSocket?.send(Datagram(buildPacket { writeFully(beacon) }, targetAddress)) }
                            lastBeacon = System.currentTimeMillis()
                        }
                        val (pcmData, pcmLen) = udpAudioQueue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
                        loopCount++
                        try {
                            var offset = 0
                            while (offset < pcmLen) {
                                val chunkSize = minOf(maxBytesPerPacket, pcmLen - offset)
                                val packet = if (sendDir != null) {
                                    val enc = WfasCrypto.encryptPacket(
                                        sendDir, seqNumber, samplePosition, false,
                                        pcmData.copyOfRange(offset, offset + chunkSize)
                                    )
                                    buildPacket { writeFully(enc) }
                                } else {
                                    buildPacket {
                                        writeByte(0x57.toByte())
                                        writeByte(0x46.toByte())
                                        writeByte(WFAS_PROTOCOL_VERSION.toByte())
                                        writeByte(0x00.toByte())
                                        writeByte((seqNumber shr 8).toByte())
                                        writeByte(seqNumber.toByte())
                                        writeInt((samplePosition and 0xFFFFFFFFL).toInt())
                                        writeFully(pcmData, offset, chunkSize)
                                    }
                                }
                                sendSocket?.send(Datagram(packet, targetAddress))
                                sentPackets++
                                if (sentPackets == 1L || sentPackets % 500L == 0L) {
                                    Log.d(TAG, "[UDP_SENDER] pkt #$sentPackets seq=$seqNumber chunkSize=$chunkSize verso $targetAddress")
                                }
                                seqNumber = (seqNumber + 1) and 0xFFFF
                                samplePosition += (chunkSize / 2 / channels).toLong()
                                offset += chunkSize
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "[UDP_SENDER] eccezione inviando a $targetAddress: ${e.message}")
                            clientAlive?.set(false)
                            break
                        }
                    }
                    Log.d(TAG, "[UDP_SENDER] streamingLoop terminato: sentPackets=$sentPackets loopCount=$loopCount clientAlive=${clientAlive?.get()}")
                }

                httpJob?.cancel()
                httpPcmQueue?.clear()
                httpPcmQueue = null
                try { httpServerSocket?.close() } catch (_: Exception) {}
                httpServerSocket = null

                if (httpEnabled) {
                    httpJob = scope.launchHttpSidecar(sampleRate, channels, httpPort)
                }

                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val wifiNet = getWifiNetworkObject(context)
                val vpnActive = isVpnActive(context)

                if (isMulticast) {
                    val targetAddress = InetSocketAddress(NetworkSettings.MULTICAST_GROUP_IP, streamingPort)
                    Log.d(TAG, "[SERVER][MULTICAST] modalità multicast, target=$targetAddress vpnActive=$vpnActive wifiNet=$wifiNet")
                    if (!vpnActive) wifiNet?.let { cm.bindProcessToNetwork(it) }
                    val wifiLocalIp = wifiIface?.inetAddresses?.toList()
                        ?.filterIsInstance<java.net.Inet4Address>()
                        ?.firstOrNull()?.hostAddress ?: "0.0.0.0"
                    Log.d(TAG, "[SERVER][MULTICAST] bindando socket su $wifiLocalIp:0")
                    sendSocket = aSocket(selectorManager).udp().bind(InetSocketAddress(wifiLocalIp, 0))
                    Log.d(TAG, "[SERVER][MULTICAST] socket bound, setupAudioRecorders...")
                    delay(500)
                    setupAudioRecorders(safeBufferSize)

                    if (rtpEnabled) {
                        rtpJob = scope.launchRtpSidecar(
                            sampleRate = sampleRate,
                            channels = channels,
                            port = rtpPort,
                            isMulticast = true,
                            clientIp = null,
                            wifiIface = wifiIface
                        )
                    }

                    launchAudioProducer()

                    val mcSec = SettingsDataStore(context).settingsFlow.first()
                    configureSecurity(mcSec.securityMode, mcSec.authKey, mcSec.encryptionEnabled)
                    var mcDir: WfasCrypto.Dir? = null
                    var mcBeaconBytes: ByteArray? = null
                    if (mcSec.encryptionEnabled && SecurityMode.fromStringSafe(mcSec.securityMode) == SecurityMode.KEY) {
                        val salt = ByteArray(WfasCrypto.SALT_BYTES).also { java.security.SecureRandom().nextBytes(it) }
                        mcDir = WfasCrypto.deriveMulticast(mcSec.authKey, salt)
                        val epoch = SettingsDataStore(context).nextMcastEpoch()
                        mcBeaconBytes = WfasCrypto.buildMcastBeacon(mcSec.authKey, epoch, System.currentTimeMillis() / 1000, salt)
                            .toByteArray(Charsets.US_ASCII)
                    }

                    try {
                        streamingLoop(targetAddress, sendDir = mcDir, beacon = mcBeaconBytes)
                    } finally {
                        withContext(NonCancellable) {
                            try {
                                repeat(3) {
                                    val byePacket = buildPacket { writeText("BYE") }
                                    sendSocket?.send(Datagram(byePacket, targetAddress))
                                }
                                println("--- Sent BYE to multicast group ---")
                            } catch (_: Exception) {}
                        }
                    }
                } else {
                    Log.d(TAG, "[SERVER][UNICAST] modalità unicast, in ascolto su porta $streamingPort")
                    connectionStatus.value = context.getString(R.string.status_waiting_for_client, streamingPort)
                    delay(500)
                    setupAudioRecorders(safeBufferSize)

                    val localAddress = InetSocketAddress("0.0.0.0", streamingPort)
                    if (!vpnActive) wifiNet?.let { cm.bindProcessToNetwork(it) }
                    sendSocket = aSocket(SelectorManager(Dispatchers.IO)).udp().bind(localAddress) { reuseAddress = true }
                    Log.d(TAG, "[SERVER][UNICAST] socket UDP bound su $localAddress, vpnActive=$vpnActive wifiNet=$wifiNet")

                    // Il producer parte subito: HTTP/RTP ricevono audio anche prima che
                    // arrivi qualsiasi client UDP.
                    launchAudioProducer()

                    val sec = SettingsDataStore(context).settingsFlow.first()
                    configureSecurity(sec.securityMode, sec.authKey, sec.encryptionEnabled)
                    var pendCnonce = ""
                    var pendSnonce = ""
                    while (isActive) {
                        startBroadcastingPresence(context, isMulticast = false, streamingPort, networkInterfaceName, rtpEnabled, serverFormat)
                        connectionStatus.value = context.getString(R.string.status_waiting_for_client, streamingPort)

                        val clientDatagram = sendSocket.receive()
                        val clientAddress = clientDatagram.address
                        val message = clientDatagram.packet.readText().trim()
                        Log.d(TAG, "[SERVER][UNICAST] datagram ricevuto da $clientAddress: '$message'")

                        if (message == "MODE_PROBE") {
                            Log.d(TAG, "[SERVER][UNICAST] rispondo UNICAST a MODE_PROBE da $clientAddress")
                            sendSocket.send(Datagram(buildPacket { writeText("UNICAST") }, clientAddress))
                            continue
                        }

                        if (!message.startsWith(NetworkSettings.CLIENT_HELLO_MESSAGE)) {
                            Log.w(TAG, "[SERVER][UNICAST] messaggio ignorato (non HELLO): '$message'")
                            continue
                        }

                        val clientVersion = parseProtocolVersion(message)
                        if (clientVersion != WFAS_PROTOCOL_VERSION) {
                            Log.w(TAG, "[SERVER][UNICAST] client incompatibile v=$clientVersion (mio v=$WFAS_PROTOCOL_VERSION), rifiuto $clientAddress")
                            sendSocket.send(Datagram(buildPacket { writeText(incompatibleMessage()) }, clientAddress))
                            signalProtocolMismatch(clientVersion)
                            continue
                        }

                        when (SecurityMode.fromStringSafe(securityMode)) {
                            SecurityMode.KEY -> {
                                val cproof = WfasAuth.getToken(message, "cproof")
                                val cnonce = WfasAuth.getToken(message, "cnonce") ?: ""
                                if (cproof == null) {
                                    pendCnonce = cnonce
                                    pendSnonce = WfasAuth.nonceHex()
                                    val sproof = WfasAuth.proof(authKey, 'S', pendCnonce, pendSnonce)
                                    sendSocket.send(Datagram(buildPacket { writeText("${NetworkSettings.AUTH_REQUIRED_PREFIX};snonce=$pendSnonce;sproof=$sproof") }, clientAddress))
                                    continue
                                }
                                val expected = WfasAuth.proof(authKey, 'C', pendCnonce.ifEmpty { cnonce }, pendSnonce)
                                if (!WfasAuth.constantTimeEquals(cproof, expected)) {
                                    Log.w(TAG, "[SERVER][UNICAST] auth failed for $clientAddress")
                                    sendSocket.send(Datagram(buildPacket { writeText(NetworkSettings.UNAUTHORIZED_MESSAGE) }, clientAddress))
                                    continue
                                }
                                Log.d(TAG, "[SERVER][UNICAST] auth OK for $clientAddress")
                            }
                            SecurityMode.ASK -> {
                                sendSocket.send(Datagram(buildPacket { writeText(NetworkSettings.PENDING_MESSAGE) }, clientAddress))
                                val keepAlive = launch {
                                    while (isActive) {
                                        delay(2000)
                                        runCatching { sendSocket.send(Datagram(buildPacket { writeText(NetworkSettings.PENDING_MESSAGE) }, clientAddress)) }
                                    }
                                }
                                val allow = try {
                                    requestAuthFromUi(clientAddress.toString())
                                } finally {
                                    keepAlive.cancel()
                                }
                                if (!allow) {
                                    sendSocket.send(Datagram(buildPacket { writeText(NetworkSettings.UNAUTHORIZED_MESSAGE) }, clientAddress))
                                    continue
                                }
                            }
                            SecurityMode.OFF -> { }
                        }

                        val encrypting = encryptionEnabled &&
                            SecurityMode.fromStringSafe(securityMode) == SecurityMode.KEY
                        val sendDir: WfasCrypto.Dir? =
                            if (encrypting) WfasCrypto.deriveUnicast(authKey, pendCnonce, pendSnonce).second else null

                        Log.d(TAG, "[SERVER][UNICAST] HELLO ricevuto da $clientAddress, invio HELLO_ACK")
                        connectionStatus.value = context.getString(R.string.status_client_connected, clientAddress)

                        // Svuota la coda: il client riceve solo audio fresco
                        udpAudioQueue.clear()

                        val ackText = helloAckMessage() + if (encrypting) ";enc=1" else ""
                        val ackPacket = buildPacket { writeText(ackText) }
                        sendSocket.send(Datagram(ackPacket, clientAddress))
                        Log.d(TAG, "[SERVER][UNICAST] HELLO_ACK inviato a $clientAddress")

                        // In unicast il server serve un client solo: finche' e' occupato
                        // non deve annunciarsi, altrimenti un terzo dispositivo lo vede
                        // libero e prova a connettersi. Il beacon riparte da solo al
                        // prossimo giro del while, cioe' quando il client si stacca.
                        Log.d(TAG, "[SERVER][UNICAST] occupato: sospendo l'annuncio in discovery")
                        stopBroadcastingPresence()
                        announceServerGone(context, networkInterfaceName)

                        val clientAlive = java.util.concurrent.atomic.AtomicBoolean(true)

                        if (rtpEnabled) {
                            rtpJob?.cancel()

                            val clientInetAddress = (clientAddress as? InetSocketAddress)?.hostname

                            rtpJob = scope.launchRtpSidecar(
                                sampleRate = sampleRate,
                                channels = channels,
                                port = rtpPort,
                                isMulticast = false,
                                clientIp = clientInetAddress,
                                wifiIface = wifiIface
                            )
                        }

                        val pingJob = launch {
                            var failures = 0
                            var pingCount = 0L
                            while (isActive && clientAlive.get()) {
                                delay(1000)
                                try {
                                    sendSocket.send(Datagram(buildPacket { writeText("PING") }, clientAddress))
                                    pingCount++
                                    if (pingCount == 1L || pingCount % 10L == 0L) {
                                        Log.d(TAG, "[SERVER][UNICAST] PING #$pingCount inviato a $clientAddress failures=$failures")
                                    }
                                    failures = 0
                                } catch (e: Exception) {
                                    failures++
                                    Log.w(TAG, "[SERVER][UNICAST] PING fallito ($failures/3): ${e.message}")
                                    if (failures >= 3) { clientAlive.set(false) }
                                }
                            }
                        }

                        val clientByeJob = launch {
                            try {
                                while (isActive && clientAlive.get()) {
                                    val datagram = sendSocket.receive()
                                    val msg = datagram.packet.readText().trim()
                                    Log.d(TAG, "[SERVER][UNICAST] byeJob ricevuto: '$msg' da ${datagram.address}")
                                    // Solo il client collegato puo' pilotare questa sessione:
                                    // senza questo controllo un CLIENT_BYE di un terzo
                                    // dispositivo chiuderebbe la connessione altrui.
                                    if (datagram.address != clientAddress) {
                                        if (msg.startsWith(NetworkSettings.CLIENT_HELLO_MESSAGE) || msg == "MODE_PROBE") {
                                            Log.w(TAG, "[SERVER][UNICAST] ${datagram.address} rifiutato: occupato con $clientAddress")
                                            runCatching {
                                                sendSocket.send(Datagram(
                                                    buildPacket { writeText(NetworkSettings.BUSY_MESSAGE) },
                                                    datagram.address
                                                ))
                                            }
                                        } else {
                                            Log.w(TAG, "[SERVER][UNICAST] datagram da ${datagram.address} ignorato: sessione occupata")
                                        }
                                        continue
                                    }
                                    if (msg == "CLIENT_BYE") {
                                        Log.d(TAG, "[SERVER][UNICAST] CLIENT_BYE ricevuto, disconnessione pulita")
                                        println("--- Received CLIENT_BYE from $clientAddress ---")
                                        clientAlive.set(false)
                                        break
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "[SERVER][UNICAST] clientByeJob eccezione: ${e.message}")
                            }
                        }

                        var clientDisconnectedUnexpectedly = false
                        try {
                            streamingLoop(clientAddress, clientAlive, sendDir)
                        } finally {
                            clientByeJob.cancel()
                            pingJob.cancel()
                            if (clientAlive.get()) {
                                withContext(NonCancellable) {
                                    try {
                                        repeat(3) {
                                            sendSocket.send(Datagram(buildPacket { writeText("BYE") }, clientAddress))
                                        }
                                        println("--- Sent BYE to $clientAddress ---")
                                    } catch (_: Exception) {}
                                }
                            } else if (isActive) {
                                clientDisconnectedUnexpectedly = true
                            }
                        }
                        if (clientDisconnectedUnexpectedly) {
                            isStreamingCurrent.value = false
                            scope.launch(Dispatchers.Main) { onClientDisconnected?.invoke() }
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    hasError = true
                    Log.e(TAG, "[SERVER] ECCEZIONE FATALE: ${e.message}", e)
                    connectionStatus.value = context.getString(R.string.status_server_error, e.message)
                }
            } finally {
                Log.d(TAG, "[SERVER] finally: cleanup, hasError=$hasError isServerStreaming=$isServerStreaming")
                isServerStreaming = false
                runCatching {
                    (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).bindProcessToNetwork(null)
                }

                try { activeInternalRecord?.stop() } catch (_: Exception) {}
                try { activeInternalRecord?.release() } catch (_: Exception) {}
                activeInternalRecord = null

                try { activeMicRecord?.stop() } catch (_: Exception) {}
                try { activeMicRecord?.release() } catch (_: Exception) {}
                activeMicRecord = null

                try { sendSocket?.close() } catch (_: Exception) {}

                originalMediaVolume?.let {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0)
                    originalMediaVolume = null
                }

                stopBroadcastingPresence()
                announceServerGone(context, networkInterfaceName)
                if (isActive && !hasError) connectionStatus.value = context.getString(R.string.status_server_stopped)
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
        networkInterfaceName: String = "Auto",
        connectionSoundEnabled: Boolean = true,
        disconnectionSoundEnabled: Boolean = true,
        onServerDisconnected: (() -> Unit)? = null
    ) {
        // ── Formato di riproduzione ───────────────────────────────────────────
        // I byte li manda il server, quindi e' il suo formato a comandare: le
        // impostazioni locali valgono solo come fallback se il beacon non lo
        // annuncia. Un formato che non sappiamo riprodurre va rifiutato, perche'
        // reinterpretarlo a caso produce solo rumore.
        val advertised = serverInfo.audioFormat
        if (advertised != null && !advertised.isPlayable) {
            Log.w(TAG, "[CLIENT] Formato non riproducibile: ${advertised.describe()} - connessione rifiutata")
            connectionStatus.value =
                context.getString(R.string.status_unsupported_format, advertised.describe())
            isStreamingCurrent.value = false
            scope.launch(Dispatchers.Main) { onServerDisconnected?.invoke() }
            return
        }

        stopStreaming(context)
        isServerStreaming = false
        isStreamingCurrent.value = true
        startDonationTimer(context)

        micSendDir = null

        // Il microfono e' un flusso nostro in salita: resta sulle impostazioni locali.
        if (sendMicrophone) {
            micStreamingJob = scope.launchMicSenderJob(context, serverInfo, sampleRate, channelConfig, bufferSize, micPort)
        }

        // Da qui in poi 'sampleRate' e 'channelConfig' sono quelli del flusso in arrivo.
        @Suppress("NAME_SHADOWING")
        val sampleRate = advertised?.sampleRate ?: sampleRate
        @Suppress("NAME_SHADOWING")
        val channelConfig = advertised?.let { if (it.channels == 2) "STEREO" else "MONO" } ?: channelConfig
        if (advertised != null) {
            Log.i(TAG, "[CLIENT] Riproduzione col formato annunciato dal server: ${advertised.describe()}")
        }

        streamingJob = scope.launch {
            var connectedSuccessfully = false
            var disconnectionSoundPlayed = false
            try {
                if (!serverInfo.isMulticast) {
                    var audioTrack: AudioTrack? = null
                    var socket: BoundDatagramSocket? = null
                    try {
                        val selectorManager = SelectorManager(Dispatchers.IO)
                        val advSettings = SettingsDataStore(context).settingsFlow.first()
                        val channelConfigOut = if (channelConfig == "STEREO") AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
                        val frameSize = if (channelConfig == "STEREO") 4 else 2
                        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, AudioFormat.ENCODING_PCM_16BIT)
                        prepareNoiseReducer(context, sampleRate, if (channelConfig == "STEREO") 2 else 1)
                        // Ultima rete: il dispositivo puo' rifiutare una combinazione
                        // che sulla carta e' valida. Meglio non riprodurre nulla.
                        if (minBuffer == AudioTrack.ERROR || minBuffer == AudioTrack.ERROR_BAD_VALUE) {
                            Log.e(TAG, "[CLIENT] AudioTrack non supporta ${sampleRate}Hz/$channelConfig")
                            connectionStatus.value = context.getString(
                                R.string.status_unsupported_format,
                                "$sampleRate Hz, " + (if (channelConfig == "STEREO") "stereo" else "mono") + ", 16 bit"
                            )
                            isStreamingCurrent.value = false
                            withContext(Dispatchers.Main) { onServerDisconnected?.invoke() }
                            return@launch
                        }
                        val targetLatencyFrames = advSettings.latencyMs * sampleRate / 1000
                        val marginFrames = sampleRate * 30 / 1000
                        var playbackBufferSize = minBuffer.coerceAtLeast((targetLatencyFrames + marginFrames * 2) * frameSize)
                        if (playbackBufferSize % frameSize != 0) {
                            playbackBufferSize += frameSize - (playbackBufferSize % frameSize)
                        }

                        val trackBuilder = AudioTrack.Builder()
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                        }
                        audioTrack = trackBuilder.build()

                        val prerollLen = (sampleRate * frameSize * 30 / 1000)
                            .coerceIn(0, playbackBufferSize - frameSize)
                            .let { it - (it % frameSize) }
                        if (prerollLen > 0) audioTrack!!.write(ByteArray(prerollLen), 0, prerollLen, AudioTrack.WRITE_BLOCKING)
                        audioTrack!!.play()

                        connectionStatus.value = context.getString(R.string.status_contacting_server, serverInfo.ip)
                        socket = aSocket(selectorManager).udp().bind { receiveBufferSize = 1 shl 20 }
                        val sock = socket!!
                        val remoteAddress = InetSocketAddress(serverInfo.ip, serverInfo.port)

                        val cnonce = WfasAuth.nonceHex()
                        var helloMsg = "${clientHelloMessage()};cnonce=$cnonce"
                        var proved = false
                        var clientSnonce = ""
                        var clientKey = clientPresharedKey
                        var sessionEncrypted = false
                        sock.send(Datagram(buildPacket { writeText(helloMsg) }, remoteAddress))
                        connectionStatus.value = context.getString(R.string.status_waiting_for_ack)

                        var handshakeDeadline = System.currentTimeMillis() + 30000
                        var handshakeOk = false

                        suspend fun promptKeyAndRestart(wrong: Boolean): Boolean {
                            val k = requestKeyFromUi(wrong) ?: return false
                            if (k.isBlank()) return false
                            clientKey = k
                            proved = false
                            helloMsg = "${clientHelloMessage()};cnonce=$cnonce"
                            sock.send(Datagram(buildPacket { writeText(helloMsg) }, remoteAddress))
                            handshakeDeadline = System.currentTimeMillis() + 30000
                            return true
                        }

                        while (System.currentTimeMillis() < handshakeDeadline) {
                            val ackMsg = try {
                                withTimeout(2000) { socket.receive() }.packet.readText().trim()
                            } catch (e: TimeoutCancellationException) {
                                socket.send(Datagram(buildPacket { writeText(helloMsg) }, remoteAddress))
                                continue
                            }
                            when {
                                ackMsg.startsWith(NetworkSettings.INCOMPATIBLE_PREFIX) -> {
                                    signalProtocolMismatch(parseProtocolVersion(ackMsg))
                                    connectionStatus.value = context.getString(R.string.status_protocol_incompatible)
                                    return@launch
                                }
                                ackMsg == NetworkSettings.BUSY_MESSAGE -> {
                                    Log.w(TAG, "[CLIENT] server occupato con un altro dispositivo")
                                    connectionStatus.value = context.getString(R.string.status_server_busy)
                                    isStreamingCurrent.value = false
                                    withContext(Dispatchers.Main) { onServerDisconnected?.invoke() }
                                    return@launch
                                }
                                ackMsg == NetworkSettings.UNAUTHORIZED_MESSAGE -> {
                                    if (!promptKeyAndRestart(true)) {
                                        connectionStatus.value = context.getString(R.string.status_unauthorized)
                                        return@launch
                                    }
                                }
                                ackMsg == NetworkSettings.PENDING_MESSAGE -> {
                                    connectionStatus.value = context.getString(R.string.status_awaiting_approval)
                                }
                                ackMsg.startsWith(NetworkSettings.AUTH_REQUIRED_PREFIX) -> {
                                    if (clientKey.isEmpty()) {
                                        val k = requestKeyFromUi(false)
                                        if (k.isNullOrBlank()) {
                                            connectionStatus.value = context.getString(R.string.status_key_required)
                                            return@launch
                                        }
                                        clientKey = k
                                        handshakeDeadline = System.currentTimeMillis() + 30000
                                    }
                                    val snonce = WfasAuth.getToken(ackMsg, "snonce") ?: ""
                                    clientSnonce = snonce
                                    val sproof = WfasAuth.getToken(ackMsg, "sproof") ?: ""
                                    if (!WfasAuth.constantTimeEquals(sproof, WfasAuth.proof(clientKey, 'S', cnonce, snonce))) {
                                        if (!promptKeyAndRestart(true)) {
                                            connectionStatus.value = context.getString(R.string.status_unauthorized)
                                            return@launch
                                        }
                                    } else {
                                        val cproof = WfasAuth.proof(clientKey, 'C', cnonce, snonce)
                                        helloMsg = "${clientHelloMessage()};cnonce=$cnonce;cproof=$cproof"
                                        proved = true
                                        sock.send(Datagram(buildPacket { writeText(helloMsg) }, remoteAddress))
                                    }
                                }
                                ackMsg.startsWith(NetworkSettings.HELLO_ACK_PREFIX) -> {
                                    val serverVersion = parseProtocolVersion(ackMsg)
                                    if (serverVersion != WFAS_PROTOCOL_VERSION) {
                                        signalProtocolMismatch(serverVersion)
                                        connectionStatus.value = context.getString(R.string.status_protocol_incompatible)
                                        return@launch
                                    }
                                    if (clientKey.isNotEmpty() && !proved) {
                                        connectionStatus.value = context.getString(R.string.status_unauthorized)
                                        return@launch
                                    }
                                    if (WfasAuth.getToken(ackMsg, "enc") == "1") sessionEncrypted = true
                                    handshakeOk = true
                                }
                            }
                            if (handshakeOk) break
                        }
                        if (!handshakeOk) {
                            throw Exception(context.getString(R.string.status_handshake_failed_unexpected_response, "timeout"))
                        }

                        val sessionKeys = if (clientKey.isNotEmpty() && proved)
                            WfasCrypto.deriveUnicast(clientKey, cnonce, clientSnonce) else null
                        val recvDir: WfasCrypto.Dir? = sessionKeys?.second
                        micSendDir = if (sessionEncrypted) sessionKeys?.first else null
                        val recvWin = WfasCrypto.ReplayWindow()
                        var serverEncrypts = sessionEncrypted

                        connectionStatus.value = context.getString(R.string.status_streaming)
                        if (connectionSoundEnabled) playConnectionSound(context)
                        connectedSuccessfully = true

                        var lastPingReceived = System.currentTimeMillis()
                        val pingTimeoutMs = 3000L

                        val MAGIC_0: Byte = 0x57
                        val MAGIC_1: Byte = 0x46
                        val HEADER_SIZE = 10

                        var expectedSeq = -1
                        var lastGoodPcm: ByteArray? = null
                        var versionChecked = false

                        val watchdogJob = launch {
                            while (isActive) {
                                delay(1000)
                                if (System.currentTimeMillis() - lastPingReceived > pingTimeoutMs) {
                                    if (disconnectionSoundEnabled) { playDisconnectionSound(context); disconnectionSoundPlayed = true }
                                    streamingJob?.cancel()
                                    break
                                }
                            }
                        }

                        val maxLagPackets = 3
                        val driftTargetBacklog = 2
                        var driftAvgBacklog = driftTargetBacklog.toFloat()
                        var driftCurrentRate = sampleRate

                        suspend fun playPacket(bytes: ByteArray, smooth: Boolean = false) {
                            if (bytes.size < HEADER_SIZE) return

                            if (!versionChecked) {
                                versionChecked = true
                                val packetVersion = bytes[2].toInt() and 0xFF
                                if (packetVersion != WFAS_PROTOCOL_VERSION) {
                                    signalProtocolMismatch(packetVersion)
                                    connectionStatus.value = context.getString(R.string.status_protocol_incompatible)
                                    streamingJob?.cancel()
                                    return
                                }
                            }

                            val encFlag = (bytes[3].toInt() and WfasCrypto.FLAG_ENCRYPTED) != 0
                            val data: ByteArray
                            if (encFlag) {
                                if (recvDir == null) return
                                val r = WfasCrypto.decryptPacket(recvDir, recvWin, bytes, bytes.size)
                                if (r !is WfasCrypto.Decrypted.Ok) return
                                serverEncrypts = true
                                data = ByteArray(HEADER_SIZE + r.pcm.size)
                                System.arraycopy(bytes, 0, data, 0, HEADER_SIZE)
                                System.arraycopy(r.pcm, 0, data, HEADER_SIZE, r.pcm.size)
                            } else {
                                if (serverEncrypts) return
                                data = bytes
                            }

                            val flags     = data[3].toInt() and 0xFF
                            val seq       = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
                            val isSilence = (flags and 0x01) != 0

                            if (expectedSeq == -1) {
                                expectedSeq = seq
                            } else {
                                val gap = (seq - expectedSeq) and 0xFFFF
                                if (gap in 1..8) {
                                    val ref = lastGoodPcm
                                    if (ref != null) {
                                        var step = 0
                                        repeat(gap.coerceAtMost(3)) {
                                            val factor = 1.0f - step * 0.35f
                                            step++
                                            val fade = ByteArray(ref.size)
                                            val bb  = ByteBuffer.wrap(ref).order(ByteOrder.LITTLE_ENDIAN)
                                            val out = ByteBuffer.wrap(fade).order(ByteOrder.LITTLE_ENDIAN)
                                            while (bb.remaining() >= 2) {
                                                val s = (bb.short * factor).toInt()
                                                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                                out.putShort(s.toShort())
                                            }
                                            audioTrack.write(fade, 0, fade.size, AudioTrack.WRITE_BLOCKING)
                                        }
                                    }
                                }
                            }

                            expectedSeq = (seq + 1) and 0xFFFF

                            if (isSilence || data.size <= HEADER_SIZE) {
                                val silenceLen = lastGoodPcm?.size ?: 3840
                                val silenceBuffer = ByteArray(silenceLen)
                                audioTrack.write(silenceBuffer, 0, silenceLen, AudioTrack.WRITE_BLOCKING)
                            } else {
                                val pcmLen = data.size - HEADER_SIZE
                                val ref = lastGoodPcm
                                if (smooth && ref != null && ref.size >= 2 && pcmLen >= 2) {
                                    val outBuf = ByteArray(pcmLen)
                                    System.arraycopy(data, HEADER_SIZE, outBuf, 0, pcmLen)
                                    val fadeSamples = (minOf(ref.size, pcmLen) / 2).coerceAtMost(256)
                                    val refBuf = ByteBuffer.wrap(ref).order(ByteOrder.LITTLE_ENDIAN)
                                    val outB   = ByteBuffer.wrap(outBuf).order(ByteOrder.LITTLE_ENDIAN)
                                    for (s in 0 until fadeSamples) {
                                        val t = (s + 1).toFloat() / (fadeSamples + 1)
                                        val refS = refBuf.getShort(s * 2).toInt()
                                        val newS = outB.getShort(s * 2).toInt()
                                        val mixed = (refS * (1f - t) + newS * t).toInt()
                                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                        outB.putShort(s * 2, mixed.toShort())
                                    }
                                    denoiseInPlace(outBuf, 0, pcmLen)
                                    audioTrack.write(outBuf, 0, pcmLen, AudioTrack.WRITE_BLOCKING)
                                } else {
                                    denoiseInPlace(data, HEADER_SIZE, pcmLen)
                                    audioTrack.write(data, HEADER_SIZE, pcmLen, AudioTrack.WRITE_BLOCKING)
                                }
                                if (lastGoodPcm == null || lastGoodPcm!!.size != pcmLen) {
                                    lastGoodPcm = ByteArray(pcmLen)
                                }
                                data.copyInto(lastGoodPcm!!, 0, HEADER_SIZE, HEADER_SIZE + pcmLen)
                            }
                        }

                        try {
                            while (isActive) {
                                val audio = ArrayList<ByteArray>()
                                var byeReceived = false
                                var dg: Datagram? = socket.receive()
                                while (dg != null) {
                                    val pk = dg.packet
                                    val pb = ByteArray(pk.remaining.toInt())
                                    pk.readFully(pb)
                                    if (pb.size >= 2 && pb[0] == MAGIC_0 && pb[1] == MAGIC_1) {
                                        audio.add(pb)
                                    } else {
                                        val ctrl = pb.toString(Charsets.UTF_8).trim()
                                        when (ctrl) {
                                            "PING" -> lastPingReceived = System.currentTimeMillis()
                                            "BYE" -> {
                                                if (disconnectionSoundEnabled) { playDisconnectionSound(context); disconnectionSoundPlayed = true }
                                                byeReceived = true
                                            }
                                        }
                                    }
                                    dg = socket.incoming.tryReceive().getOrNull()
                                }
                                if (byeReceived) { streamingJob?.cancel(); break }
                                if (audio.isEmpty()) continue

                                val skip = (audio.size - maxLagPackets).coerceAtLeast(0)
                                if (skip > 0) expectedSeq = -1
                                for (i in skip until audio.size) {
                                    if (!isActive) break
                                    playPacket(audio[i], smooth = (i == skip && skip > 0))
                                }
                                driftAvgBacklog = driftAvgBacklog * 0.9f + audio.size * 0.1f
                                val err = driftAvgBacklog - driftTargetBacklog
                                val factor = (1.0 + err * 0.0004).coerceIn(0.997, 1.003)
                                val newRate = (sampleRate * factor).toInt()
                                if (kotlin.math.abs(newRate - driftCurrentRate) >= 4) {
                                    runCatching { audioTrack!!.setPlaybackRate(newRate) }
                                    driftCurrentRate = newRate
                                }
                            }
                        } finally {
                            watchdogJob.cancel()
                        }
                    } finally {
                        audioTrack?.stop()
                        audioTrack?.release()
                        if (connectedSuccessfully) {
                            withContext(NonCancellable) {
                                try {
                                    socket?.send(Datagram(buildPacket { writeText("CLIENT_BYE") }, InetSocketAddress(serverInfo.ip, serverInfo.port)))
                                } catch (_: Exception) {}
                            }
                        }
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
                        multicastSocket = MulticastSocket(serverInfo.port).apply {
                            getWifiNetworkInterface(networkInterfaceName)?.let { networkInterface = it }
                            joinGroup(groupAddress)
                        }

                        val channelConfigOut = if (channelConfig == "STEREO") AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
                        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, AudioFormat.ENCODING_PCM_16BIT)
                        prepareNoiseReducer(context, sampleRate, if (channelConfig == "STEREO") 2 else 1)
                        // Ultima rete: il dispositivo puo' rifiutare una combinazione
                        // che sulla carta e' valida. Meglio non riprodurre nulla.
                        if (minBuffer == AudioTrack.ERROR || minBuffer == AudioTrack.ERROR_BAD_VALUE) {
                            Log.e(TAG, "[CLIENT] AudioTrack non supporta ${sampleRate}Hz/$channelConfig")
                            connectionStatus.value = context.getString(
                                R.string.status_unsupported_format,
                                "$sampleRate Hz, " + (if (channelConfig == "STEREO") "stereo" else "mono") + ", 16 bit"
                            )
                            isStreamingCurrent.value = false
                            withContext(Dispatchers.Main) { onServerDisconnected?.invoke() }
                            return@launch
                        }
                        var playbackBufferSize = minBuffer.coerceAtLeast(SettingsDataStore(context).settingsFlow.first().latencyMs * sampleRate / 1000 * (if (channelConfig == "STEREO") 4 else 2))

                        val frameSize = if (channelConfig == "STEREO") 4 else 2
                        if (playbackBufferSize % frameSize != 0) {
                            playbackBufferSize += frameSize - (playbackBufferSize % frameSize)
                        }

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
                        if (connectionSoundEnabled) playConnectionSound(context)
                        connectedSuccessfully = true

                        multicastSocket.soTimeout = 2000

                        val buffer = ByteArray(65536)
                        val packet = DatagramPacket(buffer, buffer.size)

                        val MC_MAGIC_0: Byte = 0x57
                        val MC_MAGIC_1: Byte = 0x46
                        val MC_HEADER_SIZE = 10
                        var mcVersionChecked = false
                        var mcDir: WfasCrypto.Dir? = null
                        var mcWin = WfasCrypto.ReplayWindow()
                        var mcKey = ""
                        var mcKeyAsked = false
                        var mcLastEpoch = SettingsDataStore(context).getMcastClientEpoch(serverInfo.ip)
                        val beaconPrefixLen = WfasCrypto.MSG_MCAST_ENC.length

                        val drainPacket = DatagramPacket(ByteArray(65536), 65536)
                        val maxLagPackets = 5
                        while (isActive) {
                            try {
                                multicastSocket.receive(packet)
                            } catch (_: java.net.SocketTimeoutException) {
                                continue
                            }

                            val audioPackets = ArrayList<ByteArray>()
                            var stopLoop = false
                            var mcAbort = false

                            suspend fun classify(src: ByteArray, plen: Int) {
                                if (plen <= 0) return
                                if (plen >= beaconPrefixLen &&
                                    String(src, 0, beaconPrefixLen, Charsets.US_ASCII) == WfasCrypto.MSG_MCAST_ENC) {
                                    if (mcKey.isEmpty() && !mcKeyAsked) {
                                        mcKeyAsked = true
                                        mcKey = requestKeyFromUi(false) ?: ""
                                        if (mcKey.isBlank()) connectionStatus.value = context.getString(R.string.status_key_required)
                                    }
                                    if (mcKey.isNotEmpty()) {
                                        val info = WfasCrypto.parseMcastBeacon(mcKey, String(src, 0, plen, Charsets.US_ASCII), -1L)
                                        if (info != null && (info.epoch > mcLastEpoch || (mcDir == null && info.epoch == mcLastEpoch))) {
                                            mcDir = WfasCrypto.deriveMulticast(mcKey, info.salt)
                                            mcWin = WfasCrypto.ReplayWindow()
                                            if (info.epoch > mcLastEpoch) {
                                                mcLastEpoch = info.epoch
                                                SettingsDataStore(context).setMcastClientEpoch(serverInfo.ip, info.epoch)
                                            }
                                        }
                                    }
                                    return
                                }
                                if (plen == 3 && String(src, 0, 3, Charsets.UTF_8) == "BYE") {
                                    if (disconnectionSoundEnabled) { playDisconnectionSound(context); disconnectionSoundPlayed = true }
                                    stopLoop = true
                                    return
                                }
                                audioPackets.add(src.copyOf(plen))
                            }

                            suspend fun playMc(audio: ByteArray) {
                                val len = audio.size
                                if (len >= MC_HEADER_SIZE && audio[0] == MC_MAGIC_0 && audio[1] == MC_MAGIC_1) {
                                    if (!mcVersionChecked) {
                                        mcVersionChecked = true
                                        val packetVersion = audio[2].toInt() and 0xFF
                                        if (packetVersion != WFAS_PROTOCOL_VERSION) {
                                            signalProtocolMismatch(packetVersion)
                                            connectionStatus.value = context.getString(R.string.status_protocol_incompatible)
                                            mcAbort = true
                                            return
                                        }
                                    }
                                    if ((audio[3].toInt() and WfasCrypto.FLAG_ENCRYPTED) != 0) {
                                        val dir = mcDir
                                        if (dir != null) {
                                            val r = WfasCrypto.decryptPacket(dir, mcWin, audio, len)
                                            if (r is WfasCrypto.Decrypted.Ok && r.pcm.isNotEmpty()) {
                                                denoiseInPlace(r.pcm, 0, r.pcm.size)
                                                audioTrack.write(r.pcm, 0, r.pcm.size, AudioTrack.WRITE_BLOCKING)
                                            }
                                        }
                                    } else {
                                        val pcmLen = len - MC_HEADER_SIZE
                                        if (pcmLen > 0) {
                                            denoiseInPlace(audio, MC_HEADER_SIZE, pcmLen)
                                            audioTrack.write(audio, MC_HEADER_SIZE, pcmLen, AudioTrack.WRITE_BLOCKING)
                                        }
                                    }
                                } else {
                                    denoiseInPlace(audio, 0, len)
                                    audioTrack.write(audio, 0, len, AudioTrack.WRITE_BLOCKING)
                                }
                            }

                            classify(packet.data, packet.length)
                            multicastSocket.soTimeout = 1
                            while (!stopLoop) {
                                try {
                                    multicastSocket.receive(drainPacket)
                                } catch (_: java.net.SocketTimeoutException) {
                                    break
                                }
                                classify(drainPacket.data, drainPacket.length)
                            }
                            multicastSocket.soTimeout = 2000

                            if (stopLoop) break
                            if (audioPackets.isEmpty()) continue

                            if (audioPackets.size > maxLagPackets) {
                                playMc(audioPackets[audioPackets.size - 1])
                            } else {
                                for (a in audioPackets) {
                                    if (!isActive) break
                                    playMc(a)
                                }
                            }
                            if (mcAbort) break
                        }
                    } finally {
                        audioTrack?.stop()
                        audioTrack?.release()
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
                if (e is TimeoutCancellationException) {
                    connectionStatus.value = "Timeout connessione al server"
                } else if (e !is CancellationException) {
                    connectionStatus.value = context.getString(R.string.status_client_error, e.message)
                }
            } finally {
                micStreamingJob?.cancel()
                micStreamingJob = null
                isMicMuted.value = false
                if (connectedSuccessfully && !disconnectionSoundPlayed && disconnectionSoundEnabled) {
                    playDisconnectionSound(context)
                }
                if (isStreamingCurrent.value) {
                    scope.launch(Dispatchers.Main) {
                        val currentStatus = connectionStatus.value
                        stopStreaming(context)

                        val contactingPrefix = context.getString(R.string.status_contacting_server, "").substringBefore("%")
                        val waitingClientPrefix = context.getString(R.string.status_waiting_for_client, 0).substringBefore("%")
                        val joiningPrefix = context.getString(R.string.status_joining_multicast)
                        val waitingAckPrefix = context.getString(R.string.status_waiting_for_ack)

                        if (currentStatus != context.getString(R.string.status_idle) &&
                            currentStatus != context.getString(R.string.status_streaming) &&
                            !currentStatus.startsWith(contactingPrefix) &&
                            !currentStatus.startsWith(waitingClientPrefix) &&
                            !currentStatus.startsWith(joiningPrefix) &&
                            !currentStatus.startsWith(waitingAckPrefix)
                        ) {
                            connectionStatus.value = currentStatus
                        }
                        onServerDisconnected?.invoke()
                    }
                }
            }
        }
    }

    fun stopStreaming(context: Context) {
        // Va letto prima dell'azzeramento: serve a sapere se eravamo noi il server.
        val wasServing = isServerStreaming
        isServerStreaming = false
        cancelDonationTimer()

        try { activeInternalRecord?.stop() } catch (_: Exception) {}
        try { activeMicRecord?.stop() } catch (_: Exception) {}

        streamingJob?.cancel()
        micStreamingJob?.cancel()
        rtpJob?.cancel()
        httpJob?.cancel()

        try { activeInternalRecord?.release() } catch (_: Exception) {}
        activeInternalRecord = null

        try { activeMicRecord?.release() } catch (_: Exception) {}
        activeMicRecord = null

        streamingJob = null
        micStreamingJob = null
        rtpJob = null
        httpJob = null
        httpServerSocket = null

        try { httpServerSocket?.close() } catch (_: Exception) {}

        originalMediaVolume?.let {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0)
            originalMediaVolume = null
        }

        stopBroadcastingPresence()
        if (wasServing) announceServerGone(context)

        connectionStatus.value = context.getString(R.string.status_idle)
        isStreamingCurrent.value = false
    }

    fun playConnectionSound(context: Context) {
        try {
            val player = android.media.MediaPlayer.create(context, R.raw.connection_sound)
            player?.setOnCompletionListener { it.release() }
            player?.start()
        } catch (_: Exception) {}
    }

    fun playDisconnectionSound(context: Context) {
        try {
            val player = android.media.MediaPlayer.create(context, R.raw.disconnection_sound)
            player?.setOnCompletionListener { it.release() }
            player?.start()
        } catch (_: Exception) {}
    }

    fun stopListeningForDevices() {
        listeningJob?.cancel()
        listeningJob = null
    }

    fun isListeningActive(): Boolean = listeningJob?.isActive == true

    fun stopAll() {
        stopBroadcastingPresence()
        stopListeningForDevices()
        scope.cancel()
    }

    private fun addADTStoPacket(packet: ByteArray, packetLen: Int, sampleRate: Int, channels: Int) {
        val profile = 2 // AAC LC
        val freqIdx = when (sampleRate) {
            96000 -> 0; 88200 -> 1; 64000 -> 2; 48000 -> 3; 44100 -> 4; 32000 -> 5;
            24000 -> 6; 22050 -> 7; 16000 -> 8; 12000 -> 9; 11025 -> 10; 8000 -> 11; else -> 3
        }
        packet[0] = 0xFF.toByte()
        packet[1] = 0xF9.toByte()
        packet[2] = (((profile - 1) shl 6) + (freqIdx shl 2) + (channels shr 2)).toByte()
        packet[3] = (((channels and 3) shl 6) + (packetLen shr 11)).toByte()
        packet[4] = ((packetLen and 0x7FF) shr 3).toByte()
        packet[5] = (((packetLen and 7) shl 5) + 0x1F).toByte()
        packet[6] = 0xFC.toByte()
    }

    fun getCurrentSsid(context: Context): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val info = wifiManager.connectionInfo
        return info.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: ""
    }

    suspend fun updateWidgetState(context: Context, isStreaming: Boolean, isServer: Boolean) {
        val manager = androidx.glance.appwidget.GlanceAppWidgetManager(context)

        // Aggiorna ServerWidget
        manager.getGlanceIds(ServerWidget::class.java).forEach { id ->
            updateAppWidgetState(context, id) { prefs ->
                prefs[WidgetKeys.IS_STREAMING] = isStreaming
                prefs[WidgetKeys.IS_SERVER] = isServer
            }
            ServerWidget().update(context, id)
        }

        // Aggiorna ClientWidget
        manager.getGlanceIds(ClientWidget::class.java).forEach { id ->
            updateAppWidgetState(context, id) { prefs ->
                prefs[WidgetKeys.IS_STREAMING] = isStreaming
                prefs[WidgetKeys.IS_SERVER] = isServer
            }
            ClientWidget().update(context, id)
        }
    }

    @SuppressLint("MissingPermission")
    private fun CoroutineScope.launchHttpSidecar(
        sampleRate: Int,
        channels: Int,
        port: Int
    ) = launch(Dispatchers.IO) {
        val queue = java.util.concurrent.ArrayBlockingQueue<ByteArray>(50)
        httpPcmQueue = queue

        var serverSocket: java.net.ServerSocket? = null
        try {
            serverSocket = java.net.ServerSocket(port)
            httpServerSocket = serverSocket
            println("HTTP Server avviato sulla porta $port")

            while (isActive) {
                val client = serverSocket.accept()
                launch(Dispatchers.IO) {
                    try {
                        val input = java.io.BufferedReader(java.io.InputStreamReader(client.inputStream))
                        val output = client.outputStream
                        val requestLine = input.readLine() ?: return@launch

                        if (requestLine.startsWith("GET /stream ")) {
                            // --- IL BROWSER VUOLE L'AUDIO ---
                            output.write("HTTP/1.1 200 OK\r\nContent-Type: audio/aac\r\nConnection: keep-alive\r\nCache-Control: no-cache\r\n\r\n".toByteArray())
                            output.flush()

                            val format = android.media.MediaFormat.createAudioFormat(android.media.MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
                                setInteger(android.media.MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                                setInteger(android.media.MediaFormat.KEY_BIT_RATE, 128000)
                                // FIX 2: Obblighiamo il chip ad accettare input giganti senza andare in overflow
                                setInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE, 100000)
                            }

                            val codec = android.media.MediaCodec.createEncoderByType(android.media.MediaFormat.MIMETYPE_AUDIO_AAC)
                            codec.configure(format, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE)
                            codec.start()

                            val bufferInfo = android.media.MediaCodec.BufferInfo()
                            try {
                                while (isActive && !client.isClosed) {
                                    val pcmData = queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                                    if (pcmData != null) {
                                        var offset = 0
                                        while (offset < pcmData.size && isActive && !client.isClosed) {
                                            val inputIndex = codec.dequeueInputBuffer(10000)
                                            if (inputIndex >= 0) {
                                                val inputBuffer = codec.getInputBuffer(inputIndex)
                                                inputBuffer?.clear()
                                                val capacity = inputBuffer?.capacity() ?: pcmData.size
                                                val chunk = minOf(pcmData.size - offset, capacity)
                                                inputBuffer?.put(pcmData, offset, chunk)
                                                codec.queueInputBuffer(inputIndex, 0, chunk, System.nanoTime() / 1000, 0)
                                                offset += chunk
                                            }
                                        }
                                    }

                                    var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                                    while (outputIndex >= 0) {
                                        val outputBuffer = codec.getOutputBuffer(outputIndex)

                                        // --- FIX 1: IGNORA IL METADATO DI SISTEMA CHE FA CRASHARE IL BROWSER! ---
                                        if ((bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                            bufferInfo.size = 0
                                        }
                                        // ------------------------------------------------------------------------

                                        if (outputBuffer != null && bufferInfo.size > 0) {
                                            val outData = ByteArray(bufferInfo.size + 7)
                                            addADTStoPacket(outData, outData.size, sampleRate, channels)
                                            outputBuffer.position(bufferInfo.offset)
                                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                            outputBuffer.get(outData, 7, bufferInfo.size)

                                            output.write(outData)
                                            output.flush()
                                        }
                                        codec.releaseOutputBuffer(outputIndex, false)
                                        outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                                    }
                                }
                            } catch (e: Exception) {
                                println("Client HTTP disconnesso regolarmente (Pipe rotta).")
                            } finally {
                                codec.stop()
                                codec.release()

                            }
                        } else {
                            // --- LA PAGINA WEB ---
                            val html = """
                                <!DOCTYPE html>
                                <html lang="en">
                                <head>
                                    <meta charset="UTF-8">
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                    <title>WiFi Audio Streamer</title>
                                    <style>
                                        :root { --bg: #0f0f0f; --surface: #1e1e1e; --primary: #BB86FC; --text: #e0e0e0; --text-mut: #888; }
                                        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background: var(--bg); color: var(--text); display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }
                                        .card { background: var(--surface); padding: 32px; border-radius: 28px; box-shadow: 0 8px 32px rgba(0,0,0,0.5); text-align: center; max-width: 400px; width: 100%; border: 1px solid #2a2a2a; }
                                        .icon { font-size: 48px; margin-bottom: 12px; }
                                        h2 { margin: 0 0 8px 0; font-size: 22px; font-weight: 600; color: #fff; }
                                        p.subtitle { color: var(--text-mut); margin: 0 0 24px 0; font-size: 14px; }
                                        audio { width: 100%; border-radius: 50px; outline: none; display: none; }
                                        .play-btn { background: var(--primary); color: #000; border: none; padding: 16px 32px; border-radius: 50px; font-size: 16px; font-weight: bold; cursor: pointer; transition: transform 0.1s, opacity 0.2s; width: 100%; margin-bottom: 16px; }
                                        .play-btn:active { transform: scale(0.96); }
                                        .links { display: flex; flex-direction: column; gap: 10px; margin-top: 24px; }
                                        .links a { text-decoration: none; color: var(--text); background: rgba(255,255,255,0.05); padding: 14px; border-radius: 16px; font-size: 14px; transition: background 0.2s; border: 1px solid rgba(255,255,255,0.05); font-weight: 500; }
                                        .links a:hover { background: rgba(255,255,255,0.1); }
                                        .kofi { margin-top: 24px; padding-top: 20px; border-top: 1px solid rgba(255,255,255,0.05); }
                                        .kofi a { color: #FF5E5B; text-decoration: none; font-weight: bold; font-size: 15px; transition: opacity 0.2s; }
                                        .kofi a:hover { opacity: 0.8; }
                                    </style>
                                </head>
                                <body>
                                    <div class="card">
                                        <div class="icon">🎧</div>
                                        <h2>WiFi Audio Streaming</h2>
                                        <p class="subtitle">Codec AAC</p>
                                        
                                        <audio id="player" controls src="/stream"></audio>
                                        <button id="playBtn" class="play-btn" onclick="document.getElementById('player').style.display='block'; document.getElementById('player').play(); this.style.display='none';">▶ PLAY AUDIO</button>

                                        <div class="links">
                                            <a href="https://github.com/marcomorosi06/WiFiAudioStreaming-Desktop" target="_blank">💻 Get Desktop App (GitHub)</a>
                                            <a href="https://github.com/marcomorosi06/WiFiAudioStreaming-Android" target="_blank">📱 Get Android App (GitHub)</a>
                                            <a href="https://apt.izzysoft.de/fdroid/index/apk/com.cuscus.wifiaudiostreaming" target="_blank">📲 Get Android App (IzzyOnDroid)</a>
                                        </div>

                                        <div class="kofi">
                                            <a href="https://ko-fi.com/marcomorosi" target="_blank">☕ Support me on Ko-fi</a>
                                        </div>
                                    </div>
                                </body>
                                </html>
                            """.trimIndent()
                            val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: ${html.toByteArray().size}\r\nConnection: close\r\n\r\n$html"
                            output.write(response.toByteArray())
                            output.flush()
                            client.close()
                        }
                    } catch (e: Exception) {
                        client.close()
                    }
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) println("HTTP Server error: ${e.message}")
        } finally {
            serverSocket?.close()
            if (httpPcmQueue == queue) {
                httpPcmQueue = null
            }
        }
    }
}