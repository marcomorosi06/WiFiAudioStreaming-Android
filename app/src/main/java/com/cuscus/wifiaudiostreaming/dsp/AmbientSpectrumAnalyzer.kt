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

package com.cuscus.wifiaudiostreaming.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lightweight ambient-spectrum analyzer for the Android UI background effect.
 *
 * Designed to run on the audio I/O thread with minimal overhead:
 * - Single lock protects only the ring-buffer write path
 * - 512-point mini-FFT (Cooley-Tukey, in-place)
 * - 24 log-spaced bands, RMS smoothed with attack/release
 *
 * Thread safety: [feedFrame] may be called from any thread; [snapshot] and
 * [reset] should be called from the same coroutine/thread (typically the
 * Compose frame clock or a dedicated 30-fps ticker).
 */
object AmbientSpectrumAnalyzer {

    // ── DSP constants ────────────────────────────────────────────────────────
    private const val FFT_SIZE   = 512
    private const val F_MIN      = 40.0
    private const val FLOOR_DB   = -64.0
    private const val CEIL_DB    = -9.0
    private const val TILT_DB    = 5.0
    private const val ATTACK     = 0.50f
    private const val RELEASE    = 0.14f
    private const val METER_FLOOR = -52.0
    const val  NUM_BARS          = 24

    // ── FFT twiddle factors (precomputed once) ────────────────────────────
    private val cosT   = DoubleArray(FFT_SIZE / 2)
    private val sinT   = DoubleArray(FFT_SIZE / 2)
    private val bitRev = IntArray(FFT_SIZE)
    private val window = DoubleArray(FFT_SIZE) { 0.5 - 0.5 * cos(2.0 * PI * it / (FFT_SIZE - 1)) }

    // ── Frequency-band bin ranges ─────────────────────────────────────────
    private val binLo = IntArray(NUM_BARS)
    private val binHi = IntArray(NUM_BARS)

    // ── Working buffers (not shared across calls, only used in snapshot) ──
    private val fftRe  = DoubleArray(FFT_SIZE)
    private val fftIm  = DoubleArray(FFT_SIZE)

    // ── Ring buffer (written from audio thread, read in snapshot) ─────────
    private val lock      = Any()
    private val monoRing  = DoubleArray(FFT_SIZE)
    private var monoWrite = 0
    private var sumSq     = 0.0
    private var frameCount = 0L

    // ── Smoothed output state (only touched inside snapshot) ──────────────
    private val barsSmoothed = FloatArray(NUM_BARS)
    private var levelSmoothed = 0f

    /** True when at least one [feedFrame] call has arrived since last [reset]. */
    @Volatile var isActive = false
        private set

    @Volatile private var configuredRate = 0

