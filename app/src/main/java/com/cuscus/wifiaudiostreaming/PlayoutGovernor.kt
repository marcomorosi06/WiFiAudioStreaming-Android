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

import android.media.AudioTrack
import android.util.Log

class PlayoutGovernor(
    private val track: AudioTrack,
    private val sampleRate: Int,
    private val frameSize: Int,
    targetLatencyMs: Int,
    private val tag: String = "PLAYOUT"
) {
    private val framesPerMs = (sampleRate / 1000.0).coerceAtLeast(1.0)

    private val targetFrames = (targetLatencyMs * framesPerMs).toLong().coerceAtLeast((20 * framesPerMs).toLong())
    private val highFrames = targetFrames + (targetFrames / 2).coerceAtLeast((60 * framesPerMs).toLong())
    private val panicFrames = targetFrames + (400 * framesPerMs).toLong()

    // Uno scarto ogni tanto e' impercettibile, una raffica no: la correzione fine la
    // fa il playback rate, il drop interviene solo se l'arretrato resta alto.
    private val minDropIntervalMs = 250L

    private var framesWritten = 0L
    private var lastHeadRaw = 0
    private var headWraps = 0L
    private var baseHead = -1L

    private var currentRate = sampleRate
    private var avgBufferedFrames = targetFrames.toDouble()
    private var lastResyncAt = 0L
    private var lastDropAt = 0L
    private var lastLogAt = 0L

    private fun playedFrames(): Long {
        val raw = track.playbackHeadPosition
        if (raw < lastHeadRaw && lastHeadRaw - raw > Int.MAX_VALUE / 2) headWraps++
        lastHeadRaw = raw
        val abs = (raw.toLong() and 0xFFFFFFFFL) + (headWraps shl 32)
        if (baseHead < 0L) baseHead = abs
        return abs - baseHead
    }

    fun bufferedFrames(): Long = (framesWritten - playedFrames()).coerceAtLeast(0L)

    fun bufferedMs(): Int = (bufferedFrames() / framesPerMs).toInt()

    fun noteWritten(bytes: Int) {
        if (bytes > 0 && frameSize > 0) framesWritten += bytes / frameSize
    }

    fun noteReset() {
        framesWritten = playedFrames()
        avgBufferedFrames = targetFrames.toDouble()
    }

    fun shouldDrop(incomingBytes: Int): Boolean {
        if (frameSize <= 0 || incomingBytes <= 0) return false
        val incoming = incomingBytes / frameSize
        if (avgBufferedFrames + incoming <= highFrames) return false
        if (bufferedFrames() + incoming <= highFrames) return false
        val now = System.currentTimeMillis()
        if (now - lastDropAt < minDropIntervalMs) return false
        lastDropAt = now
        // L'arretrato scartato va tolto subito dalla media, altrimenti la media
        // resta alta e comanda altri scarti che non servono piu'.
        avgBufferedFrames = (avgBufferedFrames - incoming).coerceAtLeast(0.0)
        return true
    }

    fun hardResyncIfNeeded(): Boolean {
        val buffered = bufferedFrames()
        if (buffered <= panicFrames || avgBufferedFrames <= panicFrames) return false
        val now = System.currentTimeMillis()
        if (now - lastResyncAt < 5000L) return false
        lastResyncAt = now
        runCatching {
            track.pause()
            track.flush()
            track.play()
        }
        noteReset()
        Log.w(tag, "[PLAYOUT] resync: buffered ${(buffered / framesPerMs).toInt()}ms over budget, buffer flushed")
        return true
    }

    fun retune() {
        val buffered = bufferedFrames().toDouble()
        avgBufferedFrames = avgBufferedFrames * 0.9 + buffered * 0.1
        val errFrames = avgBufferedFrames - targetFrames
        val errRatio = errFrames / targetFrames.toDouble()
        val factor = (1.0 + errRatio * 0.05).coerceIn(0.995, 1.005)
        val newRate = (sampleRate * factor).toInt().coerceAtLeast(1)
        if (kotlin.math.abs(newRate - currentRate) >= 4) {
            runCatching { track.setPlaybackRate(newRate) }
            currentRate = newRate
        }
        val now = System.currentTimeMillis()
        if (now - lastLogAt > 10_000L) {
            lastLogAt = now
            Log.d(tag, "[PLAYOUT] buffered=${(buffered / framesPerMs).toInt()}ms target=${(targetFrames / framesPerMs).toInt()}ms rate=$newRate")
        }
    }
}
