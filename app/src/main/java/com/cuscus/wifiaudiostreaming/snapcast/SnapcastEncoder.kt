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

package com.cuscus.wifiaudiostreaming.snapcast

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import java.nio.ByteBuffer

data class SnapcastAudioFormat(
    val sampleRate: Int,
    val bitDepth: Int,
    val channels: Int
) {

    val frameBytes: Int get() = channels * bitDepth / 8

    val bytesPerMs: Int get() = sampleRate * frameBytes / 1000

    fun framesOf(byteCount: Int): Int = if (frameBytes <= 0) 0 else byteCount / frameBytes

    fun describe(): String = "$sampleRate:$bitDepth:$channels"
}

interface SnapcastEncoder {

    val codecName: String

    val header: ByteArray

    val outputFormat: SnapcastAudioFormat

    fun encode(pcm: ByteArray, offset: Int, length: Int, sink: (ByteArray, Int) -> Unit)

    fun close()
}

class SnapcastPcmEncoder(override val outputFormat: SnapcastAudioFormat) : SnapcastEncoder {

    override val codecName: String = SnapcastCodecs.PCM

    override val header: ByteArray =
        SnapcastWire.wavHeader(outputFormat.sampleRate, outputFormat.bitDepth, outputFormat.channels)

    override fun encode(pcm: ByteArray, offset: Int, length: Int, sink: (ByteArray, Int) -> Unit) {
        if (length <= 0) return
        sink(pcm.copyOfRange(offset, offset + length), outputFormat.framesOf(length))
    }

    override fun close() = Unit
}

class SnapcastMediaCodecEncoder private constructor(
    override val codecName: String,
    override val outputFormat: SnapcastAudioFormat,
    private val codec: MediaCodec,
    private val framesPerPacket: Int,
    initialHeader: ByteArray
) : SnapcastEncoder {

    override var header: ByteArray = initialHeader
        private set

    private val bufferInfo = MediaCodec.BufferInfo()
    private var presentationUs = 0L
    private var closed = false

    override fun encode(pcm: ByteArray, offset: Int, length: Int, sink: (ByteArray, Int) -> Unit) {
        if (closed || length <= 0) return
        var written = 0
        while (written < length) {
            val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (inputIndex < 0) {
                drain(sink)
                continue
            }
            val inputBuffer: ByteBuffer = codec.getInputBuffer(inputIndex) ?: continue
            inputBuffer.clear()
            val chunk = minOf(inputBuffer.capacity(), length - written)
            inputBuffer.put(pcm, offset + written, chunk)
            codec.queueInputBuffer(inputIndex, 0, chunk, presentationUs, 0)
            val frames = outputFormat.framesOf(chunk)
            presentationUs += frames.toLong() * 1_000_000L / outputFormat.sampleRate
            written += chunk
            drain(sink)
        }
    }

    private fun drain(sink: (ByteArray, Int) -> Unit) {
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) return
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                header = headerFromFormat(codecName, codec.outputFormat, outputFormat) ?: header
                continue
            }
            if (outputIndex < 0) return
            val buffer = codec.getOutputBuffer(outputIndex)
            val isConfig = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
            if (buffer != null && bufferInfo.size > 0) {
                val payload = ByteArray(bufferInfo.size)
                buffer.position(bufferInfo.offset)
                buffer.limit(bufferInfo.offset + bufferInfo.size)
                buffer.get(payload)
                if (isConfig) headerFromCsd(codecName, payload, outputFormat)?.let { header = it }
                else sink(payload, framesPerPacket)
            }
            codec.releaseOutputBuffer(outputIndex, false)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { codec.stop() }
        runCatching { codec.release() }
    }

    companion object {

        private const val DEQUEUE_TIMEOUT_US = 5_000L

        private fun headerFromFormat(
            codecName: String,
            format: MediaFormat,
            audioFormat: SnapcastAudioFormat
        ): ByteArray? {
            val csd = runCatching { format.getByteBuffer("csd-0") }.getOrNull() ?: return null
            val raw = ByteArray(csd.remaining())
            csd.duplicate().get(raw)
            return headerFromCsd(codecName, raw, audioFormat)
        }

        private fun headerFromCsd(
            codecName: String,
            raw: ByteArray,
            audioFormat: SnapcastAudioFormat
        ): ByteArray? = when (codecName) {
            SnapcastCodecs.FLAC -> {
                val streamInfo = when {
                    raw.size >= 42 &&
                        raw[0] == 'f'.code.toByte() && raw[1] == 'L'.code.toByte() &&
                        raw[2] == 'a'.code.toByte() && raw[3] == 'C'.code.toByte() ->
                        raw.copyOfRange(8, 42)
                    raw.size >= 34 -> raw.copyOfRange(0, 34)
                    else -> ByteArray(0)
                }
                SnapcastWire.flacHeader(streamInfo).takeIf { it.isNotEmpty() }
            }
            SnapcastCodecs.OPUS ->
                SnapcastWire.opusHeader(audioFormat.sampleRate, audioFormat.bitDepth, audioFormat.channels)
            else -> null
        }

        fun createFlac(format: SnapcastAudioFormat, chunkMs: Int): SnapcastMediaCodecEncoder? = runCatching {
            val mediaFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_FLAC, format.sampleRate, format.channels
            ).apply {
                setInteger(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
                setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, format.bytesPerMs * chunkMs * 2)
            }
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_FLAC)
            codec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            SnapcastMediaCodecEncoder(
                codecName = SnapcastCodecs.FLAC,
                outputFormat = format,
                codec = codec,
                framesPerPacket = format.sampleRate * chunkMs / 1000,
                initialHeader = ByteArray(0)
            )
        }.getOrNull()

        fun createOpus(format: SnapcastAudioFormat, bitrate: Int, chunkMs: Int): SnapcastMediaCodecEncoder? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            if (format.sampleRate != 48000 || format.channels != 2 || format.bitDepth != 16) return null
            return runCatching {
                val mediaFormat = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_OPUS, 48000, format.channels
                ).apply {
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                    setInteger(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, format.bytesPerMs * chunkMs * 2)
                    setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
                }
                val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
                codec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
                SnapcastMediaCodecEncoder(
                    codecName = SnapcastCodecs.OPUS,
                    outputFormat = format,
                    codec = codec,
                    framesPerPacket = 48 * chunkMs,
                    initialHeader = SnapcastWire.opusHeader(48000, 16, format.channels)
                )
            }.getOrNull()
        }
    }
}

object SnapcastCodecs {

    const val PCM = "pcm"
    const val FLAC = "flac"
    const val OPUS = "opus"

    val ALL = listOf(PCM, FLAC, OPUS)

    fun normalize(value: String?): String = when (value?.lowercase()?.trim()) {
        FLAC -> FLAC
        OPUS -> OPUS
        else -> PCM
    }

    fun create(
        codec: String,
        format: SnapcastAudioFormat,
        chunkMs: Int = SnapcastDefaults.CHUNK_MS,
        opusBitrate: Int = SnapcastDefaults.OPUS_BITRATE,
        onFallback: (String) -> Unit = {}
    ): SnapcastEncoder {
        val requested = normalize(codec)
        val built = when (requested) {
            FLAC -> SnapcastMediaCodecEncoder.createFlac(format, chunkMs)
            OPUS -> SnapcastMediaCodecEncoder.createOpus(format, opusBitrate, chunkMs)
            else -> null
        }
        if (built != null) return built
        if (requested != PCM) onFallback(requested)
        return SnapcastPcmEncoder(format)
    }
}
