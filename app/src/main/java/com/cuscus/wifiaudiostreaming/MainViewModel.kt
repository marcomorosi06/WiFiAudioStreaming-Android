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
import android.app.Application
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cuscus.wifiaudiostreaming.data.AppScript
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

    private val _isMulticastMode = MutableStateFlow(false)
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

    val protocolMismatch: StateFlow<ProtocolMismatch?> = NetworkManager.protocolMismatch

    fun clearProtocolMismatch() = NetworkManager.clearProtocolMismatch()

    private val _updateBanner = MutableStateFlow<UpdateChecker.Result.Available?>(null)
    val updateBanner: StateFlow<UpdateChecker.Result.Available?> = _updateBanner.asStateFlow()

    private val _manualUpdateResult = MutableStateFlow<UpdateChecker.Result?>(null)
    val manualUpdateResult: StateFlow<UpdateChecker.Result?> = _manualUpdateResult.asStateFlow()

    private val _versionAhead = MutableStateFlow<UpdateChecker.Result.Ahead?>(null)
    val versionAhead: StateFlow<UpdateChecker.Result.Ahead?> = _versionAhead.asStateFlow()

    private val _checkingForUpdate = MutableStateFlow(false)
    val checkingForUpdate: StateFlow<Boolean> = _checkingForUpdate.asStateFlow()

    fun autoCheckForUpdates() {
        viewModelScope.launch {
            val enabled = settingsDataStore.settingsFlow.first().autoUpdateCheckEnabled
            if (!enabled) return@launch
            val r = UpdateChecker.check(getApplication<Application>())
            // In automatico si mostra solo qualcosa di utile: se GitHub non
            // risponde si resta in silenzio.
            when (r) {
                is UpdateChecker.Result.Available -> _updateBanner.value = r
                is UpdateChecker.Result.Ahead     -> _versionAhead.value = r
                else -> Unit
            }
        }
    }

    fun checkForUpdatesManual() {
        if (_checkingForUpdate.value) return
        viewModelScope.launch {
            _checkingForUpdate.value = true
            val r = UpdateChecker.check(getApplication<Application>())
            _checkingForUpdate.value = false
            _manualUpdateResult.value = r
        }
    }

    fun dismissUpdateBanner() { _updateBanner.value = null }
    fun dismissVersionAhead() { _versionAhead.value = null }
    fun clearManualUpdateResult() { _manualUpdateResult.value = null }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAutoUpdateCheckEnabled(enabled) }
    }

    val discoveredDevices: StateFlow<Map<String, ServerInfo>> = NetworkManager.discoveredDevices

    val scripts: StateFlow<List<AppScript>> = settingsDataStore.scriptsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveScript(script: AppScript) {
        viewModelScope.launch {
            val current = settingsDataStore.scriptsFlow.first().toMutableList()
            val index = current.indexOfFirst { it.id == script.id }
            if (index >= 0) current[index] = script else current.add(script)
            settingsDataStore.saveScripts(current)
        }
    }

    fun deleteScript(id: String) {
        viewModelScope.launch {
            val current = settingsDataStore.scriptsFlow.first().filterNot { it.id == id }
            settingsDataStore.saveScripts(current)
        }
    }

    init {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.first().let { settings ->
                if (!NetworkManager.isStreamingCurrent.value) {
                    _isMulticastMode.value = settings.lastMulticastMode
                }
            }
        }
    }

    fun toggleMode(isServerMode: Boolean) {
        _isServer.value = isServerMode
        if (isServerMode) {
            NetworkManager.stopListeningForDevices()
            clearDiscoveredDevices()
        }
    }

    fun setMulticastMode(isMulticast: Boolean) {
        _isMulticastMode.value = isMulticast
        viewModelScope.launch {
            settingsDataStore.saveLastMulticastMode(isMulticast)
        }
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

    fun setAdvancedAudio(latencyMs: Int, maxPayloadBytes: Int) {
        viewModelScope.launch {
            settingsDataStore.saveAdvancedAudio(latencyMs, maxPayloadBytes)
        }
    }

    fun setSecurity(mode: String, key: String) {
        viewModelScope.launch {
            settingsDataStore.saveSecurity(mode, key)
        }
    }

    fun setEncryption(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveEncryption(enabled)
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

    fun setOnboardingCompleted() {
        viewModelScope.launch {
            settingsDataStore.setLastSeenChangelogVersion(Changelog.latest.version)
            settingsDataStore.setOnboardingCompleted(true)
        }
    }

    fun markChangelogSeen() {
        viewModelScope.launch {
            settingsDataStore.setLastSeenChangelogVersion(Changelog.latest.version)
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

    fun setConnectionSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.saveConnectionSoundEnabled(enabled) }
    }

    fun setDisconnectionSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.saveDisconnectionSoundEnabled(enabled) }
    }

    fun setDeveloperMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveDeveloperMode(enabled)
            if (!enabled) NetworkManager.setNoiseReduction(false, 0)
        }
    }

    fun setNoiseReduction(enabled: Boolean, strength: Int) {
        viewModelScope.launch {
            settingsDataStore.saveNoiseReduction(enabled, strength)
            // Applicato subito al flusso in corso: niente riconnessione.
            NetworkManager.setNoiseReduction(enabled, strength)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.saveHapticsEnabled(enabled) }
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
        // Niente ottimismo: lo stato "in riproduzione" lo alza NetworkManager
        // quando l'handshake e' andato a buon fine. Alzarlo qui faceva sembrare
        // connesso anche un client rifiutato perche' il server era occupato.

        val currentSettings = appSettings.value
        if (currentSettings != null) {
            NetworkManager.configureSecurity(currentSettings.securityMode, currentSettings.authKey, currentSettings.encryptionEnabled)
            NetworkManager.clientPresharedKey = ""   // interactive client: key comes from the on-connect dialog
            NetworkManager.startClient(
                context = getApplication(),
                serverInfo = serverInfo,
                sampleRate = currentSettings.sampleRate,
                channelConfig = currentSettings.channelConfig,
                bufferSize = currentSettings.bufferSize,
                sendMicrophone = currentSettings.sendClientMicrophone,
                micPort = currentSettings.micPort,
                networkInterfaceName = currentSettings.networkInterface,
                connectionSoundEnabled = currentSettings.connectionSoundEnabled,
                disconnectionSoundEnabled = currentSettings.disconnectionSoundEnabled,
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

