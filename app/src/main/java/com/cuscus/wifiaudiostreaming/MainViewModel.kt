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

    // Il ruolo si fissa quando lo stream parte e non cambia piu' finche' dura.
    // Derivarlo di continuo da NetworkManager.isServerStreaming, che e' un var
    // normale e viene azzerato durante il teardown, faceva saltare la UI in
    // modalita' client a stream ancora vivo (per esempio alla caduta del WiFi).
    val isServer: StateFlow<Boolean> = _isServer.asStateFlow()

    init {
        viewModelScope.launch {
            NetworkManager.isStreamingCurrent.collect { streaming ->
                if (streaming) _isServer.value = NetworkManager.isServerStreaming
                else restoreForcedEncryption()
            }
        }
    }

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

    val usbLinkState: StateFlow<UsbLink.State> = UsbLink.state

    val linkMetrics: StateFlow<LinkMetrics.Snapshot> = LinkMetrics.snapshot

    init {
        viewModelScope.launch {
            settingsDataStore.settingsFlow
                .map { Triple(it.usbModeEnabled, it.usbLatencyMs, it.wfasMode) }
                .distinctUntilChanged()
                .collect { (enabled, latency, wfas) ->
                    UsbLink.configure(getApplication(), enabled, latency)
                    WfasPolicy.configure(wfas)
                }
        }
    }

    fun setWfasMode(mode: String) {
        viewModelScope.launch { settingsDataStore.saveWfasMode(mode) }
    }

    fun refreshUsbLink() { UsbLink.refresh() }

    fun setUsbMode(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.saveUsbMode(enabled) }
    }

    fun setUsbLatency(latencyMs: Int) {
        viewModelScope.launch { settingsDataStore.saveUsbLatency(latencyMs) }
    }

    fun openUsbTetherSettings() {
        UsbLink.openTetherSettings(getApplication())
    }

    val connectionStatus: StateFlow<String> = NetworkManager.connectionStatus.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = application.getString(R.string.status_idle)
    )

    val protocolMismatch: StateFlow<ProtocolMismatch?> = NetworkManager.protocolMismatch

    fun clearProtocolMismatch() = NetworkManager.clearProtocolMismatch()

    val unresponsiveServer: StateFlow<String?> = NetworkManager.unresponsiveServer

    fun clearUnresponsiveServer() = NetworkManager.clearUnresponsiveServer()

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
        viewModelScope.launch {
            combine(
                settingsDataStore.settingsFlow,
                _isMulticastMode
            ) { settings, multicast -> settings to multicast }
                .collect { (settings, multicast) ->
                    if (!settings.encryptionEnabled &&
                        isEncryptionForced(settings, multicast)
                    ) {
                        settingsDataStore.saveEncryption(true)
                    }
                }
        }
    }

    private fun effectiveMulticast(settings: AppSettings, multicast: Boolean): Boolean =
        multicast || settings.rtpEnabled || settings.httpEnabled ||
            settings.dlnaEnabled || settings.snapcastEnabled

    private fun isEncryptionForced(
        settings: AppSettings,
        multicast: Boolean = _isMulticastMode.value
    ): Boolean = SecurityMode.encryptionForcedStored(
        settings.securityMode,
        settings.qrPairingEnabled,
        effectiveMulticast(settings, multicast)
    )

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

    fun setSecurity(uiMode: String, key: String) {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            val wasQr = settings.qrPairingEnabled && SecurityMode.requiresKey(settings.securityMode)
            val nowQr = SecurityMode.isQrUiMode(uiMode)

            if (nowQr) {
                if (!wasQr && settings.manualAuthKey.isBlank() &&
                    SecurityMode.requiresKey(settings.securityMode)
                ) {
                    settingsDataStore.saveManualAuthKey(settings.authKey)
                }
                settingsDataStore.saveSecurity(SecurityMode.KEY.name, key)
                settingsDataStore.saveQrPairing(true)
                return@launch
            }

            val effectiveKey = if (wasQr) settings.manualAuthKey else key
            settingsDataStore.saveManualAuthKey(effectiveKey)
            settingsDataStore.saveSecurity(SecurityMode.storedMode(uiMode), effectiveKey)
            settingsDataStore.saveQrPairing(false)
        }
    }

    fun setEncryption(enabled: Boolean) {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            if (!enabled && isEncryptionForced(settings)) return@launch
            settingsDataStore.saveEncryption(enabled)
        }
    }

    @Volatile private var encryptionForcedByInvite = false

    private val _qrInvite = MutableStateFlow<QrInvite?>(null)
    val qrInvite: StateFlow<QrInvite?> = _qrInvite.asStateFlow()

    private val _pendingPairing = MutableStateFlow<PairingPayload?>(null)
    val pendingPairing: StateFlow<PairingPayload?> = _pendingPairing.asStateFlow()

    private val _pairingError = MutableStateFlow<PairingError?>(null)
    val pairingError: StateFlow<PairingError?> = _pairingError.asStateFlow()

    enum class PairingError { INVALID, EXPIRED, SELF }

    private fun isOwnInvite(payload: PairingPayload): Boolean {
        if (payload.isMulticast) {
            val ourKey = NetworkManager.mcastSession.value?.key
            return !ourKey.isNullOrBlank() && ourKey == payload.keyBase64
        }
        return NetAddr.isSelfAddress(payload.ip)
    }

    private val _scannerVisible = MutableStateFlow(false)
    val scannerVisible: StateFlow<Boolean> = _scannerVisible.asStateFlow()

    private val _noCameraVisible = MutableStateFlow(false)
    val noCameraVisible: StateFlow<Boolean> = _noCameraVisible.asStateFlow()

    fun openScanner() { _scannerVisible.value = true }
    fun closeScanner() { _scannerVisible.value = false }
    fun showNoCamera() { _noCameraVisible.value = true }
    fun dismissNoCamera() { _noCameraVisible.value = false }

    val epochMismatch: StateFlow<Boolean> = NetworkManager.pendingEpochMismatch

    fun clearEpochMismatch() = NetworkManager.clearEpochMismatch()

    val inviteRejected: StateFlow<Boolean> = NetworkManager.pendingInviteRejected

    fun clearInviteRejected() = NetworkManager.clearInviteRejected()

    fun dismissQrInvite() { _qrInvite.value = null }

    fun dismissPendingPairing() { _pendingPairing.value = null }

    fun clearPairingError() { _pairingError.value = null }

    private suspend fun persistPairingKey(key: String) {
        settingsDataStore.saveSecurity(SecurityMode.KEY.name, key)
        settingsDataStore.saveQrPairing(true)
        val encryption = settingsDataStore.settingsFlow.first().encryptionEnabled
        NetworkManager.configureSecurity(SecurityMode.KEY.name, key, encryption)
    }

    fun generateInvite(localIp: String, multicast: Boolean, forceNewKey: Boolean = false) {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            val port = settings.streamingPort

            val ip = if (multicast) {
                NetworkManager.MULTICAST_GROUP
            } else {
                localIp.ifBlank { NetworkManager.getLocalIpAddress(getApplication()) }
            }
            if (ip.isBlank() || ip == "0.0.0.0") {
                updateStatus(getApplication<Application>().getString(R.string.qr_invite_no_ip))
                return@launch
            }

            var encryptionForced = false
            if (multicast && !settings.encryptionEnabled) {
                settingsDataStore.saveEncryption(true)
                encryptionForced = true
                encryptionForcedByInvite = true
            }

            val currentIsGenerated = settings.authKey.isNotBlank() &&
                    settings.authKey != settings.manualAuthKey &&
                    settings.qrPairingEnabled

            val reuse = multicast && !forceNewKey && currentIsGenerated

            val key = if (reuse) {
                NetworkManager.mcastSession.value?.key?.takeIf { it.isNotBlank() }
                    ?: settings.authKey
            } else {
                WfasAuth.randomPairingKey()
            }
            if (!reuse) persistPairingKey(key)

            var epoch: Long? = null
            if (multicast) {
                if (!reuse || encryptionForced) NetworkManager.rekeyMulticast(key)
                epoch = NetworkManager.mcastSession.value?.takeIf { it.encrypted }?.epoch
            }

            val mode = if (multicast) WfasPairingUri.MODE_MULTICAST else WfasPairingUri.MODE_UNICAST
            val exp = System.currentTimeMillis() / 1000 + WfasPairingUri.PAIRING_TTL_SECONDS
            val uri = WfasPairingUri.buildAppLink(ip, port, mode, key, exp, epoch)

            _qrInvite.value = QrInvite(
                uri = uri,
                key = key,
                ip = ip,
                port = port,
                multicast = multicast,
                expEpochSeconds = exp,
                encryptionForced = encryptionForced
            )
        }
    }

    fun regenerateGroupKey(localIp: String) {
        generateInvite(localIp, multicast = true, forceNewKey = true)
    }

    fun submitScannedCode(raw: String) {
        _scannerVisible.value = false
        val payload = WfasPairingUri.parse(raw)
        if (payload == null) {
            _pairingError.value =
                if (WfasPairingUri.isExpiredUri(raw)) PairingError.EXPIRED else PairingError.INVALID
            return
        }
        if (isOwnInvite(payload)) {
            _pairingError.value = PairingError.SELF
            return
        }
        _pendingPairing.value = null
        applyPairing(payload)
    }

    fun submitDeepLink(raw: String): Boolean {
        val payload = WfasPairingUri.parse(raw)
        if (payload == null) {
            if (WfasPairingUri.isExpiredUri(raw)) {
                _pairingError.value = PairingError.EXPIRED
                return true
            }
            return false
        }
        if (isOwnInvite(payload)) {
            _pairingError.value = PairingError.SELF
            return true
        }
        _pendingPairing.value = payload
        return true
    }

    fun confirmPendingPairing() {
        val payload = _pendingPairing.value ?: return
        _pendingPairing.value = null
        if (isOwnInvite(payload)) {
            _pairingError.value = PairingError.SELF
            return
        }
        applyPairing(payload)
    }

    val serverRunning: Boolean
        get() = NetworkManager.isServerStreaming

    private fun stopCaptureServiceIfRunning() {
        if (!NetworkManager.isServerStreaming) return
        val app = getApplication<Application>()
        val yieldIntent = Intent(app, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_YIELD
        }
        runCatching { app.startService(yieldIntent) }
    }

    private fun isPayloadExpired(payload: PairingPayload): Boolean {
        val now = System.currentTimeMillis() / 1000
        return now - WfasPairingUri.CLOCK_SKEW_SECONDS > payload.expEpochSeconds
    }

    @SuppressLint("MissingPermission")
    fun applyPairing(payload: PairingPayload) {
        if (isPayloadExpired(payload)) {
            _pairingError.value = PairingError.EXPIRED
            return
        }
        viewModelScope.launch {
            NetworkManager.clearEpochMismatch()
            NetworkManager.expectedMcastEpoch = payload.mcastEpoch

            stopCaptureServiceIfRunning()

            _isServer.value = false
            _isMulticastMode.value = payload.isMulticast

            val serverInfo = ServerInfo(
                ip = NetAddr.normalize(payload.ip),
                isMulticast = payload.isMulticast,
                port = payload.port
            )
            startClient(serverInfo, presharedKey = payload.keyBase64)
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

    fun setBackgroundSpectrumSettings(enabled: Boolean, style: String, blackoutOnly: Boolean = false, groove: Int = 0) {
        viewModelScope.launch { settingsDataStore.saveBackgroundSpectrumSettings(enabled, style, blackoutOnly, groove) }
    }

    fun setBlackoutOutlinedUi(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.saveBlackoutOutlinedUi(enabled) }
    }

    // Aggiorna startListening (passando l'interfaccia)
    fun startListening() {
        val currentSettings = appSettings.value
        NetworkManager.startListeningForDevices(getApplication(), currentSettings?.networkInterface ?: "Auto")
    }

    suspend fun restartListening() {
        val currentSettings = appSettings.value
        NetworkManager.restartListeningForDevices(getApplication(), currentSettings?.networkInterface ?: "Auto")
    }

    // Aggiorna startClient (passando l'interfaccia)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startClient(serverInfo: ServerInfo, presharedKey: String? = null) {
        val intent = Intent(getApplication(), ClientService::class.java)
        getApplication<Application>().startService(intent)
        // Niente ottimismo: lo stato "in riproduzione" lo alza NetworkManager
        // quando l'handshake e' andato a buon fine. Alzarlo qui faceva sembrare
        // connesso anche un client rifiutato perche' il server era occupato.

        val currentSettings = appSettings.value
        if (currentSettings != null) {
            NetworkManager.configureSecurity(currentSettings.securityMode, currentSettings.authKey, currentSettings.encryptionEnabled)
            NetworkManager.clientPresharedKey = presharedKey ?: ""   // interactive client: key comes from the on-connect dialog
            NetworkManager.clientKeyFromInvite = presharedKey != null
            NetworkManager.clearInviteRejected()
            if (presharedKey == null) NetworkManager.expectedMcastEpoch = null
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
    fun startClientManually(rawIp: String) {
        val currentSettings = appSettings.value ?: return
        val ip = NetAddr.normalize(rawIp)
        if (ip.isBlank()) return

        viewModelScope.launch {
            updateStatus("Detecting mode for ${NetAddr.display(ip)}...")
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
        val app = getApplication<Application>()
        NetworkManager.stopStreaming(app)
        app.stopService(Intent(app, ClientService::class.java))
        app.stopService(Intent(app, AudioCaptureService::class.java))
        NotificationCenter.cancel(app, NotificationCenter.ID_SERVER)
        NotificationCenter.cancel(app, NotificationCenter.ID_CLIENT)
        restoreForcedEncryption()
    }

    fun restoreForcedEncryption() {
        if (!encryptionForcedByInvite) return
        encryptionForcedByInvite = false
        _qrInvite.value = null
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            if (isEncryptionForced(settings)) return@launch
            settingsDataStore.saveEncryption(false)
        }
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

