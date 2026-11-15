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

package com.cuscus.wifiaudiostreaming.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.cuscus.wifiaudiostreaming.UsbLink
import com.cuscus.wifiaudiostreaming.WfasPolicy
import com.cuscus.wifiaudiostreaming.scripting.AutomationGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A server in the auto-connect list.
 *
 * Beyond the address and the Wi-Fi SSID it was always matched on, an entry now
 * carries what an unattended connection to a KEY-mode server needs: an optional
 * port override, a label, an enabled flag, and a reference to the key. The key
 * itself is never stored here and never reaches the settings DataStore — only the
 * lookup name (`keyRef`) is kept, and the key lives in [SecretStore], encrypted
 * with a Keystore key that does not leave the device. Same model as the desktop
 * `AutoConnectTarget`.
 *
 * Serialisation stays backward compatible: an old `ip|ssid` entry parses with
 * every new field at its default. The delimiters `%`, `|` and `,` are escaped in
 * the free-text fields so a label or SSID can contain them.
 */
data class AutoConnectEntry(
    val ip: String,
    val ssid: String = "",
    val port: Int? = null,
    val label: String = "",
    val enabled: Boolean = true,
    val keyRef: String = ""
) {
    val hasKey: Boolean get() = keyRef.isNotBlank()

    fun displayName(): String = label.ifBlank { ip }

    override fun toString(): String = buildString {
        append(esc(ip))
        append('|').append(esc(ssid))
        port?.let { append("|port=").append(it) }
        if (label.isNotBlank()) append("|label=").append(esc(label))
        if (keyRef.isNotBlank()) append("|key=").append(keyRef)
        if (!enabled) append("|off")
    }

    companion object {
        fun newKeyRef(): String =
            java.lang.Long.toHexString(System.currentTimeMillis()) + "-" +
                java.lang.Integer.toHexString((0..0xFFFF).random())

        fun fromString(str: String): AutoConnectEntry {
            val parts = str.split("|")
            val ip = unesc(parts.getOrNull(0)?.trim().orEmpty())
            val ssid = unesc(parts.getOrNull(1)?.trim().orEmpty())
            var port: Int? = null
            var label = ""
            var keyRef = ""
            var enabled = true
            for (opt in parts.drop(2)) {
                val o = opt.trim()
                when {
                    o.equals("off", true)        -> enabled = false
                    o.equals("on", true)         -> enabled = true
                    o.startsWith("port=", true)  -> port = o.substringAfter('=').toIntOrNull()?.takeIf { it in 1..65535 }
                    o.startsWith("label=", true) -> label = unesc(o.substringAfter('='))
                    o.startsWith("key=", true)   -> keyRef = o.substringAfter('=').trim()
                }
            }
            return AutoConnectEntry(ip, ssid, port, label, enabled, keyRef)
        }

        fun parseList(str: String): List<AutoConnectEntry> =
            str.split(",").filter { it.isNotBlank() }.map { fromString(it) }

        fun serializeList(list: List<AutoConnectEntry>): String =
            list.joinToString(",") { it.toString() }

        private fun esc(s: String): String =
            s.replace("%", "%25").replace("|", "%7C").replace(",", "%2C").replace("\n", " ")

        private fun unesc(s: String): String =
            s.replace("%7C", "|").replace("%7c", "|")
                .replace("%2C", ",").replace("%2c", ",")
                .replace("%25", "%")
    }
}

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val streamInternal: Boolean,
    val streamMic: Boolean,
    val sampleRate: Int,
    val channelConfig: String,
    val bufferSize: Int,
    val streamingPort: Int,
    val sendClientMicrophone: Boolean,
    val micPort: Int,
    val onboardingCompleted: Boolean,
    val networkInterface: String,
    val rtpEnabled: Boolean,
    val rtpPort: Int,
    val httpEnabled: Boolean,
    val httpPort: Int,
    val httpSafariMode: Boolean,
    val dlnaEnabled: Boolean = false,
    val dlnaPort: Int = 8081,
    val dlnaFormat: String = "auto",
    val dlnaDevices: List<String> = emptyList(),
    val snapcastEnabled: Boolean = false,
    val snapcastPort: Int = com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.STREAM_PORT,
    val snapcastControlPort: Int = com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.CONTROL_PORT,
    val snapcastCodec: String = com.cuscus.wifiaudiostreaming.snapcast.SnapcastCodecs.PCM,
    val snapcastChunkMs: Int = com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.CHUNK_MS,
    val snapcastBufferMs: Int = com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.BUFFER_MS,
    val snapcastStreamName: String = com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.STREAM_NAME,
    /**
     * Server Snapcast salvati nella sezione Ricevi, uno per riga
     * (vedi SnapcastServerRef.serialize). Sono configurazioni, non segreti.
     */
    val snapcastServers: List<String> = emptyList(),
    /**
     * Sorgenti RTP salvate nella sezione Ricevi, una per riga
     * (vedi RtpSource.serialize). Sono configurazioni, non segreti.
     */
    val rtpSources: List<String> = emptyList(),
    val lastMulticastMode: Boolean = false,
    // Muto il volume media mentre catturo l'audio interno, cosi' non si sente due
    // volte; spento, si sente sia qui che sul client.
    val muteRender: Boolean = true,
    // In unicast: quando il client si stacca finisce la sessione, non il server.
    val serverPersist: Boolean = false,
    val clientTileIp: String = "",
    val autoConnectEnabled: Boolean = false,
    val autoConnectList: String = "",
    val connectionSoundEnabled: Boolean = true,
    val disconnectionSoundEnabled: Boolean = true,
    val lastSeenChangelogVersion: String = "",
    val autoUpdateCheckEnabled: Boolean = true,
    val latencyMs: Int = 120,
    val maxPayloadBytes: Int = 1390,
    val securityMode: String = "OFF",
    val authKey: String = "",
    // On unless the user turned it off: with a key and no encryption the
    // handshake is authenticated but the audio itself travels in cleartext.
    val encryptionEnabled: Boolean = true,
    val qrPairingEnabled: Boolean = false,
    val manualAuthKey: String = "",
    val hapticsEnabled: Boolean = true,
    val blackoutOutlinedUi: Boolean = false,
    val developerMode: Boolean = false,
    val noiseReductionEnabled: Boolean = false,
    val noiseReductionStrength: Int = 50,
    val usbModeEnabled: Boolean = false,
    val usbLatencyMs: Int = 20,
    val wfasMode: String = WfasPolicy.MODE_OFF_ON_USB,
    val backgroundSpectrumEnabled: Boolean = false,
    val backgroundSpectrumStyle: String = "BARS",
    val backgroundSpectrumBlackoutOnly: Boolean = false,
    val backgroundSpectrumGroove: Int = 0,
    // Spento di default: chi non usa Tasker o i tag NFC non ha nessun ingresso
    // esterno aperto, e chi li usa lo accende sapendo cosa sta accendendo.
    val automationEnabled: Boolean = false
)

