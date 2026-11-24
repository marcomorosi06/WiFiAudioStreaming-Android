/*
 * Copyright (c) 2026 Marco Morosi
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
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
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.core.content.ContextCompat
import com.cuscus.wifiaudiostreaming.data.AppSettings
import com.cuscus.wifiaudiostreaming.wfas3.W3
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3Addr
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3Beacon
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3Client
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3ClientState
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3Config
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3Engine
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3Exception
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3Failure
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3Library
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3Listener
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3PairInfo
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3PcmOutput
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3Peer
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3Resampler
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3Server
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3ServerSettings
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3Store
import com.cuscus.wifiaudiostreaming.wfas3.Wfas3WindowCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The v3 choices of the settings, and how they become a server configuration. */
object Wfas3Settings {
    const val PROTOCOL_V3 = "v3"
    const val PROTOCOL_V2 = "v2"
    const val CODEC_PCM = "pcm"
    const val CODEC_OPUS = "opus"
    const val OPUS_KBPS_MIN = 32
    const val OPUS_KBPS_MAX = 510
    val OPUS_KBPS_STEPS = listOf(64, 96, 128, 160, 192, 256, 320, 384, 510)
    val OPUS_FRAMES = listOf(120, 240, 480, 960)
    val PCM_RATES = listOf(44100, 48000, 88200, 96000, 176400, 192000)
    val LATENCIES = listOf(20, 40, 60, 80, 120, 200, 300, 500)

    fun isV3(app: AppSettings): Boolean = app.wfasProtocol != PROTOCOL_V2

    /** An Android server plays no microphone of its clients, as with v2. */
    fun server(app: AppSettings, multicast: Boolean): Wfas3ServerSettings {
        val rate = app.sampleRate.takeIf { it in PCM_RATES } ?: 48000
        return Wfas3ServerSettings(
            codec = if (app.v3Codec == CODEC_OPUS) W3.CODEC_OPUS else W3.CODEC_PCM,
            sampleRate = rate,
            channels = if (app.channelConfig == "STEREO") 2 else 1,
            bits = 16,
            opusKbps = app.v3OpusKbps.coerceIn(OPUS_KBPS_MIN, OPUS_KBPS_MAX),
            opusFrameSamples = app.v3OpusFrameSamples.takeIf { it in OPUS_FRAMES } ?: 480,
            latencyMs = app.v3LatencyMs.coerceIn(20, 1000),
            sync = app.v3Sync,
            multicast = multicast,
            acceptMicrophone = false,
            acceptNew = app.v3AcceptNew,
        )
    }

    /**
     * The name other devices see. By default the model name, which says what
     * the device is without saying whose it is; the user can choose another.
     */
    fun deviceName(app: AppSettings): String = app.v3DeviceName.trim().ifEmpty {
        Build.MODEL?.trim()?.takeIf { it.isNotEmpty() } ?: "Android"
    }
}

/**
 * The records of WFAS v3 on this device: its keys and one file per pairing.
 *
 * They live in no_backup/wfas3, which the system never copies elsewhere: an
 * older copy of a pairing restored on another phone would give its keys to
 * two devices. Each file is sealed with AES-256-GCM under a key kept in the
 * Android Keystore, which never leaves it. Writes go to a temporary file,
 * are synced, then renamed, so a record is either the old or the new one.
 *
 * The Keystore key is made only for an empty folder. With records on disk
 * and no key (a Keystore reset by the system, a folder copied from another
 * install) a new key would make every record unreadable and the next save
 * would replace the device record: the store stops with STORE_KEY instead
 * and touches nothing.
 */
class Wfas3KeystoreStore(context: Context) : Wfas3Store {
    private val dir = File(context.noBackupFilesDir, "wfas3")

    private fun ensureDir() {
        if (!dir.isDirectory && !dir.mkdirs()) throw Wfas3Exception(Wfas3Failure.STORAGE, "store folder")
    }

    private fun hasRecords(): Boolean =
        dir.listFiles()?.any { it.isFile && it.name.endsWith(".rec") } ?: false

    override fun hasPairRecords(): Boolean =
        dir.listFiles()?.any { it.isFile && it.name.startsWith("pair-") && it.name.endsWith(".rec") } ?: false

