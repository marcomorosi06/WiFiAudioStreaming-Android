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

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

object DlnaCodecSupport {
    private val cache = ConcurrentHashMap<String, Set<DlnaCodec>>()

    fun available(sampleRate: Int, channels: Int): Set<DlnaCodec> =
        cache.getOrPut("$sampleRate/$channels") {
            val out = LinkedHashSet<DlnaCodec>()
            out.add(DlnaCodec.LPCM)
            out.add(DlnaCodec.WAV)
            if (aacUsable(sampleRate, channels)) out.add(DlnaCodec.ADTS)
            DlnaDiagnostics.record(
                "codec",
                "encoders usable at ${sampleRate}Hz/${channels}ch: ${out.joinToString(", ") { it.id }}"
            )
            out
        }

    private fun aacUsable(sampleRate: Int, channels: Int): Boolean = runCatching {
        if (channels !in 1..2) return false
        val probe = AacAdtsEncoder.create(sampleRate, channels, 320_000) ?: return false
        probe.release()
        true
    }.getOrDefault(false)
}

internal class AacAdtsEncoder private constructor(
    private val codec: MediaCodec,
    private val sampleRate: Int,
    private val channels: Int
) {
    private val bufferInfo = MediaCodec.BufferInfo()
    private val frequencyIndex = when (sampleRate) {
        96000 -> 0; 88200 -> 1; 64000 -> 2; 48000 -> 3; 44100 -> 4
        32000 -> 5; 24000 -> 6; 22050 -> 7; 16000 -> 8; 12000 -> 9
        11025 -> 10; 8000 -> 11; 7350 -> 12; else -> 4
    }
    private var presentationTimeUs = 0L

    fun feed(pcmLittleEndian: ByteArray, onPacket: (ByteArray) -> Unit) {
        var offset = 0
        while (offset < pcmLittleEndian.size) {
            val inputIndex = codec.dequeueInputBuffer(10_000)
            if (inputIndex < 0) break
            val inputBuffer = codec.getInputBuffer(inputIndex) ?: break
            inputBuffer.clear()
            val chunk = minOf(pcmLittleEndian.size - offset, inputBuffer.capacity())
            inputBuffer.put(pcmLittleEndian, offset, chunk)
            codec.queueInputBuffer(inputIndex, 0, chunk, presentationTimeUs, 0)
            val frames = chunk / 2 / channels
            presentationTimeUs += frames * 1_000_000L / sampleRate
            offset += chunk
            drain(onPacket)
        }
        drain(onPacket)
    }

    private fun drain(onPacket: (ByteArray) -> Unit) {
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            if (outputIndex < 0) break
            val outputBuffer = codec.getOutputBuffer(outputIndex)
            val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
            if (outputBuffer != null && bufferInfo.size > 0 && !isConfig) {
                val payload = ByteArray(bufferInfo.size + 7)
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                outputBuffer.get(payload, 7, bufferInfo.size)
                writeAdtsHeader(payload, payload.size)
                onPacket(payload)
            }
            codec.releaseOutputBuffer(outputIndex, false)
        }
    }

    private fun writeAdtsHeader(target: ByteArray, frameLength: Int) {
        target[0] = 0xFF.toByte()
        target[1] = 0xF1.toByte()
        target[2] = ((1 shl 6) or (frequencyIndex shl 2) or (channels shr 2)).toByte()
        target[3] = (((channels and 3) shl 6) or (frameLength shr 11)).toByte()
        target[4] = ((frameLength shr 3) and 0xFF).toByte()
        target[5] = (((frameLength and 7) shl 5) or 0x1F).toByte()
        target[6] = 0xFC.toByte()
    }

    fun release() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }

    companion object {
        fun create(sampleRate: Int, channels: Int, bitRate: Int): AacAdtsEncoder? = runCatching {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 100_000)
            }
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            AacAdtsEncoder(codec, sampleRate, channels)
        }.getOrNull()
    }
}

private class DlnaClient(
    val codec: DlnaCodec,
    val stream: OutputStream,
    val remoteAddress: String
) {
    @Volatile
    var alive: Boolean = true
}

