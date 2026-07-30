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

class CaptionTimeline(
    private val sampleRate: Int,
    private val maxPending: Int = 32,
    private val defaultHoldMs: Int = 4000,
    private val minHoldMs: Int = 1500,
    private val maxHoldMs: Int = 8000
) {

    private class Entry(
        val capId: Long,
        var rev: Int,
        val samplePos: Long,
        var durMs: Int,
        var text: String,
        var isFinal: Boolean,
        var isClear: Boolean
    )

    private val pending = ArrayDeque<Entry>()
    private var shown: Entry? = null
    private var shownAt: Long = 0L

    @Synchronized
    fun submit(c: WfasCaptions.Caption) {
        val existing = pending.firstOrNull { it.capId == c.capId }
        if (existing != null) {
            if (c.rev < existing.rev) return
            existing.rev = c.rev
            existing.text = c.text
            existing.durMs = c.durMs
            existing.isFinal = c.isFinal
            existing.isClear = c.isClear
            return
        }
        val current = shown
        if (current != null && current.capId == c.capId) {
            if (c.rev < current.rev) return
            current.rev = c.rev
            current.text = c.text
            current.durMs = c.durMs
            current.isFinal = c.isFinal
            current.isClear = c.isClear
            return
        }
        pending.addLast(Entry(c.capId, c.rev, c.samplePos, c.durMs, c.text, c.isFinal, c.isClear))
        while (pending.size > maxPending) pending.removeFirst()
    }

    @Synchronized
    fun textAt(playbackSamplePos: Long): String {
        while (true) {
            val head = pending.firstOrNull() ?: break
            if (WfasCaptions.sampleDelta(playbackSamplePos, head.samplePos) < 0L) break
            pending.removeFirst()
            if (head.isClear) {
                shown = null
                shownAt = 0L
            } else {
                shown = head
                shownAt = playbackSamplePos
            }
        }
        val current = shown ?: return ""
        val hold = (if (current.durMs > 0) current.durMs else defaultHoldMs)
            .coerceIn(minHoldMs, maxHoldMs)
        val elapsedSamples = WfasCaptions.sampleDelta(playbackSamplePos, shownAt)
        val holdSamples = hold.toLong() * sampleRate / 1000L
        if (elapsedSamples > holdSamples) {
            shown = null
            shownAt = 0L
            return ""
        }
        return current.text
    }

    @Synchronized
    fun reset() {
        pending.clear()
        shown = null
        shownAt = 0L
    }

    @Synchronized
    fun pendingCount(): Int = pending.size
}