    private fun key(create: Boolean): SecretKey? {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) return null
        // generateKey replaces an entry of the same alias: never while the
        // alias is still there, nor while records sealed by a lost key exist.
        if (ks.containsAlias(ALIAS) || hasRecords()) throw Wfas3Exception(Wfas3Failure.STORE_KEY, "store key missing")
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return gen.generateKey()
    }

    private fun seal(name: String, data: ByteArray): ByteArray {
        // A key is made only when there is something to keep: never to replace
        // one that existing records still need.
        val k = key(create = true) ?: throw Wfas3Exception(Wfas3Failure.STORAGE, "keystore")
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, k)
        c.updateAAD((AAD + name).toByteArray(Charsets.US_ASCII))
        val iv = c.iv
        val ct = c.doFinal(data)
        return MAGIC + byteArrayOf(iv.size.toByte()) + iv + ct
    }

    private fun open(name: String, blob: ByteArray): ByteArray? {
        if (blob.size < MAGIC.size + 1 + 12 + 16 || !blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return null
        val ivLen = blob[MAGIC.size].toInt() and 0xFF
        if (ivLen < 12 || blob.size < MAGIC.size + 1 + ivLen + 16) return null
        val iv = blob.copyOfRange(MAGIC.size + 1, MAGIC.size + 1 + ivLen)
        // A record and no key to open it: say so, never treat it as missing.
        val k = key(create = false) ?: throw Wfas3Exception(Wfas3Failure.STORE_KEY, "store key missing")
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, iv))
        c.updateAAD((AAD + name).toByteArray(Charsets.US_ASCII))
        return c.doFinal(blob, MAGIC.size + 1 + ivLen, blob.size - MAGIC.size - 1 - ivLen)
    }

    /** Null if the record does not exist; throws if it exists and cannot be read or opened. */
    private fun read(name: String): ByteArray? {
        val f = File(dir, name)
        if (!f.exists()) return null
        return try {
            open(name, f.readBytes()) ?: throw Wfas3Exception(Wfas3Failure.DEVICE_RECORD, "unreadable $name")
        } catch (x: Wfas3Exception) {
            throw x
        } catch (x: Exception) {
            throw Wfas3Exception(Wfas3Failure.DEVICE_RECORD, "unreadable $name")
        }
    }

    private fun write(name: String, data: ByteArray) {
        try {
            ensureDir()
            val blob = seal(name, data)
            val tmp = File(dir, "$name.tmp")
            FileOutputStream(tmp).use { o -> o.write(blob); o.fd.sync() }
            if (!tmp.renameTo(File(dir, name))) throw java.io.IOException("rename")
        } catch (x: Wfas3Exception) {
            throw x
        } catch (x: Exception) {
            throw Wfas3Exception(Wfas3Failure.STORAGE, "write $name")
        }
    }

    private fun erase(name: String) {
        val f = File(dir, name)
        if (!f.exists()) return
        runCatching {
            val n = f.length().toInt()
            FileOutputStream(f).use { o -> o.write(ByteArray(n)); o.fd.sync() }
        }
        if (!f.delete() && f.exists()) throw Wfas3Exception(Wfas3Failure.STORAGE, "erase $name")
    }

    override fun loadDevice(): ByteArray? = read(DEVICE)
    override fun saveDevice(rec: ByteArray) = write(DEVICE, rec)
    override fun loadPair(slot: Int): ByteArray? = read(pairName(slot))
    override fun savePair(slot: Int, rec: ByteArray) = write(pairName(slot), rec)
    override fun erasePair(slot: Int) { erase(pairName(slot)); erase(hintName(slot)) }
    override fun loadHint(slot: Int): ByteArray? = runCatching { read(hintName(slot)) }.getOrNull()
    override fun saveHint(slot: Int, data: ByteArray?) { if (data == null) erase(hintName(slot)) else write(hintName(slot), data) }

    override fun eraseAll() {
        dir.listFiles()?.forEach { f -> runCatching { erase(f.name) } }
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) }
    }

    private fun pairName(slot: Int) = "pair-%02d.rec".format(slot)
    private fun hintName(slot: Int) = "hint-%02d.rec".format(slot)

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "wfas3-store"
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val DEVICE = "device.rec"
        private const val AAD = "wfas3:"
        private val MAGIC = "W3S1".toByteArray(Charsets.US_ASCII)
    }
}

/**
 * Keeps the Wi-Fi radio out of power save while the v3 client plays. In power
 * save, packets reach the phone in bursts every ~100 ms: the playout has to
 * wait for the slowest of them, and synchronised playback cannot measure the
 * clocks (its time samples need a round trip under 15 ms). The server side
 * (AudioCaptureService) already holds the same locks.
 */
internal object Wfas3ClientLocks {
    private var wifiLow: android.net.wifi.WifiManager.WifiLock? = null
    private var wifiHigh: android.net.wifi.WifiManager.WifiLock? = null
    private var wake: android.os.PowerManager.WakeLock? = null

    @Synchronized
    @SuppressLint("WakelockTimeout")
    fun hold(context: Context, on: Boolean) {
        if (on) {
            if (wake?.isHeld == true) return
            runCatching {
                val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                wake = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "WiFiAudioStreaming::Wfas3Client").apply {
                    setReferenceCounted(false); acquire()
                }
            }
            runCatching {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
                // Low latency works while the app is in front; high performance
                // covers older systems and, where it still acts, the background.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    wifiLow = wm.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "WiFiAudioStreaming::Wfas3ClientLL").apply {
                        setReferenceCounted(false); acquire()
                    }
                }
                @Suppress("DEPRECATION")
                wifiHigh = wm.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WiFiAudioStreaming::Wfas3ClientHP").apply {
                    setReferenceCounted(false); acquire()
                }
            }
        } else {
            runCatching { wifiLow?.let { if (it.isHeld) it.release() } }
            runCatching { wifiHigh?.let { if (it.isHeld) it.release() } }
            runCatching { wake?.let { if (it.isHeld) it.release() } }
            wifiLow = null; wifiHigh = null; wake = null
        }
    }
}

/** AudioTrack output for the v3 client; formats it cannot open are converted to 48 kHz. */
class Wfas3TrackOutput private constructor(
    private val track: AudioTrack,
    private val trackRate: Int,
    private val trackChannels: Int,
    override val rate: Int,
    override val channels: Int,
) : Wfas3PcmOutput {
    private var written = 0L
    private val resampler = if (trackRate != rate || trackChannels != channels)
        Wfas3Resampler(rate, trackRate, channels, trackChannels) else null

    override fun write(buf: ShortArray, frames: Int) {
        val (data, n) = resampler?.process(buf, 0, frames) ?: (buf to frames)
        val total = n * trackChannels
        var off = 0
        while (off < total) {
            val r = track.write(data, off, total - off)
            if (r <= 0) break
            off += r
        }
        written += n
    }

    override fun pendingUs(): Long {
        val head = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        return (written - head).coerceAtLeast(0L) * 1_000_000L / trackRate
    }

    // Writes block once the buffer is full, so it is always about this full.
    override val bufferUs: Long =
        (runCatching { track.bufferSizeInFrames }.getOrNull() ?: 0).toLong() * 1_000_000L / trackRate

    override fun close() {
        runCatching { track.pause(); track.flush(); track.stop() }
        runCatching { track.release() }
    }

    companion object {
        private fun mask(ch: Int): Int = when (ch) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            4 -> AudioFormat.CHANNEL_OUT_QUAD
            6 -> AudioFormat.CHANNEL_OUT_5POINT1
            8 -> AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
            else -> 0
        }

        private fun build(rate: Int, ch: Int): AudioTrack? = runCatching {
            val m = mask(ch).takeIf { it != 0 } ?: return@runCatching null
            val min = AudioTrack.getMinBufferSize(rate, m, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) return@runCatching null
            // The audio waits in this buffer before it is heard: keep it to
            // the device minimum, or 20 ms, whichever is larger.
            val frames = maxOf(min / (ch * 2), rate / 1000 * 20)
            val b = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setChannelMask(m)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(frames * ch * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) b.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            b.build().takeIf { it.state == AudioTrack.STATE_INITIALIZED }?.also {
                // The system may give a larger buffer than asked: use only what was asked.
                runCatching { if (it.bufferSizeInFrames > frames) it.setBufferSizeInFrames(frames) }
                it.play()
            }
        }.getOrNull()

        fun open(rate: Int, channels: Int): Wfas3TrackOutput {
            build(rate, channels)?.let { return Wfas3TrackOutput(it, rate, channels, rate, channels) }
            val outCh = if (channels >= 2) 2 else 1
            build(48000, outCh)?.let { return Wfas3TrackOutput(it, 48000, outCh, rate, channels) }
            throw IllegalStateException("audio output")
        }
    }
}

