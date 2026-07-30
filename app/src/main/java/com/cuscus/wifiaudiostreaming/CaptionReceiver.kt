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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

sealed class CaptionChannelState {
    object Idle : CaptionChannelState()
    object Requesting : CaptionChannelState()
    class Active(val language: String) : CaptionChannelState()
    class Unavailable(val reason: String) : CaptionChannelState()
    class Refused(val detail: String) : CaptionChannelState()
}

class CaptionReceiver(
    private val scope: CoroutineScope,
    private val serverAddress: InetAddress,
    private val serverPort: Int,
    private val authKey: String,
    private val encrypting: Boolean,
    private val cnonceHex: String,
    private val snonceHex: String,
    private val requestedLanguage: String,
    private val sendProof: Boolean,
    private val onState: (CaptionChannelState) -> Unit,
    private val onCaption: (WfasCaptions.Caption) -> Unit
) {

    private var socket: DatagramSocket? = null
    private var job: Job? = null
    private val running = AtomicBoolean(false)

    private var dir: WfasCrypto.Dir? = null
    private var window: WfasCrypto.ReplayWindow? = null
    private val dedup = WfasCaptions.Dedup()
    private val plaintextReported = AtomicBoolean(false)

    fun start() {
        if (running.getAndSet(true)) return
        job = scope.launch(Dispatchers.IO) { run() }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        runCatching {
            val s = socket
            if (s != null && !s.isClosed) {
                val bye = WfasCaptions.MSG_STOP.toByteArray(Charsets.US_ASCII)
                s.send(DatagramPacket(bye, bye.size, serverAddress, serverPort))
            }
        }
        job?.cancel()
        job = null
        runCatching { socket?.close() }
        socket = null
        dedup.reset()
        onState(CaptionChannelState.Idle)
    }

    private fun run() {
        val s = try {
            DatagramSocket().apply { soTimeout = WfasCaptions.REQUEST_TIMEOUT_MS.toInt() }
        } catch (e: Exception) {
            onState(CaptionChannelState.Refused("socket=${e.message}"))
            running.set(false)
            return
        }
        socket = s

        onState(CaptionChannelState.Requesting)
        val proof = if (sendProof) WfasCaptions.proof(authKey, cnonceHex, snonceHex) else null
        val request = WfasCaptions.buildRequest(requestedLanguage, proof).toByteArray(Charsets.US_ASCII)

        var accepted = false
        var attempt = 0
        while (running.get() && attempt <= WfasCaptions.REQUEST_RETRIES && !accepted) {
            attempt++
            val sent = runCatching {
                s.send(DatagramPacket(request, request.size, serverAddress, serverPort))
            }
            if (sent.isFailure) {
                onState(CaptionChannelState.Refused("send=${sent.exceptionOrNull()?.message}"))
                cleanup()
                return
            }
            val reply = awaitControl(s) ?: continue
            when {
                reply.startsWith(WfasCaptions.MSG_ACK) -> {
                    val serverEnc = WfasCaptions.token(reply, "enc") == "1"
                    if (encrypting && !serverEnc) {
                        onState(CaptionChannelState.Refused("downgrade"))
                        cleanup()
                        return
                    }
                    val lang = WfasCaptions.token(reply, "lang") ?: requestedLanguage
                    dir = if (encrypting) WfasCaptions.deriveUnicast(authKey, cnonceHex, snonceHex) else null
                    window = if (encrypting) WfasCrypto.ReplayWindow() else null
                    dedup.reset()
                    accepted = true
                    onState(CaptionChannelState.Active(lang))
                }
                reply.startsWith(WfasCaptions.MSG_UNAVAIL) -> {
                    val reason = WfasCaptions.token(reply, "reason") ?: WfasCaptions.REASON_DISABLED
                    onState(CaptionChannelState.Unavailable(reason))
                    cleanup()
                    return
                }
            }
        }

        if (!accepted) {
            onState(CaptionChannelState.Unavailable(WfasCaptions.REASON_DISABLED))
            cleanup()
            return
        }

        s.soTimeout = 500
        val buf = ByteArray(2048)
        while (scope.isActive && running.get()) {
            val packet = DatagramPacket(buf, buf.size)
            try {
                s.receive(packet)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (e: Exception) {
                if (running.get()) onState(CaptionChannelState.Refused("recv=${e.message}"))
                break
            }
            if (packet.address != serverAddress) continue
            consume(buf, packet.length)
        }
        cleanup()
    }

    private fun awaitControl(s: DatagramSocket): String? {
        val buf = ByteArray(512)
        val packet = DatagramPacket(buf, buf.size)
        return try {
            s.receive(packet)
            if (packet.address != serverAddress) null
            else String(buf, 0, packet.length, Charsets.US_ASCII)
        } catch (_: SocketTimeoutException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun consume(buf: ByteArray, len: Int) {
        when (val d = WfasCaptions.decode(dir, window, buf, len, encrypting)) {
            is WfasCaptions.Decoded.Ok -> {
                if (dedup.accept(d.caption)) onCaption(d.caption)
            }
            is WfasCaptions.Decoded.PolicyReject -> {
                if (!plaintextReported.getAndSet(true)) {
                    onState(CaptionChannelState.Refused("plaintext"))
                }
            }
            else -> Unit
        }
    }

    private fun cleanup() {
        running.set(false)
        runCatching { socket?.close() }
        socket = null
    }
}