    // ── Init ──────────────────────────────────────────────────────────────
    init {
        // Precompute FFT twiddle factors
        for (i in 0 until FFT_SIZE / 2) {
            val a = -2.0 * PI * i / FFT_SIZE
            cosT[i] = cos(a)
            sinT[i] = sin(a)
        }
        // Bit-reversal permutation table
        var j = 0
        for (i in 0 until FFT_SIZE) {
            bitRev[i] = j
            var m = FFT_SIZE shr 1
            while (m in 1..j) { j -= m; m = m shr 1 }
            j += m
        }
        configure(48000)
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Reconfigure band layout for [sampleRate]. Called automatically with 48000 Hz
     * at init or dynamically when [feedFrame] receives stream data at a different sample rate.
     */
    fun configure(sampleRate: Int) {
        val validRate = sampleRate.coerceAtLeast(8000)
        if (validRate == configuredRate) return
        configuredRate = validRate
        val nyq    = validRate / 2.0
        val fMax   = (nyq * 0.92).coerceAtMost(20000.0).coerceAtLeast(F_MIN * 4)
        val lnLo   = ln(F_MIN)
        val lnHi   = ln(fMax)
        val binHz  = validRate.toDouble() / FFT_SIZE
        for (b in 0 until NUM_BARS) {
            val f0 = exp(lnLo + (lnHi - lnLo) * b / NUM_BARS)
            val f1 = exp(lnLo + (lnHi - lnLo) * (b + 1) / NUM_BARS)
            val lo = (f0 / binHz).toInt().coerceIn(1, FFT_SIZE / 2 - 1)
            val hi = (f1 / binHz).toInt().coerceIn(lo, FFT_SIZE / 2 - 1)
            binLo[b] = lo
            binHi[b] = hi
        }
    }

    /**
     * Feed PCM-16LE bytes into the analyzer. Safe to call from any thread.
     *
     * @param buf        byte array containing PCM-16LE samples
     * @param offset     byte offset of the first sample in [buf]
     * @param len        number of bytes to consume (must be even)
     * @param channels   channel count (1 = mono, 2 = stereo – will be downmixed to mono)
     * @param sampleRate stream sample rate in Hz (defaults to 48000 Hz)
     */
    fun feedFrame(buf: ByteArray, offset: Int, len: Int, channels: Int, sampleRate: Int = 48000) {
        if (sampleRate > 0 && sampleRate != configuredRate) {
            configure(sampleRate)
        }
        if (len < 2) return
        val ch = channels.coerceAtLeast(1)
        val samplesPerFrame = ch * 2   // bytes per interleaved frame
        val frames = len / samplesPerFrame

        synchronized(lock) {
            var byteIdx = offset
            repeat(frames) {
                // Downmix to mono (average across channels)
                var acc = 0L
                for (c in 0 until ch) {
                    val lo  = buf[byteIdx].toInt() and 0xFF
                    val hi  = buf[byteIdx + 1].toInt()
                    acc += (hi shl 8) or lo
                    byteIdx += 2
                }
                val mono = (acc / ch).toDouble()
                monoRing[monoWrite] = mono
                monoWrite = (monoWrite + 1) % FFT_SIZE
                sumSq += mono * mono
                frameCount++
            }
            isActive = true
        }
    }

    private const val PEAK_GRAV = 0.008f
    const val GROOVE_MAX       = 1.6f
    private const val GRV_SLOW     = 0.012f
    private const val GRV_RADIUS   = 0.07f
    private const val GRV_CONTRAST = 0.85f
    private const val GRV_WHITEN   = 0.25f
    private const val GRV_GATE_DB  = 8.0f
    private const val GRV_MAX_DEV  = 22.0f
    private const val GRV_RELEASE  = 0.30f

    private val peaksOut = FloatArray(NUM_BARS)
    private val peakVel  = FloatArray(NUM_BARS)
    private val gvDb     = FloatArray(NUM_BARS)
    private val gvSlow   = FloatArray(NUM_BARS)
    private val gvSum    = FloatArray(NUM_BARS + 1)
    private var gvReady  = false

    /**
     * Compute the current spectrum snapshot. Updates [bars] and optional [peaks] in place
     * and returns the smoothed RMS level [0, 1].
     *
     * @param bars output array for bar heights, must be at least [NUM_BARS] long
     * @param peaks optional output array for falling peak caps, must be at least [NUM_BARS] long
     * @param grooveAmount adaptive spectrum contrast & melody tracking amount (0 to 160, default 0)
     * @return smoothed RMS level in [0, 1]
     */
    fun snapshot(bars: FloatArray, peaks: FloatArray? = null, grooveAmount: Int = 0): Float {
        val energy: Double
        val frames: Long
        val start: Int

        synchronized(lock) {
            start      = monoWrite
            frames     = frameCount
            energy     = sumSq
            sumSq      = 0.0
            frameCount = 0L
            for (i in 0 until FFT_SIZE) {
                fftRe[i] = monoRing[(start + i) % FFT_SIZE] * window[i]
                fftIm[i] = 0.0
            }
        }

        fft()

        val gain  = (2.0 / (FFT_SIZE * 0.5)) / 32768.0
        val denom = CEIL_DB - FLOOR_DB
        var loudest = -400.0

        for (b in 0 until NUM_BARS) {
            var peak = 0.0
            var k = binLo[b]
            val e = binHi[b]
            while (k <= e) {
                val re = fftRe[k]; val im = fftIm[k]
                val p  = re * re + im * im
                if (p > peak) peak = p
                k++
            }
            val amp  = sqrt(peak) * gain
            val tilt = TILT_DB * b / (NUM_BARS - 1).coerceAtLeast(1)
            val dbv  = 20.0 * log10(amp + 1e-12) + tilt
            if (dbv > loudest) loudest = dbv
            gvDb[b] = dbv.toFloat()
        }

        val grv = (grooveAmount / 100f).coerceIn(0f, GROOVE_MAX)
        if (grv > 0f) applyGroove(grv, loudest) else gvReady = false

        val release = RELEASE + (GRV_RELEASE - RELEASE) * (grv / GROOVE_MAX).coerceIn(0f, 1f)

        for (b in 0 until NUM_BARS) {
            var v = ((gvDb[b] - FLOOR_DB) / denom).toFloat().coerceIn(0f, 1f)

            // Attack / release smoothing
            val alpha = if (v > barsSmoothed[b]) ATTACK else release
            barsSmoothed[b] += (v - barsSmoothed[b]) * alpha
            if (b < bars.size) bars[b] = barsSmoothed[b]

            // Gravity physics for falling peak caps
            if (barsSmoothed[b] >= peaksOut[b]) {
                peaksOut[b] = barsSmoothed[b]
                peakVel[b]  = 0f
            } else {
                peakVel[b]  += PEAK_GRAV
                peaksOut[b] -= peakVel[b]
                if (peaksOut[b] < barsSmoothed[b]) {
                    peaksOut[b] = barsSmoothed[b]
                    peakVel[b]  = 0f
                }
            }
            if (peaks != null && b < peaks.size) peaks[b] = peaksOut[b]
        }

        // RMS level meter
        val rms = if (frames > 0) sqrt(energy / frames) / 32768.0 else 0.0
        val dbv = 20.0 * log10(rms + 1e-12)
        val lv  = ((dbv - METER_FLOOR) / (-METER_FLOOR)).toFloat().coerceIn(0f, 1f)
        levelSmoothed += (lv - levelSmoothed) * (if (lv > levelSmoothed) 0.50f else 0.18f)

        return levelSmoothed
    }

    private fun applyGroove(amount: Float, loudestDb: Double) {
        val n = NUM_BARS
        if (!gvReady) {
            for (b in 0 until n) gvSlow[b] = gvDb[b]
            gvReady = true
        }
        val gate = ((loudestDb - FLOOR_DB) / GRV_GATE_DB).coerceIn(0.0, 1.0).toFloat()
        if (gate <= 0f) return
        val k = amount * gate
        var slowSum = 0f
        for (b in 0 until n) {
            gvSlow[b] += (gvDb[b] - gvSlow[b]) * GRV_SLOW
            slowSum += gvSlow[b]
        }
        val slowMean = slowSum / n
        gvSum[0] = 0f
        for (b in 0 until n) gvSum[b + 1] = gvSum[b] + gvDb[b]
        val r = (n * GRV_RADIUS).roundToInt().coerceIn(1, 12)
        for (b in 0 until n) {
            val lo = (b - r).coerceAtLeast(0)
            val hi = (b + r + 1).coerceAtMost(n)
            val local = (gvSum[hi] - gvSum[lo]) / (hi - lo)
            val d = gvDb[b]
            var o = d + GRV_CONTRAST * (d - local) - GRV_WHITEN * (gvSlow[b] - slowMean)
            if (o > d + GRV_MAX_DEV) o = d + GRV_MAX_DEV
            else if (o < d - GRV_MAX_DEV) o = d - GRV_MAX_DEV
            gvDb[b] = d + (o - d) * k
        }
    }

    /**
     * Reset all internal state (ring-buffer, smoothed bars, RMS, groove).
     * Call this when the stream stops so the background fades to silence cleanly.
     */
    fun reset() {
        synchronized(lock) {
            monoRing.fill(0.0)
            monoWrite  = 0
            sumSq      = 0.0
            frameCount = 0L
            isActive   = false
        }
        barsSmoothed.fill(0f)
        levelSmoothed = 0f
        gvSlow.fill(0f)
        gvReady = false
    }

    // ── Private FFT ───────────────────────────────────────────────────────

    private fun fft() {
        // Bit-reversal reorder
        for (i in 0 until FFT_SIZE) {
            val j = bitRev[i]
            if (j > i) {
                var t = fftRe[i]; fftRe[i] = fftRe[j]; fftRe[j] = t
                    t = fftIm[i]; fftIm[i] = fftIm[j]; fftIm[j] = t
            }
        }
        // Cooley-Tukey butterfly
        var len = 2
        while (len <= FFT_SIZE) {
            val step = FFT_SIZE / len
            val half = len / 2
            var i = 0
            while (i < FFT_SIZE) {
                var k = 0
                for (jj in 0 until half) {
                    val wr = cosT[k]; val wi = sinT[k]
                    val a  = i + jj;  val b  = a + half
                    val tr = fftRe[b] * wr - fftIm[b] * wi
                    val ti = fftRe[b] * wi + fftIm[b] * wr
                    fftRe[b] = fftRe[a] - tr; fftIm[b] = fftIm[a] - ti
                    fftRe[a] += tr;            fftIm[a] += ti
                    k += step
                }
                i += len
            }
            len = len shl 1
        }
    }
}