/**
 * WFAS v3 for the Android app: one engine, its records, and the server, client
 * and pairing state the screens show. Everything that touches the protocol
 * goes through here, so the rules are the same wherever it is used from.
 *
 * The engine starts only when v3 is on and something asks for it; its port is
 * open only while a server, a client or a pairing needs it.
 */
object Wfas3Android : Wfas3Listener {
    private const val TAG = "WFAS3"
    private const val LOCK_OWNER = "wfas3"

    data class WindowState(val kind: Int, val text: String, val qr: String?, val openedAtMs: Long, val ask: Boolean, val paired: String? = null)
    /** [from]: the address the client asks from, shown with the question. */
    data class AskState(val ask: Int, val name: String, val flags: Int, val code: String, val pin: Boolean, val from: String = "") {
        val wantsGuest: Boolean get() = (flags and W3.PAIR_GUEST) != 0
        val wantsMic: Boolean get() = (flags and W3.PAIR_MIC) != 0
    }

    sealed class ClientPairing {
        object Idle : ClientPairing()
        data class Waiting(val pin: Boolean) : ClientPairing()
        data class Compare(val code: String) : ClientPairing()
        /** [slot]: the pair just made, so the interface can connect to it at once. */
        data class Done(val name: String, val guest: Boolean, val slot: Int = -1) : ClientPairing()
        /** [noNetwork]: nothing was sent, the port of this device could not open. */
        data class Failed(val reason: Int, val race: Boolean, val noNetwork: Boolean = false) : ClientPairing()
    }

    sealed class Notice {
        abstract val id: Long
        data class Suspect(override val id: Long, val name: String) : Notice()
        data class ForgetAsked(override val id: Long, val pair: Int, val name: String) : Notice()
        data class MicRefused(override val id: Long) : Notice()
        data class Storage(override val id: Long) : Notice()
    }

    val failure = MutableStateFlow<Wfas3Failure?>(null)
    val pairs = MutableStateFlow<List<Wfas3PairInfo>>(emptyList())
    val peers = MutableStateFlow<List<Wfas3Peer>>(emptyList())
    val clientState = MutableStateFlow<Wfas3ClientState>(Wfas3ClientState.Idle)
    val window = MutableStateFlow<WindowState?>(null)
    val windowFailures = MutableStateFlow(0)
    val asks = MutableStateFlow<List<AskState>>(emptyList())
    val pairing = MutableStateFlow<ClientPairing>(ClientPairing.Idle)
    val beacons = MutableStateFlow<List<Wfas3Beacon>>(emptyList())
    val notices = MutableStateFlow<List<Notice>>(emptyList())

    private val lock = Any()
    @Volatile private var ctx: Context? = null
    @Volatile private var app: AppSettings? = null

    /**
     * The settings last seen. Before the settings flow has configured this
     * object (a service started without the app open) they are read once from
     * the DataStore: never the defaults of an empty settings object.
     */
    val appSettings: AppSettings
        get() = app ?: loadSettings(ctx ?: throw IllegalStateException("Wfas3Android used before any context"))

