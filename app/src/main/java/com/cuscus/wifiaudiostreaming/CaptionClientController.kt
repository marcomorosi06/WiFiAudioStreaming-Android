/*
 * Copyright (c) 2026 Marco Morosi
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 */

package com.cuscus.wifiaudiostreaming

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong

object CaptionClientController {

    private const val TAG = "WFAS-CAP"
    private const val TICK_MS = 100L

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var receiver: CaptionReceiver? = null

    @Volatile
    private var timeline: CaptionTimeline? = null

    private var ticker: Job? = null

    private val writtenSamplePos = AtomicLong(-1L)
    private val pendingFrames = AtomicLong(0L)

    val state = MutableStateFlow<CaptionChannelState>(CaptionChannelState.Idle)

    val isActive: Boolean get() = receiver != null

    fun start(
        context: Context,
        serverIp: String,
        captionPort: Int?,
        authKey: String,
        encrypting: Boolean,
        proved: Boolean,
        cnonceHex: String,
        snonceHex: String,
        sampleRate: Int
    ) {
        stop(context)

        val prefs = AndroidCaptionPreferences.load(context)
        if (!prefs.showOverlay) {
            Log.i(TAG, "not starting: the overlay switch is off in settings")
            return
        }
        if (captionPort == null) {
            Log.i(TAG, "not starting: server $serverIp did not advertise cap= in its beacon, so it has no captions to send")
            state.value = CaptionChannelState.Unavailable(WfasCaptions.REASON_DISABLED)
            return
        }
        if (!CaptionOverlayService.canDrawOverlays(context)) {
            Log.w(TAG, "not starting: draw-over-other-apps permission not granted")
            state.value = CaptionChannelState.Refused("no-overlay-permission")
            return
        }

        val address = runCatching { InetAddress.getByName(serverIp) }.getOrNull()
        if (address == null) {
            Log.w(TAG, "not starting: cannot resolve $serverIp")
            return
        }

        writtenSamplePos.set(-1L)
        pendingFrames.set(0L)
        val line = CaptionTimeline(sampleRate)
        timeline = line

        val language = java.util.Locale.getDefault().language.ifEmpty { "auto" }

        val rx = CaptionReceiver(
            scope = scope,
            serverAddress = address,
            serverPort = captionPort,
            authKey = authKey,
            encrypting = encrypting,
            cnonceHex = cnonceHex,
            snonceHex = snonceHex,
            requestedLanguage = language,
            sendProof = proved && authKey.isNotEmpty(),
            onState = { s ->
                state.value = s
                Log.d(TAG, "channel state: $s")
                if (s is CaptionChannelState.Active) CaptionOverlayService.show(context)
            },
            onCaption = { c ->
                Log.d(TAG, "caption #${c.capId}.${c.rev} @${c.samplePos} final=${c.isFinal} '${c.text}'")
                line.submit(c)
            }
        )
        receiver = rx
        rx.start()

        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val written = writtenSamplePos.get()
                if (written < 0L) continue
                val lag = pendingFrames.get().coerceAtLeast(0L)
                val playing = (written - lag) and 0xFFFFFFFFL
                CaptionOverlayService.setText(line.textAt(playing))
            }
        }
        Log.d(TAG, "captions requested from $serverIp:$captionPort lang=$language enc=$encrypting")
    }

    fun onAudioPacket(samplePos: Long) {
        writtenSamplePos.set(samplePos)
    }

    fun onPlaybackLag(frames: Long) {
        pendingFrames.set(frames)
    }

    fun stop(context: Context) {
        ticker?.cancel()
        ticker = null
        receiver?.stop()
        receiver = null
        timeline?.reset()
        timeline = null
        writtenSamplePos.set(-1L)
        pendingFrames.set(0L)
        state.value = CaptionChannelState.Idle
        CaptionOverlayService.setText("")
        CaptionOverlayService.hide(context)
    }
}
