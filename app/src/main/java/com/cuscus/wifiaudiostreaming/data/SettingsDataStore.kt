package com.cuscus.wifiaudiostreaming.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// Data class per contenere tutte le impostazioni in modo strutturato
data class AppSettings(
    val streamInternal: Boolean,
    val streamMic: Boolean,
    val sampleRate: Int,
    val channelConfig: String, // "MONO" o "STEREO"
    val bufferSize: Int,
    val streamingPort: Int,
    val sendClientMicrophone: Boolean,
    val micPort: Int,
    val experimentalFeaturesEnabled: Boolean,
    val onboardingCompleted: Boolean
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
            onboardingCompleted = preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false
        )
    }

    suspend fun saveAudioSourceSettings(streamInternal: Boolean, streamMic: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.STREAM_INTERNAL] = streamInternal
            preferences[PreferencesKeys.STREAM_MIC] = streamMic
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
}