class SettingsDataStore(context: Context) {
    private val dataStore = context.settingsDataStore
    private val appContext = context.applicationContext

    private object PreferencesKeys {
        val STREAM_INTERNAL = booleanPreferencesKey("stream_internal")
        val STREAM_MIC = booleanPreferencesKey("stream_mic")
        val SAMPLE_RATE = intPreferencesKey("sample_rate")
        val CHANNEL_CONFIG = stringPreferencesKey("channel_config")
        val BUFFER_SIZE = intPreferencesKey("buffer_size")
        val LATENCY_MS = intPreferencesKey("latency_ms")
        val MAX_PAYLOAD = intPreferencesKey("max_payload")
        val SECURITY_MODE = stringPreferencesKey("security_mode")
        val ENCRYPTION_ENABLED = booleanPreferencesKey("encryption_enabled")
        val QR_PAIRING_ENABLED = booleanPreferencesKey("qr_pairing_enabled")
        val STREAMING_PORT = intPreferencesKey("streaming_port")
        val SEND_CLIENT_MICROPHONE = booleanPreferencesKey("send_client_microphone")
        val MIC_PORT = intPreferencesKey("mic_port")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LAST_MULTICAST_MODE = booleanPreferencesKey("last_multicast_mode")
        val MUTE_RENDER = booleanPreferencesKey("mute_render")
        val SERVER_PERSIST = booleanPreferencesKey("server_persist")
        val NETWORK_INTERFACE = stringPreferencesKey("network_interface")
        val RTP_ENABLED = booleanPreferencesKey("rtp_enabled")
        val RTP_PORT = intPreferencesKey("rtp_port")
        val HTTP_ENABLED = booleanPreferencesKey("http_enabled")
        val HTTP_PORT = intPreferencesKey("http_port")
        val HTTP_SAFARI_MODE = booleanPreferencesKey("http_safari_mode")
        val DLNA_ENABLED = booleanPreferencesKey("dlna_enabled")
        val DLNA_PORT = intPreferencesKey("dlna_port")
        val DLNA_FORMAT = stringPreferencesKey("dlna_format")
        val DLNA_DEVICES = stringPreferencesKey("dlna_devices")
        val SNAPCAST_ENABLED = booleanPreferencesKey("snapcast_enabled")
        val SNAPCAST_PORT = intPreferencesKey("snapcast_port")
        val SNAPCAST_CONTROL_PORT = intPreferencesKey("snapcast_control_port")
        val SNAPCAST_CODEC = stringPreferencesKey("snapcast_codec")
        val SNAPCAST_CHUNK_MS = intPreferencesKey("snapcast_chunk_ms")
        val SNAPCAST_BUFFER_MS = intPreferencesKey("snapcast_buffer_ms")
        val SNAPCAST_STREAM_NAME = stringPreferencesKey("snapcast_stream_name")
        val SNAPCAST_SERVERS = stringPreferencesKey("snapcast_servers")
        val RTP_SOURCES = stringPreferencesKey("rtp_sources")
        val CLIENT_TILE_IP = stringPreferencesKey("client_tile_ip")
        val AUTO_CONNECT_ENABLED = booleanPreferencesKey("auto_connect_enabled")
        val AUTO_CONNECT_LIST = stringPreferencesKey("auto_connect_list")
        val CONNECTION_SOUND_ENABLED = booleanPreferencesKey("connection_sound_enabled")
        val DISCONNECTION_SOUND_ENABLED = booleanPreferencesKey("disconnection_sound_enabled")
        val AUTOMATION_SCRIPTS = stringPreferencesKey("automation_scripts")
        val LAST_SEEN_CHANGELOG_VERSION = stringPreferencesKey("last_seen_changelog_version")
        val AUTO_UPDATE_CHECK_ENABLED = booleanPreferencesKey("auto_update_check_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val BLACKOUT_OUTLINED_UI = booleanPreferencesKey("blackout_outlined_ui")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        val NOISE_REDUCTION_ENABLED = booleanPreferencesKey("noise_reduction_enabled")
        val NOISE_REDUCTION_STRENGTH = intPreferencesKey("noise_reduction_strength")
        val USB_MODE_ENABLED = booleanPreferencesKey("usb_mode_enabled")
        val USB_LATENCY_MS = intPreferencesKey("usb_latency_ms")
        val WFAS_MODE = stringPreferencesKey("wfas_mode")
        val BACKGROUND_SPECTRUM_ENABLED = booleanPreferencesKey("background_spectrum_enabled")
        val BACKGROUND_SPECTRUM_STYLE = stringPreferencesKey("background_spectrum_style")
        val BACKGROUND_SPECTRUM_BLACKOUT_ONLY = booleanPreferencesKey("background_spectrum_blackout_only")
        val BACKGROUND_SPECTRUM_GROOVE = intPreferencesKey("background_spectrum_groove")
        val AUTOMATION_ENABLED = booleanPreferencesKey("automation_enabled")
        val LEGACY_AUTOMATION_TOKEN = stringPreferencesKey("automation_token")
        // Read once, to be moved into SecretStore and deleted. Nothing writes
        // them any more: see [authKeysFlow].
        val LEGACY_AUTH_KEY = stringPreferencesKey("auth_key")
        val LEGACY_MANUAL_AUTH_KEY = stringPreferencesKey("manual_auth_key")
    }