class DlnaMediaServer(
    private val scope: CoroutineScope,
    private val port: Int,
    private val sampleRate: Int,
    private val channels: Int,
    private val quirksForAddress: (String) -> DlnaQuirks
) {
    private companion object {
        const val BIND_ATTEMPTS = 8
        const val BIND_RETRY_DELAY_MS = 400L
    }

    private val clients = CopyOnWriteArrayList<DlnaClient>()
    private val pcmQueue = ArrayBlockingQueue<ByteArray>(12)

    @Volatile
    private var acceptorJob: Job? = null

    @Volatile
    private var pumpJob: Job? = null

    @Volatile
    var bindFailure: String? = null
        private set

    fun clientCount(): Int = clients.count { it.alive }

    fun clientCount(codec: DlnaCodec): Int = clients.count { it.alive && it.codec == codec }

    fun submitPcm(pcmLittleEndian: ByteArray) {
        if (clients.isEmpty()) return
        while (!pcmQueue.offer(pcmLittleEndian)) {
            if (pcmQueue.poll() == null) break
        }
    }

    fun start() {
        acceptorJob = scope.launch(Dispatchers.IO) { runAcceptor(this) }
        pumpJob = scope.launch(Dispatchers.IO) { runPump(this) }
    }

    suspend fun stop() {
        val acceptor = acceptorJob
        val pump = pumpJob
        acceptorJob = null
        pumpJob = null
        clients.forEach { client ->
            client.alive = false
            runCatching { client.stream.close() }
        }
        clients.clear()
        pcmQueue.clear()
        runCatching { acceptor?.cancelAndJoin() }
        runCatching { pump?.cancelAndJoin() }
    }

    private suspend fun bindServerSocket(owner: CoroutineScope): ServerSocket? {
        var attempt = 0
        while (owner.isActive && attempt < BIND_ATTEMPTS) {
            attempt++
            val socket = ServerSocket()
            try {
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(port))
                socket.soTimeout = 500
                return socket
            } catch (e: Exception) {
                runCatching { socket.close() }
                if (e is CancellationException) throw e
                DlnaDiagnostics.record(
                    "media",
                    "bind on port $port failed (attempt $attempt/$BIND_ATTEMPTS): ${e.javaClass.simpleName} ${e.message}"
                )
                delay(BIND_RETRY_DELAY_MS)
            }
        }
        return null
    }

    private suspend fun runAcceptor(owner: CoroutineScope) {
        val serverSocket = bindServerSocket(owner)
        if (serverSocket == null) {
            bindFailure = "port $port unavailable"
            DlnaDiagnostics.record("media", "giving up on port $port, DLNA output is not listening")
            return
        }
        bindFailure = null
        DlnaDiagnostics.record("media", "media server listening on port $port")
        try {
            while (owner.isActive) {
                val socket = try {
                    serverSocket.accept()
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                } catch (e: Exception) {
                    if (!serverSocket.isClosed && e !is CancellationException) {
                        DlnaDiagnostics.record("media", "accept failed: ${e.message}")
                    }
                    break
                }
                owner.launch(Dispatchers.IO) { handle(socket, owner) }
            }
        } finally {
            runCatching { serverSocket.close() }
            DlnaDiagnostics.record("media", "media server stopped")
        }
    }

    private suspend fun handle(socket: java.net.Socket, owner: CoroutineScope) {
        val remote = socket.inetAddress?.hostAddress.orEmpty()
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 3000
            val requestText = readRequestHead(socket.getInputStream()) ?: return
            val lines = requestText.split("\r\n")
            val requestLine = lines.firstOrNull().orEmpty()
            val method = requestLine.substringBefore(' ').uppercase()
            val path = (requestLine.split(' ').getOrNull(1) ?: "/").substringBefore('?')
            val headers = HashMap<String, String>()
            lines.drop(1).forEach { line ->
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }

            DlnaDiagnostics.record(
                "media",
                "$method $path from $remote :: ua=${headers["user-agent"].orEmpty()} " +
                        "transferMode=${headers["transfermode.dlna.org"].orEmpty()} range=${headers["range"].orEmpty()}"
            )

            val codec = DlnaCodec.entries.firstOrNull { it.path == path }
            val output = socket.getOutputStream()

            if (codec == null) {
                val body = "WiFi Audio Streaming DLNA endpoint".toByteArray()
                output.write(
                    ("HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\nContent-Length: ${body.size}\r\n" +
                            "Connection: close\r\n\r\n").toByteArray()
                )
                output.write(body)
                output.flush()
                return
            }

            output.write(buildResponseHeader(codec, quirksForAddress(remote)))
            output.flush()

            if (method == "HEAD") return

            if (codec == DlnaCodec.WAV) {
                output.write(wavStreamingHeader())
                output.flush()
            }

            val client = DlnaClient(codec, output, remote)
            clients.add(client)
            DlnaDiagnostics.record("media", "client attached: $remote codec=${codec.id}")
            try {
                while (owner.isActive && client.alive && !socket.isClosed) {
                    delay(400)
                }
            } finally {
                client.alive = false
                clients.remove(client)
                DlnaDiagnostics.record("media", "client detached: $remote codec=${codec.id}")
            }
        } catch (e: Exception) {
            if (e !is CancellationException) DlnaDiagnostics.record("media", "handler error from $remote: ${e.message}")
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun readRequestHead(input: java.io.InputStream): String? {
        val buffer = StringBuilder()
        var consecutiveNewlines = 0
        var total = 0
        while (total < 16384) {
            val value = input.read()
            if (value < 0) return if (buffer.isEmpty()) null else buffer.toString()
            total++
            val ch = value.toChar()
            buffer.append(ch)
            when {
                ch == '\n' -> {
                    consecutiveNewlines++
                    if (consecutiveNewlines >= 2) return buffer.toString()
                }
                ch == '\r' -> Unit
                else -> consecutiveNewlines = 0
            }
        }
        return buffer.toString()
    }

    private fun buildResponseHeader(codec: DlnaCodec, quirks: DlnaQuirks): ByteArray {
        val mime = quirks.mimeOverride[codec] ?: codec.defaultMime(sampleRate, channels)
        val profile = quirks.pnOverride[codec] ?: codec.defaultPn
        val contentFeatures = "DLNA.ORG_PN=$profile;DLNA.ORG_OP=00;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=${quirks.flags}"
        return buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: ").append(mime).append("\r\n")
            append("transferMode.dlna.org: Streaming\r\n")
            append("contentFeatures.dlna.org: ").append(contentFeatures).append("\r\n")
            append("EXT:\r\n")
            append("Server: ").append(DlnaConst.USER_AGENT).append("\r\n")
            append("Accept-Ranges: none\r\n")
            append("Cache-Control: no-cache\r\n")
            append("Pragma: no-cache\r\n")
            if (quirks.requireContentLength) append("Content-Length: 2147483647\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(Charsets.US_ASCII)
    }

    private fun wavStreamingHeader(): ByteArray {
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(-1)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(-1)
        return buffer.array()
    }

    private fun toBigEndian(pcmLittleEndian: ByteArray): ByteArray {
        val out = ByteArray(pcmLittleEndian.size)
        var i = 0
        while (i + 1 < pcmLittleEndian.size) {
            out[i] = pcmLittleEndian[i + 1]
            out[i + 1] = pcmLittleEndian[i]
            i += 2
        }
        return out
    }

    private fun broadcast(codec: DlnaCodec, data: ByteArray) {
        val dead = ArrayList<DlnaClient>()
        clients.forEach { client ->
            if (client.codec != codec || !client.alive) return@forEach
            try {
                client.stream.write(data)
                client.stream.flush()
            } catch (_: Exception) {
                client.alive = false
                dead.add(client)
            }
        }
        if (dead.isNotEmpty()) clients.removeAll(dead)
    }

    private suspend fun runPump(owner: CoroutineScope) {
        var aacEncoder: AacAdtsEncoder? = null
        try {
            while (owner.isActive) {
                val pcm = pcmQueue.poll(200, TimeUnit.MILLISECONDS) ?: continue

                if (clientCount(DlnaCodec.LPCM) > 0) broadcast(DlnaCodec.LPCM, toBigEndian(pcm))
                if (clientCount(DlnaCodec.WAV) > 0) broadcast(DlnaCodec.WAV, pcm)

                if (clientCount(DlnaCodec.ADTS) > 0) {
                    if (aacEncoder == null) {
                        aacEncoder = AacAdtsEncoder.create(sampleRate, channels, 320_000)
                        if (aacEncoder == null) DlnaDiagnostics.record("media", "aac encoder unavailable")
                    }
                    aacEncoder?.feed(pcm) { broadcast(DlnaCodec.ADTS, it) }
                } else if (aacEncoder != null) {
                    aacEncoder.release()
                    aacEncoder = null
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) DlnaDiagnostics.record("media", "pump error: ${e.message}")
        } finally {
            aacEncoder?.release()
        }
    }
}
