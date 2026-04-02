package com.cuscus.wifiaudiostreaming

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cuscus.wifiaudiostreaming.data.AppSettings
import com.cuscus.wifiaudiostreaming.data.AutoConnectEntry
import com.cuscus.wifiaudiostreaming.data.SettingsDataStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsDataStore = SettingsDataStore(application)

    private val _isServer = MutableStateFlow(true)
    val isServer: StateFlow<Boolean> = combine(
        _isServer,
        NetworkManager.isStreamingCurrent
    ) { serverMode, streaming ->
        if (streaming) NetworkManager.isServerStreaming else serverMode
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isStreaming: StateFlow<Boolean> = NetworkManager.isStreamingCurrent.asStateFlow()

    private val _isMulticastMode = MutableStateFlow(true)
    val isMulticastMode: StateFlow<Boolean> = _isMulticastMode.asStateFlow()

    // ## SOLUZIONE APPLICATA QUI ##
    // Il StateFlow ora è nullable (AppSettings?) e parte da `null` come valore iniziale.
    // Questo ci permette di distinguere lo stato "in caricamento" da quello "caricato".
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
            clearDiscoveredDevices()
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

    // Funzione aggiuntiva per resettare l'onboarding dalle impostazioni
    fun resetOnboarding() {
        viewModelScope.launch {
            settingsDataStore.setOnboardingCompleted(false)
        }
    }

    fun setIsStreaming(streaming: Boolean) {
        NetworkManager.isStreamingCurrent.value = streaming
    }

    fun setNetworkInterface(name: String) {
        viewModelScope.launch { settingsDataStore.saveNetworkInterface(name) }
    }

    fun setServerProtocols(rtpEnabled: Boolean, rtpPort: Int, httpEnabled: Boolean) {
        viewModelScope.launch { settingsDataStore.saveServerProtocols(rtpEnabled, rtpPort, httpEnabled) }
    }

    fun setHttpSettings(port: Int, safariMode: Boolean) {
        viewModelScope.launch { settingsDataStore.saveHttpSettings(port, safariMode) }
    }

    fun setClientTileIp(ip: String) {
        viewModelScope.launch { settingsDataStore.saveClientTileIp(ip) }
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAutoConnectEnabled(enabled) }
    }

    fun toggleAutoConnectIp(ip: String) {
        viewModelScope.launch { settingsDataStore.toggleAutoConnectIp(ip) }
    }

    fun saveAutoConnectList(list: List<AutoConnectEntry>) {
        viewModelScope.launch { settingsDataStore.saveAutoConnectList(list) }
    }

    // Aggiorna startListening (passando l'interfaccia)
    fun startListening() {
        val currentSettings = appSettings.value
        NetworkManager.startListeningForDevices(getApplication(), currentSettings?.networkInterface ?: "Auto")
    }

    // Aggiorna startClient (passando l'interfaccia)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startClient(serverInfo: ServerInfo) {
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
                networkInterfaceName = currentSettings.networkInterface,
                onServerDisconnected = {
                    setIsStreaming(false)
                    val stopIntent = Intent(getApplication(), ClientService::class.java)
                    getApplication<Application>().stopService(stopIntent)
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startClientManually(ip: String) {
        val currentSettings = appSettings.value ?: return

        viewModelScope.launch {
            updateStatus("Detecting mode for $ip...")
            val port = currentSettings.streamingPort

            val knownServer = discoveredDevices.value.values.find { it.ip == ip }

            val isMulti = knownServer?.isMulticast ?: NetworkManager.probeIsMulticast(ip, port)

            val manualServerInfo = ServerInfo(
                ip = ip,
                isMulticast = isMulti,
                port = port
            )
            startClient(manualServerInfo)
        }
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
        super.onCleared()
    }
}