    /**
     * The pre-shared key does not live here.
     *
     * It sits in [SecretStore], encrypted with a Keystore key that does not leave
     * the device, for the same reason the automation token does: this DataStore is
     * a plain file, and a key in cleartext inside it is a key that any copy of the
     * data partition — or of a backup — hands over intact.
     *
     * It is still surfaced through [AppSettings] so that every reader keeps seeing
     * one settings object, which is why the two sources are combined here rather
     * than the call sites being taught about a second store.
     *
     * Opening the encrypted file goes through the Keystore, so the whole producer
     * runs on IO: `settingsFlow` is collected from the main thread in several
     * places. A Keystore that refuses to open must not take the settings down with
     * it either — it fails closed, with no key, which a KEY-mode server reads as
     * "refuse everyone" rather than "let anyone in".
     */
    private fun authKeysFlow(): Flow<SecretStore.AuthKeys> = flow {
        val store = runCatching { secrets() }.getOrNull()
        if (store == null) emit(SecretStore.AuthKeys()) else emitAll(store.authKeys)
    }.flowOn(Dispatchers.IO)

    /**
     * The encrypted store, with the one-way move of whatever a build before this
     * one left in cleartext done on the way through.
     *
     * The plaintext is dropped even when nothing was adopted from it: in that case
     * the encrypted store already held a value, which is the newer of the two —
     * every save since the upgrade has gone there — and what is left in the
     * DataStore is a stale copy of a key, which is exactly what this is trying to
     * stop existing.
     */
    private suspend fun secrets(): SecretStore {
        val store = SecretStore.get(appContext)
        migrationLock.withLock {
            if (!legacyKeysMigrated) {
                val stored = dataStore.data.first()
                val legacyAuth = stored[PreferencesKeys.LEGACY_AUTH_KEY]
                val legacyManual = stored[PreferencesKeys.LEGACY_MANUAL_AUTH_KEY]
                if (legacyAuth != null || legacyManual != null) {
                    store.adoptLegacyAuthKeys(legacyAuth, legacyManual)
                    dataStore.edit { preferences ->
                        preferences.remove(PreferencesKeys.LEGACY_AUTH_KEY)
                        preferences.remove(PreferencesKeys.LEGACY_MANUAL_AUTH_KEY)
                    }
                }
                legacyKeysMigrated = true
            }
        }
        return store
    }

