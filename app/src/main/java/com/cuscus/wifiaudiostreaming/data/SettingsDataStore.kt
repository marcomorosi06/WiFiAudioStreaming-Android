package com.cuscus.wifiaudiostreaming.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AutoConnectEntry(val ip: String, val ssid: String = "") {
    override fun toString() = "$ip|$ssid"
    companion object {
        fun fromString(str: String): AutoConnectEntry {
            val parts = str.split("|")
            return AutoConnectEntry(parts[0], parts.getOrNull(1) ?: "")
        }
        fun parseList(str: String): List<AutoConnectEntry> {
            return str.split(",").filter { it.isNotBlank() }.map { fromString(it) }
        }
        fun serializeList(list: List<AutoConnectEntry>): String {
            return list.joinToString(",") { it.toString() }
        }
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
    val experimentalFeaturesEnabled: Boolean,
    val onboardingCompleted: Boolean,
    val networkInterface: String,
    val rtpEnabled: Boolean,
    val rtpPort: Int,
    val httpEnabled: Boolean,
    val httpPort: Int,
    val httpSafariMode: Boolean,
    val clientTileIp: String = "",
    val autoConnectEnabled: Boolean = false,
    val autoConnectList: String = ""
)

class SettingsDataStore(context: Context) {
    private val dataStore = context.settingsDataStore

    private object PreferencesKeys {
        val STREAM_INTERNAL = booleanPreferencesKey("stream_internal")
        val STREAM_MIC = booleanPreferencesKey("stream_mic")
        val SAMPLE_RATE = intPreferencesKey("sample_rate")
        val CHANNEL_CONFIG = stringPreferencesKey("channel_config")
        val BUFFER_SIZE = intPreferencesKey("buffer_size")
        val STREAMING_PORT = intPreferencesKey("streaming_port")
        val SEND_CLIENT_MICROPHONE = booleanPreferencesKey("send_client_microphone")
        val MIC_PORT = intPreferencesKey("mic_port")
        val EXPERIMENTAL_FEATURES_ENABLED = booleanPreferencesKey("experimental_features_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val NETWORK_INTERFACE = stringPreferencesKey("network_interface")
        val RTP_ENABLED = booleanPreferencesKey("rtp_enabled")
        val RTP_PORT = intPreferencesKey("rtp_port")
        val HTTP_ENABLED = booleanPreferencesKey("http_enabled")
        val HTTP_PORT = intPreferencesKey("http_port")
        val HTTP_SAFARI_MODE = booleanPreferencesKey("http_safari_mode")
        val CLIENT_TILE_IP = stringPreferencesKey("client_tile_ip")
        val AUTO_CONNECT_ENABLED = booleanPreferencesKey("auto_connect_enabled")
        val AUTO_CONNECT_LIST = stringPreferencesKey("auto_connect_list")
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            streamInternal = preferences[PreferencesKeys.STREAM_INTERNAL] ?: true,
            streamMic = preferences[PreferencesKeys.STREAM_MIC] ?: false,
            sampleRate = preferences[PreferencesKeys.SAMPLE_RATE] ?: 48000,
            channelConfig = preferences[PreferencesKeys.CHANNEL_CONFIG] ?: "STEREO",
            bufferSize = preferences[PreferencesKeys.BUFFER_SIZE] ?: 6400,
            streamingPort = preferences[PreferencesKeys.STREAMING_PORT] ?: 9090,
            sendClientMicrophone = preferences[PreferencesKeys.SEND_CLIENT_MICROPHONE] ?: false,
            micPort = preferences[PreferencesKeys.MIC_PORT] ?: 9092,
            experimentalFeaturesEnabled = preferences[PreferencesKeys.EXPERIMENTAL_FEATURES_ENABLED] ?: false,
            onboardingCompleted = preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false,
            networkInterface = preferences[PreferencesKeys.NETWORK_INTERFACE] ?: "Auto",
            rtpEnabled = preferences[PreferencesKeys.RTP_ENABLED] ?: false,
            rtpPort = preferences[PreferencesKeys.RTP_PORT] ?: 9094,
            httpEnabled = preferences[PreferencesKeys.HTTP_ENABLED] ?: false,
            httpPort = preferences[PreferencesKeys.HTTP_PORT] ?: 8080,
            httpSafariMode = preferences[PreferencesKeys.HTTP_SAFARI_MODE] ?: false,
            clientTileIp = preferences[PreferencesKeys.CLIENT_TILE_IP] ?: "",
            autoConnectEnabled = preferences[PreferencesKeys.AUTO_CONNECT_ENABLED] ?: false,
            autoConnectList = preferences[PreferencesKeys.AUTO_CONNECT_LIST] ?: ""
        )
    }

    suspend fun saveAudioSourceSettings(streamInternal: Boolean, streamMic: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.STREAM_INTERNAL] = streamInternal
            preferences[PreferencesKeys.STREAM_MIC] = streamMic
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

    suspend fun saveExperimentalFeatures(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.EXPERIMENTAL_FEATURES_ENABLED] = enabled
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
}