    private fun loadSettings(context: Context): AppSettings {
        val read = kotlinx.coroutines.runBlocking {
            com.cuscus.wifiaudiostreaming.data.SettingsDataStore(context.applicationContext).settingsFlow.first()
        }
        if (app == null) app = read
        return app ?: read
    }
    private var store: Wfas3KeystoreStore? = null
    private var engine: Wfas3Engine? = null
    private var server: Wfas3Server? = null
    private var client: Wfas3Client? = null
    @Volatile private var clientWanted = false
    @Volatile private var pinSearching = false
    @Volatile private var pairingActive = false
    @Volatile private var locating = false
    // The slot the client is after; a locate running meanwhile leaves its search alone.
    @Volatile private var clientSlot = -1
    // One locate at a time: they share the broadcast targets of the engine.
    private val locateLock = java.util.concurrent.locks.ReentrantLock()
    @Volatile private var lockHeld = false
    private var closeTimer: Thread? = null
    private var noticeId = 0L

    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "wfas3-calls").apply { isDaemon = true }
    }

    /**
     * Engine calls can send datagrams, which Android does not allow on the main
     * thread: the screens and the main-thread paths of the app go through here,
     * one call after the other, in order.
     */
    fun bg(block: () -> Unit) {
        worker.execute { runCatching(block).onFailure { Log.w(TAG, "call failed", it) } }
    }

    val v3Enabled: Boolean get() = Wfas3Settings.isV3(appSettings)

    /**
     * The protocol in use, read from the settings if nothing has configured this
     * object yet (a service started without the app open): never the default
     * value of an empty settings object.
     */
    fun v3Enabled(context: Context): Boolean {
        if (app == null) {
            ctx = context.applicationContext
            runCatching { loadSettings(context) }
        }
        // Settings that cannot be read leave the default protocol, v3.
        return app?.let { Wfas3Settings.isV3(it) } ?: true
    }
    val serving: Boolean get() = server != null

    /** Called by the settings flow: v2 stops the engine, a live server follows the new choices. */
    fun configure(context: Context, settings: AppSettings) {
        ctx = context.applicationContext
        val before = this.app
        this.app = settings
        val app = settings
        if (!Wfas3Settings.isV3(app)) {
            if (engine != null) shutdown()
            return
        }
        val s = server ?: return
        if (before == null) return
        if (before.v3Codec != app.v3Codec || before.v3OpusKbps != app.v3OpusKbps ||
            before.v3OpusFrameSamples != app.v3OpusFrameSamples || before.v3LatencyMs != app.v3LatencyMs ||
            before.v3Sync != app.v3Sync || before.v3AcceptNew != app.v3AcceptNew
        ) {
            val next = Wfas3Settings.server(app, s.settings.multicast)
            // A new capture rate needs a new capture: that waits for the next start.
            if (next.captureRate == s.settings.captureRate) s.update(next)
        }
    }

    private fun nextId(): Long = synchronized(lock) { ++noticeId }

    private fun notice(n: Notice) { notices.value = notices.value + n }

    fun dismiss(n: Notice) { notices.value = notices.value.filterNot { it.id == n.id } }

    fun failureText(context: Context): String = when (failure.value) {
        Wfas3Failure.LIBRARY -> context.getString(R.string.w3_fail_library)
        Wfas3Failure.SELFTEST -> context.getString(R.string.w3_fail_selftest)
        Wfas3Failure.DEVICE_RECORD -> context.getString(R.string.w3_fail_device_record)
        Wfas3Failure.STORAGE -> context.getString(R.string.w3_fail_storage)
        Wfas3Failure.PORT_IN_USE -> context.getString(R.string.w3_fail_port, W3.UDP_PORT)
        Wfas3Failure.STORE_KEY -> context.getString(R.string.w3_fail_store_key)
        Wfas3Failure.IN_USE, Wfas3Failure.NETWORK, null -> context.getString(R.string.w3_fail_network)
    }

    /** The engine, started if needed; null (with [failure] set) if it cannot run here. */
    fun ensure(context: Context, settings: AppSettings? = null): Wfas3Engine? {
        ctx = context.applicationContext
        val app = settings ?: appSettings
        synchronized(lock) {
            engine?.let { return it }
            Wfas3Library.loader = { System.loadLibrary("wfas3") }
            // Debug builds only: the v3 trace in logcat, tag WFAS3 (events,
            // sessions, counters; never keys or codes).
            if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
                com.cuscus.wifiaudiostreaming.wfas3.Wfas3Trace.sink == null) {
                com.cuscus.wifiaudiostreaming.wfas3.Wfas3Trace.sink = { Log.i(TAG, it) }
            }
            val st = store ?: Wfas3KeystoreStore(context.applicationContext).also { store = it }
            val e = Wfas3Engine(st, Wfas3Settings.deviceName(app))
            applyNetwork(e, app)
            e.addListener(this)
            try {
                // The records are loaded now; the port opens only while something needs it.
                e.start(listen = false)
            } catch (x: Wfas3Exception) {
                failure.value = x.failure
                Log.w(TAG, "engine not started: ${x.failure}")
                return null
            } catch (t: Throwable) {
                failure.value = Wfas3Failure.LIBRARY
                Log.w(TAG, "engine not started: ${t.javaClass.simpleName}")
                return null
            }
            failure.value = null
            engine = e
            refreshPairs()
            return e
        }
    }

    private fun applyNetwork(e: Wfas3Engine, app: AppSettings) {
        val ifc = runCatching { NetworkManager.getWifiNetworkInterface(app.networkInterface) }.getOrNull()
            ?: Wfas3Engine.defaultMulticastInterface()
        e.multicastInterface = ifc
        e.broadcastTargets = ifc?.let { Wfas3Engine.broadcastsOf(it) } ?: emptyList()
        ctx?.let { e.notLocal4 = mobileInterfaces(it) }
    }

    /**
     * The interfaces of mobile data networks, with their 464XLAT ones
     * ("v4-" + name). Their IPv4 subnet is the carrier's, which can span
     * many subscribers (some modems report a /8): pairing datagrams from
     * there are not from the local network. A VPN is not one of them, even
     * when it runs over mobile data.
     */
    private fun mobileInterfaces(context: Context): Set<String> = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val names = HashSet<String>()
        @Suppress("DEPRECATION")
        for (n in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            if (!caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) continue
            val name = cm.getLinkProperties(n)?.interfaceName ?: continue
            names += name
            names += "v4-$name"
        }
        names
    } catch (_: Exception) {
        emptySet()
    }

    /** The Wi-Fi changed: probes and the group follow the new interface. */
    fun networkChanged() {
        val e = engine ?: return
        synchronized(lock) { applyNetwork(e, appSettings) }
    }

    private fun wantsNetwork(): Boolean =
        server != null || clientWanted || window.value != null || pinSearching || pairingActive || locating

    /**
     * Open the port when something needs it, close it a moment after the last
     * user is gone. Android drops broadcasts on Wi-Fi unless a multicast lock is
     * held, so the lock follows the port.
     */
    fun updateNetwork() {
        val e = engine ?: return
        val c = ctx
        synchronized(lock) {
            closeTimer?.interrupt()
            closeTimer = null
            if (wantsNetwork()) {
                applyNetwork(e, appSettings)
                if (c != null && !lockHeld) { WfasMulticastLock.acquire(c, LOCK_OWNER); lockHeld = true }
                if (!e.isListening) {
                    try {
                        e.listen(true)
                        // The port is open again: a past network failure no longer applies.
                        if (failure.value == Wfas3Failure.NETWORK || failure.value == Wfas3Failure.PORT_IN_USE) failure.value = null
                    } catch (x: Wfas3Exception) {
                        failure.value = x.failure
                    } catch (t: Exception) {
                        failure.value = Wfas3Failure.NETWORK
                        Log.w(TAG, "port not opened", t)
                    }
                }
            } else if (e.isListening || lockHeld) {
                val t = Thread({
                    try {
                        Thread.sleep(400)
                        synchronized(lock) {
                            if (!wantsNetwork()) {
                                runCatching { engine?.listen(false) }
                                if (lockHeld) { WfasMulticastLock.release(LOCK_OWNER); lockHeld = false }
                            }
                        }
                    } catch (_: InterruptedException) {
                    }
                }, "wfas3-close")
                t.isDaemon = true
                closeTimer = t
                t.start()
            }
        }
    }

    fun shutdown() {
        synchronized(lock) {
            closeTimer?.interrupt()
            closeTimer = null
            stopMicCapture()
            client?.detach()
            client = null
            server?.stop()
            server = null
            engine?.shutdown()
            engine = null
            if (lockHeld) { WfasMulticastLock.release(LOCK_OWNER); lockHeld = false }
            window.value = null
            asks.value = emptyList()
            peers.value = emptyList()
            beacons.value = emptyList()
            pairing.value = ClientPairing.Idle
            clientState.value = Wfas3ClientState.Idle
            clientWanted = false
            pinSearching = false
            pairingActive = false
        }
    }

    fun refreshPairs() { pairs.value = engine?.pairs() ?: emptyList() }

    // -- server ---------------------------------------------------------------

    fun startServer(context: Context, app: AppSettings, multicast: Boolean): Wfas3Server? {
        val e = ensure(context, app) ?: return null
        synchronized(lock) {
            stopClientLocked()
            server?.stop()
            val s = Wfas3Server(e, Wfas3Settings.server(app, multicast))
            s.events = object : Wfas3Server.Events {
                override fun onPeers(peers: List<Wfas3Peer>) { this@Wfas3Android.peers.value = peers }
                override fun onMicrophone(session: Int, pcm: ShortArray, frames: Int, channels: Int, rate: Int, captureUs: Long, playUs: Long) {}
            }
            server = s
            updateNetwork()
            if (!e.isListening) {
                server = null
                return null
            }
            s.start()
            return s
        }
    }

    private var feedResampler: Wfas3Resampler? = null
    private var feedPcm = ShortArray(0)

    /** Captured PCM (16-bit little endian) for the v3 server; converted to its rate if needed. */
    fun feedBytes(data: ByteArray, len: Int, channels: Int, rate: Int) {
        val s = server ?: return
        val frames = len / (2 * channels)
        if (frames <= 0) return
        if (feedPcm.size < frames * channels) feedPcm = ShortArray(frames * channels)
        for (i in 0 until frames * channels) {
            feedPcm[i] = ((data[2 * i].toInt() and 0xFF) or (data[2 * i + 1].toInt() shl 8)).toShort()
        }
        val target = s.settings.captureRate
        val (pcm, n) = if (rate == target) feedPcm to frames else {
            val r = feedResampler?.takeIf { it.inRate == rate && it.inChannels == channels }
                ?: Wfas3Resampler(rate, target, channels, channels).also { feedResampler = it }
            r.process(feedPcm, 0, frames)
        }
        if (n <= 0) return
        val now = Wfas3Engine.clockUs()
        s.feed(pcm, 0, n, channels, now - n * 1_000_000L / target)
    }

    fun pace() { server?.pace(Wfas3Engine.clockUs()) }

    /** From the main thread: stops this server, not one started after the call. */
    fun stopServerAsync() {
        val s = server ?: return
        bg { synchronized(lock) { if (server === s) stopServer() } }
    }

    fun stopServer() {
        synchronized(lock) {
            if (server == null) return
            server?.stop()
            server = null
            peers.value = emptyList()
            if (window.value != null) closeWindow()
            updateNetwork()
        }
    }

    // -- client ---------------------------------------------------------------

    @Volatile private var wantMic = false
    private val saveScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    /** The server reached last is the one the client goes back to by itself. */
    private fun rememberLastPair(slot: Int) {
        val c = ctx ?: return
        saveScope.launch { runCatching { com.cuscus.wifiaudiostreaming.data.SettingsDataStore(c).saveV3LastPair(slot) } }
    }

    /**
     * The client state, told the way the rest of the app tells it: the status
     * line, the "streaming" flag of the home screen, the notification of
     * ClientService and the connection sounds.
     */
    private fun publish(state: Wfas3ClientState, prev: Wfas3ClientState) {
        val c = ctx ?: return
        NetworkManager.connectionStatus.value = wfas3ClientStateText(c, state, pairs.value)
        Wfas3ClientLocks.hold(c, state !is Wfas3ClientState.Idle && state !is Wfas3ClientState.Stopped)
        when (state) {
            is Wfas3ClientState.Connected -> {
                if (!NetworkManager.isStreamingCurrent.value && appSettings.connectionSoundEnabled) NetworkManager.playConnectionSound(c)
                NetworkManager.isStreamingCurrent.value = true
            }
            is Wfas3ClientState.Stopped, is Wfas3ClientState.Idle -> {
                val was = NetworkManager.isStreamingCurrent.value || prev is Wfas3ClientState.Connected
                NetworkManager.isStreamingCurrent.value = false
                if (was && prev is Wfas3ClientState.Connected && appSettings.disconnectionSoundEnabled) NetworkManager.playDisconnectionSound(c)
                runCatching { c.stopService(android.content.Intent(c, ClientService::class.java)) }
            }
            else -> Unit
        }
    }

    fun connect(context: Context, app: AppSettings, pair: Int, at: Wfas3Addr?, sendMic: Boolean): Boolean {
        val e = ensure(context, app) ?: return false
        synchronized(lock) {
            server?.stop(); server = null; peers.value = emptyList()
            val c = client ?: Wfas3Client(e) { rate, ch -> Wfas3TrackOutput.open(rate, ch) }.also {
                it.events = object : Wfas3Client.Events {
                    override fun onState(state: Wfas3ClientState) {
                        val prev = clientState.value
                        clientState.value = state
                        publish(state, prev)
                        if (state is Wfas3ClientState.Connected) {
                            startMicIfWanted()
                            if (appSettings.v3LastPair != state.pair) rememberLastPair(state.pair)
                        } else {
                            stopMicCapture()
                        }
                        if (state is Wfas3ClientState.Idle || state is Wfas3ClientState.Stopped) {
                            clientWanted = false
                            updateNetwork()
                        }
                    }
                    override fun onMicrophoneRefused() { notice(Notice.MicRefused(nextId())) }
                }
                it.attach()
                client = it
            }
            c.latencyMs = app.v3LatencyMs.coerceIn(20, 1000)
            wantMic = sendMic && hasMicPermission(context)
            c.setMicrophone(wantMic)
            clientWanted = true
            clientSlot = pair
            connectGen++
            updateNetwork()
            if (!e.isListening) { clientWanted = false; return false }
            c.connect(pair, at)
            return true
        }
    }

    /**
     * Ask every paired server to answer, and report where each one did within
     * [timeoutMs]. [extra] gets the probes too: an address the user typed.
     * Only paired servers can recognise the probes, so nobody else learns anything.
     */
    fun locate(context: Context, timeoutMs: Long, extra: InetAddress? = null): Map<Int, Wfas3Addr> {
        locateLock.lock()
        try { return locateLocked(context, timeoutMs, extra) } finally { locateLock.unlock() }
    }

    private fun locateLocked(context: Context, timeoutMs: Long, extra: InetAddress?): Map<Int, Wfas3Addr> {
        val e = ensure(context) ?: return emptyMap()
        val found = java.util.concurrent.ConcurrentHashMap<Int, Wfas3Addr>()
        val l = object : Wfas3Listener { override fun onFound(pair: Int, addr: Wfas3Addr) { found[pair] = addr } }
        val slots = e.pairs().filterNot { it.suspect }.map { it.slot }
        if (slots.isEmpty()) return emptyMap()
        val before = e.broadcastTargets
        locating = true
        e.addListener(l)
        try {
            updateNetwork()
            if (extra != null) {
                e.broadcastTargets = before.ifEmpty { listOf(InetAddress.getByName("255.255.255.255")) } + extra
            }
            if (!e.isListening) return emptyMap()
            slots.forEach { e.find(it, true) }
            val end = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < end && found.size < slots.size) Thread.sleep(50)
            // A connection started meanwhile keeps searching for its own server.
            slots.forEach { if (!(clientWanted && it == clientSlot)) e.find(it, false) }
        } finally {
            e.removeListener(l)
            e.broadcastTargets = before
            locating = false
            updateNetwork()
        }
        return HashMap(found)
    }

    @Volatile private var connectGen = 0

    /** From the main thread: ends this session, not one started after the call. */
    fun disconnectAsync() {
        if (!clientWanted && clientState.value is Wfas3ClientState.Idle) return
        val g = connectGen
        bg { if (connectGen == g) disconnect() }
    }

    fun disconnect() {
        client?.disconnect()
        stopMicCapture()
        clientWanted = false
        updateNetwork()
    }

    private fun stopClientLocked() {
        client?.detach()
        client = null
        stopMicCapture()
        clientState.value = Wfas3ClientState.Idle
        clientWanted = false
    }

    fun setMicrophone(context: Context, on: Boolean) {
        wantMic = on && hasMicPermission(context)
        client?.setMicrophone(wantMic)
        if (wantMic) startMicIfWanted() else stopMicCapture()
    }

    private fun hasMicPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @Volatile private var micThread: Thread? = null

    @SuppressLint("MissingPermission")
    private fun startMicIfWanted() {
        if (!wantMic || micThread != null) return
        val c = client ?: return
        if (clientState.value !is Wfas3ClientState.Connected) return
        val context = ctx ?: return
        if (!hasMicPermission(context)) return
        val t = Thread({
            var rec: AudioRecord? = null
            try {
                // 48 kHz mono is what the v3 microphone stream carries; a device
                // that cannot record it is read at 44.1 kHz and converted.
                for (rate in intArrayOf(48000, 44100)) {
                    val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    if (min <= 0) continue
                    val r = runCatching {
                        AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate / 1000 * 40 * 2))
                    }.getOrNull()
                    if (r != null && r.state == AudioRecord.STATE_INITIALIZED) { rec = r; break }
                    r?.release()
                }
                val ar = rec ?: return@Thread
                val rate = ar.sampleRate
                val frames = rate / 100
                val buf = ShortArray(frames)
                val rs = if (rate != 48000) Wfas3Resampler(rate, 48000, 1, 1) else null
                ar.startRecording()
                while (!Thread.currentThread().isInterrupted) {
                    val n = ar.read(buf, 0, frames)
                    if (n <= 0) continue
                    val (out, outFrames) = rs?.process(buf, 0, n) ?: (buf to n)
                    val now = Wfas3Engine.clockUs()
                    c.feedMic(out, 0, outFrames, 1, now - outFrames * 1_000_000L / 48000)
                }
            } catch (_: InterruptedException) {
            } catch (t: Throwable) {
                Log.w(TAG, "microphone capture stopped: ${t.javaClass.simpleName}")
            } finally {
                runCatching { rec?.stop() }
                runCatching { rec?.release() }
            }
        }, "wfas3-mic")
        t.isDaemon = true
        micThread = t
        t.start()
    }

    private fun stopMicCapture() {
        val t = micThread ?: return
        micThread = null
        t.interrupt()
    }

    // -- pairing --------------------------------------------------------------

    /** A server opens a window for one device. An Android server takes no client microphone. */
    fun openWindow(context: Context, kind: Int, ask: Boolean): Boolean {
        val e = ensure(context) ?: return false
        windowFailures.value = 0
        asks.value = emptyList()
        // The window needs the port for its beacons: hold it open first.
        window.value = WindowState(kind, "", null, System.currentTimeMillis(), ask)
        val code: Wfas3WindowCode? = try {
            updateNetwork()
            if (e.isListening) e.openWindow(kind, ask, W3.PERM_RECEIVE) else null
        } catch (t: Exception) {
            Log.w(TAG, "window not opened", t)
            null
        }
        // Never a window left waiting for a code that will not come.
        if (code == null) { window.value = null; runCatching { updateNetwork() }; return false }
        window.value = WindowState(kind, code.text, code.qr, System.currentTimeMillis(), ask)
        return true
    }

    fun closeWindow() {
        engine?.closeWindow()
        window.value = null
        asks.value = emptyList()
        updateNetwork()
    }

    /** The user closed the window dialog after a pairing: release the port if unused. */
    fun windowDismissed() {
        window.value = null
        asks.value = emptyList()
        updateNetwork()
    }

    fun answer(a: AskState, yes: Boolean, guest: Boolean) {
        engine?.answer(a.ask, yes, W3.PERM_RECEIVE, guest || a.wantsGuest)
        asks.update { list -> list.filterNot { it.ask == a.ask } }
    }

    fun codeValid(text: String): Boolean = runCatching {
        Wfas3Library.loader = { System.loadLibrary("wfas3") }
        Wfas3Engine.codeValid(text)
    }.getOrDefault(false)

    // A pairing from this phone is made to listen: the server gets no
    // permission to receive this phone's audio (see Wfas3Engine.pairWithCode).
    fun pairWithCode(context: Context, code: String, guest: Boolean, mic: Boolean, replace: Int = -1): Boolean {
        val e = ensure(context) ?: return false
        pairing.value = ClientPairing.Waiting(pin = false)
        pairingActive = true
        if (!portReady(e)) return false
        val st = e.pairWithCode(code.trim(), guest, mic, replace)
        if (st != W3.OK) {
            pairingActive = false
            updateNetwork()
            pairing.value = ClientPairing.Failed(if (st == W3.E_ARG) W3.PAIRF_WRONG else W3.PAIRF_PROTOCOL, false)
            return false
        }
        return true
    }

    /**
     * Open the port for a client pairing. If it cannot open nothing would be
     * sent: the attempt ends at once and says why, instead of waiting.
     */
    private fun portReady(e: Wfas3Engine): Boolean {
        runCatching { updateNetwork() }
        if (e.isListening) return true
        pairingActive = false
        runCatching { updateNetwork() }
        pairing.value = ClientPairing.Failed(W3.PAIRF_PROTOCOL, race = false, noNetwork = true)
        return false
    }

    fun startPinSearch(context: Context): Boolean {
        val e = ensure(context) ?: return false
        beacons.value = emptyList()
        pinSearching = true
        updateNetwork()
        return e.beacons(true) == W3.OK
    }

    fun stopPinSearch() {
        engine?.beacons(false)
        beacons.value = emptyList()
        pinSearching = false
        updateNetwork()
    }

    fun pairWithPin(b: Wfas3Beacon, pin: String, guest: Boolean, mic: Boolean, replace: Int = -1): Boolean {
        val e = engine ?: return false
        pairing.value = ClientPairing.Waiting(pin = true)
        pairingActive = true
        if (!portReady(e)) return false
        val st = e.pairWithPin(b, pin, guest, mic, replace)
        if (st != W3.OK) {
            pairingActive = false
            updateNetwork()
            pairing.value = ClientPairing.Failed(W3.PAIRF_WRONG, false)
            return false
        }
        return true
    }

    fun confirmCode(same: Boolean) { engine?.confirm(same) }

    fun cancelPairing() {
        engine?.cancelPairing()
        pairingActive = false
        stopPinSearch()
        pairing.value = ClientPairing.Idle
    }

    fun resetPairing() { pairing.value = ClientPairing.Idle }

    fun removePair(slot: Int) {
        engine?.removePair(slot)
        refreshPairs()
    }

    /** Erase every v3 key and pairing on this device, the Keystore key included. */
    fun eraseAll(context: Context): Boolean {
        synchronized(lock) {
            shutdown()
            val st = store ?: Wfas3KeystoreStore(context.applicationContext)
            val ok = runCatching { st.eraseAll() }.isSuccess
            store = null
            failure.value = null
            pairs.value = emptyList()
            notices.value = emptyList()
            return ok
        }
    }

    // -- events ---------------------------------------------------------------

    private fun nameOf(pair: Int): String = pairs.value.firstOrNull { it.slot == pair }?.name.orEmpty()

    override fun onPairAsk(ask: Int, name: String, flags: Int, code: String, from: Wfas3Addr) {
        val w = window.value
        val a = AskState(ask, name, flags, code, w?.kind == W3.PAIR_PIN, from.hostText())
        asks.update { list -> list.filterNot { it.ask == ask } + a }
    }

    override fun onPaired(pair: Int, name: String, flags: Int) {
        refreshPairs()
        val w = window.value
        if (w != null) {
            window.value = w.copy(paired = name)
            asks.value = emptyList()
            // One pairing per window (specification 13.2).
            engine?.closeWindow()
        } else {
            pairing.value = ClientPairing.Done(name, (flags and W3.PAIR_GUEST) != 0, pair)
            engine?.beacons(false)
            beacons.value = emptyList()
            pinSearching = false
            pairingActive = false
            updateNetwork()
        }
    }

    /** Both modes reach this step; only in PIN mode do the users compare the codes. */
    override fun onPairCode(code: String) {
        val p = pairing.value
        if (p is ClientPairing.Waiting && p.pin) pairing.value = ClientPairing.Compare(code)
    }

    override fun onPairFailed(reason: Int, race: Boolean) {
        if (window.value != null) {
            windowFailures.value = engine?.windowFailed()?.coerceAtLeast(0) ?: windowFailures.value
            return
        }
        pairingActive = false
        updateNetwork()
        if (reason == W3.PAIRF_CANCELLED && !race) { pairing.value = ClientPairing.Idle; return }
        pairing.value = ClientPairing.Failed(reason, race)
    }

    override fun onWindowClosed() {
        val w = window.value ?: return
        if (w.paired == null) window.value = null
        asks.value = emptyList()
        if (window.value == null) updateNetwork()
    }

    /** When each listed beacon was last heard: a window sends one every second. */
    private val beaconHeard = HashMap<String, Long>()
    private const val BEACON_FRESH_MS = 4_000L

    override fun onBeacon(beacon: Wfas3Beacon) {
        // At most 8 names, as the specification asks. A server stays listed
        // while it is heard; one not heard for 4 s leaves, so that the list
        // follows the windows that are open and old or forged entries do not
        // keep a real server out for good.
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(beaconHeard) {
            val cur = fresh(now)
            val known = cur.indexOfFirst { it.key == beacon.key }
            when {
                known >= 0 -> { beaconHeard[beacon.key] = now; beacons.value = cur.toMutableList().also { it[known] = beacon } }
                cur.size < 8 -> { beaconHeard[beacon.key] = now; beacons.value = cur + beacon }
                else -> beacons.value = cur
            }
        }
    }

    /** Drop the beacons not heard lately; the pairing dialog calls it every second. */
    fun pruneBeacons() {
        synchronized(beaconHeard) {
            val cur = fresh(android.os.SystemClock.elapsedRealtime())
            if (cur.size != beacons.value.size) beacons.value = cur
        }
    }

    private fun fresh(now: Long): List<Wfas3Beacon> {
        val cur = beacons.value.filter { now - (beaconHeard[it.key] ?: Long.MIN_VALUE / 2) <= BEACON_FRESH_MS }
        beaconHeard.keys.retainAll(cur.map { it.key }.toSet())
        return cur
    }

    override fun onSuspect(pair: Int) {
        refreshPairs()
        notice(Notice.Suspect(nextId(), nameOf(pair)))
    }

    override fun onForgetAsked(pair: Int) {
        notice(Notice.ForgetAsked(nextId(), pair, nameOf(pair)))
    }

    override fun onPairsChanged() = refreshPairs()

    override fun onFatal(failure: Wfas3Failure) {
        this.failure.value = failure
        if (failure == Wfas3Failure.STORAGE) notice(Notice.Storage(nextId()))
    }
}