    private companion object {
        // Process-wide: a SettingsDataStore is built on the spot wherever the
        // settings are needed, so per-instance state would re-run the migration
        // on every one of them.
        val migrationLock = Mutex()
        @Volatile var legacyKeysMigrated = false
    }

    val scriptsFlow: Flow<List<AppScript>> = dataStore.data.map { preferences ->
        AppScript.parseList(preferences[PreferencesKeys.AUTOMATION_SCRIPTS] ?: "")
    }

    suspend fun saveScripts(list: List<AppScript>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTOMATION_SCRIPTS] = AppScript.serializeList(list)
        }
    }

    val settingsFlow: Flow<AppSettings> = combine(
        dataStore.data,
        authKeysFlow()
    ) { preferences, authKeys ->
        AppSettings(
            streamInternal = preferences[PreferencesKeys.STREAM_INTERNAL] ?: true,
            streamMic = preferences[PreferencesKeys.STREAM_MIC] ?: false,
            sampleRate = preferences[PreferencesKeys.SAMPLE_RATE] ?: 48000,
            channelConfig = preferences[PreferencesKeys.CHANNEL_CONFIG] ?: "STEREO",
            bufferSize = preferences[PreferencesKeys.BUFFER_SIZE] ?: 512,
            streamingPort = preferences[PreferencesKeys.STREAMING_PORT] ?: 9090,
            sendClientMicrophone = preferences[PreferencesKeys.SEND_CLIENT_MICROPHONE] ?: false,
            micPort = preferences[PreferencesKeys.MIC_PORT] ?: 9092,
            onboardingCompleted = preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false,
            lastMulticastMode = preferences[PreferencesKeys.LAST_MULTICAST_MODE] ?: false,
            muteRender = preferences[PreferencesKeys.MUTE_RENDER] ?: true,
            serverPersist = preferences[PreferencesKeys.SERVER_PERSIST] ?: false,
            networkInterface = preferences[PreferencesKeys.NETWORK_INTERFACE] ?: "Auto",
            rtpEnabled = preferences[PreferencesKeys.RTP_ENABLED] ?: false,
            rtpPort = preferences[PreferencesKeys.RTP_PORT] ?: 9094,
            httpEnabled = preferences[PreferencesKeys.HTTP_ENABLED] ?: false,
            httpPort = preferences[PreferencesKeys.HTTP_PORT] ?: 8080,
            dlnaEnabled = preferences[PreferencesKeys.DLNA_ENABLED] ?: false,
            dlnaPort = preferences[PreferencesKeys.DLNA_PORT] ?: 8081,
            dlnaFormat = preferences[PreferencesKeys.DLNA_FORMAT] ?: "auto",
            dlnaDevices = (preferences[PreferencesKeys.DLNA_DEVICES] ?: "")
                .split('\n').map { it.trim() }.filter { it.isNotEmpty() },
            httpSafariMode = preferences[PreferencesKeys.HTTP_SAFARI_MODE] ?: false,
            snapcastServers = (preferences[PreferencesKeys.SNAPCAST_SERVERS] ?: "")
                .split('\n').map { it.trim() }.filter { it.isNotEmpty() },
            rtpSources = (preferences[PreferencesKeys.RTP_SOURCES] ?: "")
                .split('\n').map { it.trim() }.filter { it.isNotEmpty() },
            snapcastEnabled = preferences[PreferencesKeys.SNAPCAST_ENABLED] ?: false,
            snapcastPort = preferences[PreferencesKeys.SNAPCAST_PORT]
                ?: com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.STREAM_PORT,
            snapcastControlPort = preferences[PreferencesKeys.SNAPCAST_CONTROL_PORT]
                ?: com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.CONTROL_PORT,
            snapcastCodec = com.cuscus.wifiaudiostreaming.snapcast.SnapcastCodecs.normalize(
                preferences[PreferencesKeys.SNAPCAST_CODEC]
            ),
            snapcastChunkMs = preferences[PreferencesKeys.SNAPCAST_CHUNK_MS]
                ?: com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.CHUNK_MS,
            snapcastBufferMs = preferences[PreferencesKeys.SNAPCAST_BUFFER_MS]
                ?: com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.BUFFER_MS,
            snapcastStreamName = preferences[PreferencesKeys.SNAPCAST_STREAM_NAME]
                ?: com.cuscus.wifiaudiostreaming.snapcast.SnapcastDefaults.STREAM_NAME,
            clientTileIp = preferences[PreferencesKeys.CLIENT_TILE_IP] ?: "",
            autoConnectEnabled = preferences[PreferencesKeys.AUTO_CONNECT_ENABLED] ?: false,
            autoConnectList = preferences[PreferencesKeys.AUTO_CONNECT_LIST] ?: "",
            connectionSoundEnabled = preferences[PreferencesKeys.CONNECTION_SOUND_ENABLED] ?: true,
            disconnectionSoundEnabled = preferences[PreferencesKeys.DISCONNECTION_SOUND_ENABLED] ?: true,
            hapticsEnabled = preferences[PreferencesKeys.HAPTICS_ENABLED] ?: true,
            blackoutOutlinedUi = preferences[PreferencesKeys.BLACKOUT_OUTLINED_UI] ?: false,
            developerMode = preferences[PreferencesKeys.DEVELOPER_MODE] ?: false,
            noiseReductionEnabled = preferences[PreferencesKeys.NOISE_REDUCTION_ENABLED] ?: false,
            noiseReductionStrength = preferences[PreferencesKeys.NOISE_REDUCTION_STRENGTH] ?: 50,
            lastSeenChangelogVersion = preferences[PreferencesKeys.LAST_SEEN_CHANGELOG_VERSION] ?: "",
            autoUpdateCheckEnabled = preferences[PreferencesKeys.AUTO_UPDATE_CHECK_ENABLED] ?: true,
            latencyMs = preferences[PreferencesKeys.LATENCY_MS] ?: 120,
            maxPayloadBytes = preferences[PreferencesKeys.MAX_PAYLOAD] ?: 1390,
            securityMode = preferences[PreferencesKeys.SECURITY_MODE] ?: "OFF",
            authKey = authKeys.authKey,
            encryptionEnabled = preferences[PreferencesKeys.ENCRYPTION_ENABLED] ?: true,
            qrPairingEnabled = preferences[PreferencesKeys.QR_PAIRING_ENABLED] ?: false,
            manualAuthKey = authKeys.manualAuthKey,
            usbModeEnabled = preferences[PreferencesKeys.USB_MODE_ENABLED] ?: false,
            usbLatencyMs = preferences[PreferencesKeys.USB_LATENCY_MS] ?: UsbLink.DEFAULT_USB_LATENCY_MS,
            wfasMode = preferences[PreferencesKeys.WFAS_MODE] ?: WfasPolicy.MODE_OFF_ON_USB,
            backgroundSpectrumEnabled = preferences[PreferencesKeys.BACKGROUND_SPECTRUM_ENABLED] ?: false,
            backgroundSpectrumStyle = preferences[PreferencesKeys.BACKGROUND_SPECTRUM_STYLE] ?: "BARS",
            backgroundSpectrumBlackoutOnly = preferences[PreferencesKeys.BACKGROUND_SPECTRUM_BLACKOUT_ONLY] ?: false,
            backgroundSpectrumGroove = preferences[PreferencesKeys.BACKGROUND_SPECTRUM_GROOVE] ?: 0,
            automationEnabled = preferences[PreferencesKeys.AUTOMATION_ENABLED] ?: false
        )
    }

    suspend fun setAutomationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTOMATION_ENABLED] = enabled
        }
    }

    // Il token e' passato a SecretStore, che lo cifra col Keystore. Se una build
    // precedente ne aveva lasciato una copia in chiaro qui dentro va rimossa,
    // altrimenti resterebbe leggibile nel file del DataStore e nei backup.
    suspend fun purgeLegacyPlaintextToken() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.LEGACY_AUTOMATION_TOKEN)
        }
    }

    suspend fun saveBackgroundSpectrumSettings(enabled: Boolean, style: String, blackoutOnly: Boolean = false, groove: Int = 0) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKGROUND_SPECTRUM_ENABLED] = enabled
            preferences[PreferencesKeys.BACKGROUND_SPECTRUM_STYLE] = style
            preferences[PreferencesKeys.BACKGROUND_SPECTRUM_BLACKOUT_ONLY] = blackoutOnly
            preferences[PreferencesKeys.BACKGROUND_SPECTRUM_GROOVE] = groove
        }
    }

    suspend fun setLastSeenChangelogVersion(version: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SEEN_CHANGELOG_VERSION] = version
        }
    }

    suspend fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_UPDATE_CHECK_ENABLED] = enabled
        }
    }

    suspend fun saveAudioSourceSettings(streamInternal: Boolean, streamMic: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.STREAM_INTERNAL] = streamInternal
            preferences[PreferencesKeys.STREAM_MIC] = streamMic
        }
    }

    suspend fun saveMuteRender(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MUTE_RENDER] = enabled
        }
    }

    suspend fun saveServerPersist(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SERVER_PERSIST] = enabled
        }
    }

    suspend fun saveClientTileIp(ip: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CLIENT_TILE_IP] = ip
        }
    }

    suspend fun saveAudioQualitySettings(sampleRate: Int, channelConfig: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SAMPLE_RATE] = sampleRate
            preferences[PreferencesKeys.CHANNEL_CONFIG] = channelConfig
        }
    }

    suspend fun saveBufferSize(bufferSize: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BUFFER_SIZE] = bufferSize
        }
    }

    suspend fun saveWfasMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.WFAS_MODE] =
                if (mode in WfasPolicy.MODES) mode else WfasPolicy.MODE_OFF_ON_USB
        }
    }

    suspend fun saveUsbMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USB_MODE_ENABLED] = enabled
        }
    }

    suspend fun saveUsbLatency(latencyMs: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USB_LATENCY_MS] = latencyMs.coerceIn(
                UsbLink.MIN_USB_LATENCY_MS, UsbLink.MAX_USB_LATENCY_MS
            )
        }
    }

    suspend fun saveAdvancedAudio(latencyMs: Int, maxPayloadBytes: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LATENCY_MS] = latencyMs
            preferences[PreferencesKeys.MAX_PAYLOAD] = maxPayloadBytes
        }
    }

    // Il modo resta qui, la chiave va nella custodia cifrata. Se il Keystore non
    // si apre la chiave non viene salvata: il modo si', e in modo KEY senza
    // chiave il server rifiuta tutti - si perde una connessione, non si apre una
    // porta. Come per il token, un fallimento del Keystore non fa cadere l'app.
    suspend fun saveSecurity(mode: String, key: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SECURITY_MODE] = mode
        }
        withContext(Dispatchers.IO) {
            runCatching { secrets().saveAuthKey(key) }
        }
    }

    suspend fun saveQrPairing(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.QR_PAIRING_ENABLED] = enabled
        }
    }

    suspend fun saveManualAuthKey(key: String) {
        withContext(Dispatchers.IO) {
            runCatching { secrets().saveManualAuthKey(key) }
        }
    }

    suspend fun saveEncryption(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ENCRYPTION_ENABLED] = enabled
        }
    }

    // Multicast encryption: server monotonic session epoch + per-server-IP highest
    // epoch accepted by a client (anti ghost-replay).
    suspend fun nextMcastEpoch(): Long {
        val key = androidx.datastore.preferences.core.longPreferencesKey("mcast_server_epoch")
        var result = 1L
        dataStore.edit { p -> val e = (p[key] ?: 0L) + 1L; p[key] = e; result = e }
        return result
    }
    suspend fun getMcastClientEpoch(ip: String): Long {
        val key = androidx.datastore.preferences.core.longPreferencesKey("mcast_epoch_$ip")
        return dataStore.data.first()[key] ?: 0L
    }
    suspend fun setMcastClientEpoch(ip: String, e: Long) {
        val key = androidx.datastore.preferences.core.longPreferencesKey("mcast_epoch_$ip")
        dataStore.edit { p -> p[key] = e }
    }

    suspend fun isDonationQualified(): Boolean {
        val key = booleanPreferencesKey("donation_qualified")
        return dataStore.data.first()[key] ?: false
    }
    suspend fun setDonationQualified(b: Boolean) {
        val key = booleanPreferencesKey("donation_qualified")
        dataStore.edit { p -> p[key] = b }
    }
    suspend fun donationSnoozeUntil(): Long {
        val key = androidx.datastore.preferences.core.longPreferencesKey("donation_snooze_until")
        return dataStore.data.first()[key] ?: 0L
    }
    suspend fun setDonationSnoozeUntil(t: Long) {
        val key = androidx.datastore.preferences.core.longPreferencesKey("donation_snooze_until")
        dataStore.edit { p -> p[key] = t }
    }
    suspend fun donationDismissCount(): Int {
        val key = androidx.datastore.preferences.core.intPreferencesKey("donation_dismiss_count")
        return dataStore.data.first()[key] ?: 0
    }
    suspend fun setDonationDismissCount(n: Int) {
        val key = androidx.datastore.preferences.core.intPreferencesKey("donation_dismiss_count")
        dataStore.edit { p -> p[key] = n }
    }
    suspend fun donationSupported(): Boolean {
        val key = booleanPreferencesKey("donation_supported")
        return dataStore.data.first()[key] ?: false
    }
    suspend fun setDonationSupported(b: Boolean) {
        val key = booleanPreferencesKey("donation_supported")
        dataStore.edit { p -> p[key] = b }
    }
    suspend fun resetDonationPrompt() {
        setDonationSupported(false)
        setDonationQualified(true)
        setDonationDismissCount(0)
        setDonationSnoozeUntil(0L)
    }

    fun donationBackoffDays(count: Int): Long = when {
        count <= 1 -> 2L
        count == 2 -> 5L
        count == 3 -> 14L
        else -> 30L
    }

    suspend fun saveStreamingPort(port: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.STREAMING_PORT] = port
        }
    }

    suspend fun saveSendClientMicrophone(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SEND_CLIENT_MICROPHONE] = enabled
        }
    }

    suspend fun saveMicPort(port: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MIC_PORT] = port
        }
    }

    suspend fun saveLastMulticastMode(isMulticast: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_MULTICAST_MODE] = isMulticast
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun saveNetworkInterface(interfaceName: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NETWORK_INTERFACE] = interfaceName
        }
    }

    suspend fun saveServerProtocols(rtpEnabled: Boolean, rtpPort: Int, httpEnabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.RTP_ENABLED] = rtpEnabled
            preferences[PreferencesKeys.RTP_PORT] = rtpPort
            preferences[PreferencesKeys.HTTP_ENABLED] = httpEnabled
        }
    }

    /**
     * Turns off every protocol that carries the audio outside WFAS, in one
     * edit so that nothing reading the settings in between sees half of it.
     */
    suspend fun disableNonWfasProtocols() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.RTP_ENABLED] = false
            preferences[PreferencesKeys.HTTP_ENABLED] = false
            preferences[PreferencesKeys.DLNA_ENABLED] = false
            preferences[PreferencesKeys.SNAPCAST_ENABLED] = false
        }
    }

    suspend fun saveSnapcastEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SNAPCAST_ENABLED] = enabled
        }
    }

    suspend fun saveSnapcastSettings(
        port: Int,
        controlPort: Int,
        codec: String,
        chunkMs: Int,
        bufferMs: Int,
        streamName: String
    ) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SNAPCAST_PORT] = port
            preferences[PreferencesKeys.SNAPCAST_CONTROL_PORT] = controlPort
            preferences[PreferencesKeys.SNAPCAST_CODEC] =
                com.cuscus.wifiaudiostreaming.snapcast.SnapcastCodecs.normalize(codec)
            preferences[PreferencesKeys.SNAPCAST_CHUNK_MS] = chunkMs
            preferences[PreferencesKeys.SNAPCAST_BUFFER_MS] = bufferMs
            preferences[PreferencesKeys.SNAPCAST_STREAM_NAME] = streamName
        }
    }

    suspend fun saveDlnaEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DLNA_ENABLED] = enabled
        }
    }

    suspend fun saveDlnaSettings(port: Int, format: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DLNA_PORT] = port
            preferences[PreferencesKeys.DLNA_FORMAT] = format
        }
    }

    suspend fun saveRtpSources(entries: List<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.RTP_SOURCES] = entries.joinToString("\n")
        }
    }

    suspend fun saveSnapcastServers(entries: List<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SNAPCAST_SERVERS] = entries.joinToString("\n")
        }
    }

    suspend fun saveDlnaDevices(entries: List<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DLNA_DEVICES] = entries.joinToString("\n")
        }
    }

    suspend fun saveHttpSettings(port: Int, safariMode: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HTTP_PORT] = port
            preferences[PreferencesKeys.HTTP_SAFARI_MODE] = safariMode
        }
    }

    suspend fun setAutoConnectEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_CONNECT_ENABLED] = enabled
        }
    }

    suspend fun toggleAutoConnectIp(ip: String) {
        dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.AUTO_CONNECT_LIST] ?: ""
            val list = AutoConnectEntry.parseList(current).toMutableList()
            val existing = list.find { it.ip == ip }
            if (existing != null) {
                list.remove(existing)
            } else {
                list.add(AutoConnectEntry(ip, ""))
            }
            preferences[PreferencesKeys.AUTO_CONNECT_LIST] = AutoConnectEntry.serializeList(list)
        }
    }

    suspend fun saveAutoConnectList(list: List<AutoConnectEntry>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_CONNECT_LIST] = AutoConnectEntry.serializeList(list)
        }
    }

    suspend fun saveConnectionSoundEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CONNECTION_SOUND_ENABLED] = enabled
        }
    }

    suspend fun saveDisconnectionSoundEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DISCONNECTION_SOUND_ENABLED] = enabled
        }
    }

    suspend fun saveDeveloperMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEVELOPER_MODE] = enabled
            // Spegnendo la modalita' sviluppatore non deve restare attivo un DSP
            // che l'utente non puo' piu' vedere ne' disattivare.
            if (!enabled) preferences[PreferencesKeys.NOISE_REDUCTION_ENABLED] = false
        }
    }

    suspend fun saveNoiseReduction(enabled: Boolean, strength: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOISE_REDUCTION_ENABLED] = enabled
            preferences[PreferencesKeys.NOISE_REDUCTION_STRENGTH] = strength.coerceIn(0, 100)
        }
    }

    suspend fun saveHapticsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun saveBlackoutOutlinedUi(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLACKOUT_OUTLINED_UI] = enabled
        }
    }

}