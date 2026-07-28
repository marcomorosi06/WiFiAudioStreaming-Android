package com.cuscus.wifiaudiostreaming

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

object LinkMetrics {

    private const val TAG = "WFAS-METRICS"
    private const val REPORT_INTERVAL_MS = 5000L

    data class Snapshot(
        val transport: String = "-",
        val packets: Long = 0,
        val lost: Long = 0,
        val reordered: Long = 0,
        val jitterMs: Double = 0.0,
        val peakJitterMs: Double = 0.0,
        val meanIntervalMs: Double = 0.0
    ) {
        val lossPercent: Double
            get() {
                val expected = packets + lost
                return if (expected <= 0) 0.0 else lost * 100.0 / expected
            }

        fun format(): String = String.format(
            "%s pkts=%d loss=%.2f%% reorder=%d jitter=%.2fms peak=%.2fms interval=%.2fms",
            transport, packets, lossPercent, reordered, jitterMs, peakJitterMs, meanIntervalMs
        )
    }

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot

    @Volatile private var enabled = false
    @Volatile private var transport = "-"
    @Volatile private var sampleRate = 48000

    private var packets = 0L
    private var lost = 0L
    private var reordered = 0L
    private var jitter = 0.0
    private var peakJitter = 0.0
    private var lastSeq = -1
    private var lastTransitMs = Double.NaN
    private var lastArrivalNs = 0L
    private var intervalSumMs = 0.0
    private var intervalCount = 0L
    private var lastReportMs = 0L

    @Synchronized
    fun start(transportLabel: String, rate: Int) {
        enabled = true
        transport = transportLabel
        sampleRate = if (rate > 0) rate else 48000
        packets = 0; lost = 0; reordered = 0
        jitter = 0.0; peakJitter = 0.0
        lastSeq = -1
        lastTransitMs = Double.NaN
        lastArrivalNs = 0L
        intervalSumMs = 0.0
        intervalCount = 0L
        lastReportMs = System.currentTimeMillis()
        _snapshot.value = Snapshot(transport = transportLabel)
    }

    @Synchronized
    fun stop() {
        if (!enabled) return
        enabled = false
        publish()
        Log.i(TAG, "final ${_snapshot.value.format()}")
    }

    @Synchronized
    fun onPacket(seq: Int, samplePos: Long) {
        if (!enabled) return
        val nowNs = System.nanoTime()
        packets++

        if (lastSeq < 0) {
            lastSeq = seq
        } else {
            val delta = ((seq - lastSeq) and 0xFFFF)
            when {
                delta == 0 -> Unit
                delta <= 32768 -> {
                    if (delta > 1) lost += (delta - 1).toLong()
                    lastSeq = seq
                }
                else -> {
                    reordered++
                    if (lost > 0) lost--
                }
            }
        }

        if (lastArrivalNs != 0L) {
            val intervalMs = (nowNs - lastArrivalNs) / 1_000_000.0
            if (intervalMs in 0.0..1000.0) {
                intervalSumMs += intervalMs
                intervalCount++
            }
        }
        lastArrivalNs = nowNs

        if (samplePos >= 0) {
            val mediaMs = samplePos * 1000.0 / sampleRate
            val arrivalMs = nowNs / 1_000_000.0
            val transitMs = arrivalMs - mediaMs
            if (!lastTransitMs.isNaN()) {
                val d = abs(transitMs - lastTransitMs)
                if (d < 5000.0) {
                    jitter += (d - jitter) / 16.0
                    if (jitter > peakJitter) peakJitter = jitter
                }
            }
            lastTransitMs = transitMs
        }

        val nowMs = System.currentTimeMillis()
        if (nowMs - lastReportMs >= REPORT_INTERVAL_MS) {
            lastReportMs = nowMs
            publish()
            Log.i(TAG, _snapshot.value.format())
        }
    }

    private fun publish() {
        _snapshot.value = Snapshot(
            transport = transport,
            packets = packets,
            lost = lost,
            reordered = reordered,
            jitterMs = jitter,
            peakJitterMs = peakJitter,
            meanIntervalMs = if (intervalCount == 0L) 0.0 else intervalSumMs / intervalCount
        )
    }
}