// -- texts, shared by the screens and the notification ---------------------------

fun wfas3PairFailText(context: Context, reason: Int, race: Boolean): String {
    val base = context.getString(
        when (reason) {
            W3.PAIRF_TIMEOUT -> R.string.w3_pf_timeout
            W3.PAIRF_WRONG -> R.string.w3_pf_wrong
            W3.PAIRF_REFUSED -> R.string.w3_pf_refused
            W3.PAIRF_KEY -> R.string.w3_pf_key
            W3.PAIRF_USED -> R.string.w3_pf_used
            W3.PAIRF_FULL -> R.string.w3_pf_full
            W3.PAIRF_CANCELLED -> R.string.w3_pf_cancelled
            W3.PAIRF_CODE -> R.string.w3_pf_code
            else -> R.string.w3_pf_protocol
        }
    )
    return if (race) base + "\n\n" + context.getString(R.string.w3_pf_race) else base
}

fun wfas3FormatText(context: Context, c: Wfas3Config): String {
    val parts = ArrayList<String>()
    parts += if (c.opus) context.getString(R.string.w3_fmt_opus, (c.payloadBytes * 8L * 48000L / c.frameSamples / 1000L).toInt())
    else context.getString(R.string.w3_fmt_pcm, c.sampleRate)
    if (c.sync) parts += context.getString(R.string.w3_fmt_sync)
    if (c.multicast) parts += context.getString(R.string.w3_fmt_multicast)
    return parts.joinToString(", ")
}

