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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    val onboardingCompleted: Boolean,
    val networkInterface: String,
    val rtpEnabled: Boolean,
    val rtpPort: Int,
    val httpEnabled: Boolean,
    val httpPort: Int,
    val httpSafariMode: Boolean,
    val lastMulticastMode: Boolean = false,
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
    val encryptionEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true
)

class SettingsDataStore(context: Context) {
    private val dataStore = context.settingsDataStore

    private object PreferencesKeys {
        val STREAM_INTERNAL = booleanPreferencesKey("stream_internal")
        val STREAM_MIC = booleanPreferencesKey("stream_mic")
        val SAMPLE_RATE = intPreferencesKey("sample_rate")
        val CHANNEL_CONFIG = stringPreferencesKey("channel_config")
        val BUFFER_SIZE = intPreferencesKey("buffer_size")
        val LATENCY_MS = intPreferencesKey("latency_ms")
        val MAX_PAYLOAD = intPreferencesKey("max_payload")
        val SECURITY_MODE = stringPreferencesKey("security_mode")
        val AUTH_KEY = stringPreferencesKey("auth_key")
        val ENCRYPTION_ENABLED = booleanPreferencesKey("encryption_enabled")
        val STREAMING_PORT = intPreferencesKey("streaming_port")
        val SEND_CLIENT_MICROPHONE = booleanPreferencesKey("send_client_microphone")
        val MIC_PORT = intPreferencesKey("mic_port")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LAST_MULTICAST_MODE = booleanPreferencesKey("last_multicast_mode")
        val NETWORK_INTERFACE = stringPreferencesKey("network_interface")
        val RTP_ENABLED = booleanPreferencesKey("rtp_enabled")
        val RTP_PORT = intPreferencesKey("rtp_port")
        val HTTP_ENABLED = booleanPreferencesKey("http_enabled")
        val HTTP_PORT = intPreferencesKey("http_port")
        val HTTP_SAFARI_MODE = booleanPreferencesKey("http_safari_mode")
        val CLIENT_TILE_IP = stringPreferencesKey("client_tile_ip")
        val AUTO_CONNECT_ENABLED = booleanPreferencesKey("auto_connect_enabled")
        val AUTO_CONNECT_LIST = stringPreferencesKey("auto_connect_list")
        val CONNECTION_SOUND_ENABLED = booleanPreferencesKey("connection_sound_enabled")
        val DISCONNECTION_SOUND_ENABLED = booleanPreferencesKey("disconnection_sound_enabled")
        val AUTOMATION_SCRIPTS = stringPreferencesKey("automation_scripts")
        val LAST_SEEN_CHANGELOG_VERSION = stringPreferencesKey("last_seen_changelog_version")
        val AUTO_UPDATE_CHECK_ENABLED = booleanPreferencesKey("auto_update_check_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    }

    val scriptsFlow: Flow<List<AppScript>> = dataStore.data.map { preferences ->
        AppScript.parseList(preferences[PreferencesKeys.AUTOMATION_SCRIPTS] ?: "")
    }

    suspend fun saveScripts(list: List<AppScript>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTOMATION_SCRIPTS] = AppScript.serializeList(list)
        }
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data.map { preferences ->
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
            networkInterface = preferences[PreferencesKeys.NETWORK_INTERFACE] ?: "Auto",
            rtpEnabled = preferences[PreferencesKeys.RTP_ENABLED] ?: false,
            rtpPort = preferences[PreferencesKeys.RTP_PORT] ?: 9094,
            httpEnabled = preferences[PreferencesKeys.HTTP_ENABLED] ?: false,
            httpPort = preferences[PreferencesKeys.HTTP_PORT] ?: 8080,
            httpSafariMode = preferences[PreferencesKeys.HTTP_SAFARI_MODE] ?: false,
            clientTileIp = preferences[PreferencesKeys.CLIENT_TILE_IP] ?: "",
            autoConnectEnabled = preferences[PreferencesKeys.AUTO_CONNECT_ENABLED] ?: false,
            autoConnectList = preferences[PreferencesKeys.AUTO_CONNECT_LIST] ?: "",
            connectionSoundEnabled = preferences[PreferencesKeys.CONNECTION_SOUND_ENABLED] ?: true,
            disconnectionSoundEnabled = preferences[PreferencesKeys.DISCONNECTION_SOUND_ENABLED] ?: true,
            hapticsEnabled = preferences[PreferencesKeys.HAPTICS_ENABLED] ?: true,
            lastSeenChangelogVersion = preferences[PreferencesKeys.LAST_SEEN_CHANGELOG_VERSION] ?: "",
            autoUpdateCheckEnabled = preferences[PreferencesKeys.AUTO_UPDATE_CHECK_ENABLED] ?: true,
            latencyMs = preferences[PreferencesKeys.LATENCY_MS] ?: 120,
            maxPayloadBytes = preferences[PreferencesKeys.MAX_PAYLOAD] ?: 1390,
            securityMode = preferences[PreferencesKeys.SECURITY_MODE] ?: "OFF",
            authKey = preferences[PreferencesKeys.AUTH_KEY] ?: "",
            encryptionEnabled = preferences[PreferencesKeys.ENCRYPTION_ENABLED] ?: false
        )
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

    suspend fun saveAdvancedAudio(latencyMs: Int, maxPayloadBytes: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LATENCY_MS] = latencyMs
            preferences[PreferencesKeys.MAX_PAYLOAD] = maxPayloadBytes
        }
    }

    suspend fun saveSecurity(mode: String, key: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SECURITY_MODE] = mode
            preferences[PreferencesKeys.AUTH_KEY] = key
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

    suspend fun saveHapticsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTICS_ENABLED] = enabled
        }
    }

}