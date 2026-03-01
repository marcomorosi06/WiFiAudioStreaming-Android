package com.cuscus.wifiaudiostreaming

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cuscus.wifiaudiostreaming.data.AppSettings
import com.cuscus.wifiaudiostreaming.data.SettingsDataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsDataStore = SettingsDataStore(application)

    private val _isServer = MutableStateFlow(true)
    val isServer: StateFlow<Boolean> = _isServer.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isMulticastMode = MutableStateFlow(true)
    val isMulticastMode: StateFlow<Boolean> = _isMulticastMode.asStateFlow()

    val appSettings: StateFlow<AppSettings?> = settingsDataStore.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val connectionStatus: StateFlow<String> = NetworkManager.connectionStatus.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = application.getString(R.string.status_idle)
    )

    val discoveredDevices: StateFlow<Map<String, ServerInfo>> = NetworkManager.discoveredDevices

    fun toggleMode(isServerMode: Boolean) {
        _isServer.value = isServerMode
        if (isServerMode) {
            NetworkManager.stopListeningForDevices()
        }
    }

    fun setMulticastMode(isMulticast: Boolean) {
        _isMulticastMode.value = isMulticast
    }

    fun setStreamInternal(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.value?.let {
                settingsDataStore.saveAudioSourceSettings(enabled, it.streamMic)
            }
        }
    }

    fun setStreamMic(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.value?.let {
                settingsDataStore.saveAudioSourceSettings(it.streamInternal, enabled)
            }
        }
    }

    fun setSampleRate(rate: Int) {
        viewModelScope.launch {
            appSettings.value?.let {
                settingsDataStore.saveAudioQualitySettings(rate, it.channelConfig)
            }
        }
    }

    fun setChannelConfig(config: String) {
        viewModelScope.launch {
            appSettings.value?.let {
                settingsDataStore.saveAudioQualitySettings(it.sampleRate, config)
            }
        }
    }

    fun setBufferSize(size: Int) {
        viewModelScope.launch {
            settingsDataStore.saveBufferSize(size)
        }
    }

    fun setStreamingPort(port: Int) {
        viewModelScope.launch {
            if (port in 1024..65535) {
                settingsDataStore.saveStreamingPort(port)
            }
        }
    }

    fun setSendClientMicrophone(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveSendClientMicrophone(enabled)
        }
    }

    fun setMicPort(port: Int) {
        viewModelScope.launch {
            if (port in 1024..65535) {
                settingsDataStore.saveMicPort(port)
            }
        }
    }

    fun setExperimentalFeatures(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveExperimentalFeatures(enabled)
        }
    }

    fun setOnboardingCompleted() {
        viewModelScope.launch {
            settingsDataStore.setOnboardingCompleted(true)
        }
    }

    fun resetOnboarding() {
        viewModelScope.launch {
            settingsDataStore.setOnboardingCompleted(false)
        }
    }

    fun startListening() {
        NetworkManager.startListeningForDevices(getApplication())
    }

    fun setIsStreaming(streaming: Boolean) {
        _isStreaming.value = streaming
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    // MODIFICA QUI: Aggiunto password: String? = null
    fun startClient(serverInfo: ServerInfo, password: String? = null) {
        val intent = Intent(getApplication(), ClientService::class.java)
        getApplication<Application>().startService(intent)
        setIsStreaming(true)

        val currentSettings = appSettings.value
        if (currentSettings != null) {
            NetworkManager.startClient(
                context = getApplication(),
                serverInfo = serverInfo,
                sampleRate = currentSettings.sampleRate,
                channelConfig = currentSettings.channelConfig,
                bufferSize = currentSettings.bufferSize,
                sendMicrophone = currentSettings.sendClientMicrophone,
                micPort = currentSettings.micPort,
                password = password, // <-- Passaggio della password
                onServerDisconnected = {
                    setIsStreaming(false)
                    val stopIntent = Intent(getApplication(), ClientService::class.java)
                    getApplication<Application>().stopService(stopIntent)
                },
                onAuthDenied = {
                    updateStatus("Autenticazione Fallita / Password Errata")
                    setIsStreaming(false)
                    val stopIntent = Intent(getApplication(), ClientService::class.java)
                    getApplication<Application>().stopService(stopIntent)
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    // MODIFICA QUI: Aggiunto password: String? = null
    fun startClientManually(ip: String, password: String? = null) {
        val currentSettings = appSettings.value ?: return

        val manualServerInfo = ServerInfo(
            ip = ip,
            isMulticast = false,
            port = currentSettings.streamingPort,
            isPasswordProtected = password != null // Deduce se è protetto
        )
        // Passa info e password a startClient
        startClient(manualServerInfo, password)
    }

    fun stopStreaming() {
        setIsStreaming(false)
        NetworkManager.stopStreaming(getApplication())
    }

    fun updateStatus(message: String) {
        NetworkManager.connectionStatus.value = message
    }

    fun clearDiscoveredDevices() {
        (NetworkManager.discoveredDevices as MutableStateFlow).value = emptyMap()
    }

    override fun onCleared() {
        val intent = Intent(getApplication(), ClientService::class.java)
        getApplication<Application>().stopService(intent)
        super.onCleared()
        NetworkManager.stopAll()
    }
}