fun wfas3ClientStateText(context: Context, state: Wfas3ClientState, pairs: List<Wfas3PairInfo>): String {
    fun nm(p: Int) = pairs.firstOrNull { it.slot == p }?.name?.ifBlank { null } ?: "?"
    fun reason(r: Wfas3ClientState.Reason, name: String) = context.getString(
        when (r) {
            Wfas3ClientState.Reason.NOT_FOUND -> R.string.w3_state_not_found
            Wfas3ClientState.Reason.BUSY -> R.string.w3_state_busy
            Wfas3ClientState.Reason.NOT_PERMITTED -> R.string.w3_state_not_permitted
            Wfas3ClientState.Reason.SUSPECT -> R.string.w3_state_suspect
            Wfas3ClientState.Reason.REMOVED -> R.string.w3_state_removed
            Wfas3ClientState.Reason.GUEST_ENDED -> R.string.w3_state_guest_ended
            Wfas3ClientState.Reason.LOST -> R.string.w3_state_lost
            Wfas3ClientState.Reason.UNSUPPORTED -> R.string.w3_state_unsupported
        }, name
    )
    return when (state) {
        is Wfas3ClientState.Idle -> context.getString(R.string.status_idle)
        is Wfas3ClientState.Searching -> context.getString(R.string.w3_state_searching, nm(state.pair))
        is Wfas3ClientState.Connecting -> context.getString(R.string.w3_state_connecting, nm(state.pair))
        is Wfas3ClientState.Connected -> context.getString(R.string.w3_state_connected, nm(state.pair), wfas3FormatText(context, state.config))
        is Wfas3ClientState.Waiting -> reason(state.reason, nm(state.pair))
        is Wfas3ClientState.Stopped -> reason(state.reason, nm(state.pair))
    }
}
