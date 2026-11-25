package com.aistudio.aimusic.wltv.jqr8x.data.media

import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioMasteringSettings
import com.aistudio.aimusic.wltv.jqr8x.data.model.AudioTrackRole
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Pure on-device stereo float PCM mastering & mixing DSP processor.
 *
 * Implements:
 * - Vocal vs Instrumental track gain staging
 * - Sub-rumble high-pass filter + smooth RMS noise-floor reduction gate
 * - 3-band biquad EQ (Low Shelf 120 Hz, Mid Presence 1.8 kHz, High Air Shelf 7.5 kHz)
 * - Soft-knee dynamic range compressor + makeup gain
 * - Schroeder stereo sanctuary reverb (4 damped comb filters + 2 allpass diffusers)
 * - Transparent tanh peak safety limiter (-0.8 dBFS ceiling)
 */
object AudioDspProcessor {

    fun computeRoleGainMultiplier(role: AudioTrackRole, vocalBalanceOffset: Float): Float {
        val offset = vocalBalanceOffset.coerceIn(-1f, 1f)
        if (abs(offset) < 0.01f || role == AudioTrackRole.GENERAL) return 1.0f
        return when (role) {
            AudioTrackRole.VOCAL -> {
                if (offset > 0f) 1.0f + (0.55f * offset) else (1.0f + 0.55f * offset).coerceAtLeast(0.35f)
            }
            AudioTrackRole.INSTRUMENTAL -> {
                if (offset < 0f) 1.0f + (0.55f * (-offset)) else (1.0f - 0.55f * offset).coerceAtLeast(0.35f)
            }
            AudioTrackRole.GENERAL -> 1.0f
        }
    }

    fun applyMasteringChainInPlace(
        interleavedStereo: FloatArray,
        sampleRate: Int,
        settings: AudioMasteringSettings
    ) {
        if (interleavedStereo.size < 2 || sampleRate <= 0) return

        val totalFrames = interleavedStereo.size / 2
        if (settings.enabled) {
            // 1. Sub-rumble high-pass + adaptive noise gate when noise reduction is enabled
            if (settings.noiseReductionEnabled && settings.noiseReductionAmount > 0.01f) {
                applyHighPassFilterInPlace(interleavedStereo, totalFrames, sampleRate, cutoffHz = 75.0)
                applyAdaptiveNoiseGateInPlace(
                    interleavedStereo,
                    totalFrames,
                    sampleRate,
                    amount = settings.noiseReductionAmount.coerceIn(0f, 1f)
                )
            }

            // 2. 3-Band Biquad EQ
            if (abs(settings.eqLowGainDb) > 0.1f) {
                val coeffs = BiquadCoeffs.lowShelf(
                    sampleRate = sampleRate.toDouble(),
                    freqHz = 120.0,
                    gainDb = settings.eqLowGainDb.coerceIn(-9f, 9f).toDouble(),
                    slope = 0.85
                )
                applyBiquadInPlace(interleavedStereo, totalFrames, coeffs)
            }
            if (abs(settings.eqMidGainDb) > 0.1f) {
                val coeffs = BiquadCoeffs.peakingEq(
                    sampleRate = sampleRate.toDouble(),
                    freqHz = 1800.0,
                    gainDb = settings.eqMidGainDb.coerceIn(-9f, 9f).toDouble(),
                    q = 0.95
                )
                applyBiquadInPlace(interleavedStereo, totalFrames, coeffs)
            }
            if (abs(settings.eqHighGainDb) > 0.1f) {
                val coeffs = BiquadCoeffs.highShelf(
                    sampleRate = sampleRate.toDouble(),
                    freqHz = 7500.0,
                    gainDb = settings.eqHighGainDb.coerceIn(-9f, 9f).toDouble(),
                    slope = 0.85
                )
                applyBiquadInPlace(interleavedStereo, totalFrames, coeffs)
            }

            // 3. Dynamic Range Compressor
            if (settings.compressorEnabled && settings.compressorAmount > 0.01f) {
                applyCompressorInPlace(
                    interleavedStereo = interleavedStereo,
                    totalFrames = totalFrames,
                    sampleRate = sampleRate,
                    amount = settings.compressorAmount.coerceIn(0f, 1f)
                )
            }

            // 4. Schroeder Stereo Sanctuary Reverb
            if (settings.reverbEnabled && settings.reverbMix > 0.01f) {
                applyStereoReverbInPlace(
                    interleavedStereo = interleavedStereo,
                    totalFrames = totalFrames,
                    sampleRate = sampleRate,
                    wetMix = settings.reverbMix.coerceIn(0f, 0.6f)
                )
            }
        }

        // 5. Master output trim + transparent safety peak limiter
        val masterGainLinear = 10.0.pow(settings.masterGainDb.coerceIn(-6f, 6f).toDouble() / 20.0).toFloat()
        for (i in 0 until totalFrames * 2) {
            val scaled = interleavedStereo[i] * masterGainLinear
            interleavedStereo[i] = if (abs(scaled) > 0.90f) {
                tanh(scaled.toDouble()).toFloat() * 0.95f
            } else {
                scaled
            }
        }
    }

    private fun applyHighPassFilterInPlace(
        buffer: FloatArray,
        totalFrames: Int,
        sampleRate: Int,
        cutoffHz: Double
    ) {
        val coeffs = BiquadCoeffs.highPass(sampleRate.toDouble(), cutoffHz, q = 0.7071)
        applyBiquadInPlace(buffer, totalFrames, coeffs)
    }

    private fun applyAdaptiveNoiseGateInPlace(
        buffer: FloatArray,
        totalFrames: Int,
        sampleRate: Int,
        amount: Float
    ) {
        // Threshold glides smoothly between -48 dBFS (light) and -30 dBFS (strong)
        val thresholdDb = -48.0 + (18.0 * amount)
        val thresholdLinear = 10.0.pow(thresholdDb / 20.0).toFloat()
        val floorGain = (1.0f - 0.88f * amount).coerceIn(0.08f, 1.0f)

        val attackCoeff = exp(-1.0 / (0.005 * sampleRate)).toFloat()
        val releaseCoeff = exp(-1.0 / (0.060 * sampleRate)).toFloat()

        var envelope = 0f
        var gain = 1f
        val gainAttack = exp(-1.0 / (0.004 * sampleRate)).toFloat()
        val gainRelease = exp(-1.0 / (0.080 * sampleRate)).toFloat()

        for (f in 0 until totalFrames) {
            val idx = f * 2
            val level = maxOf(abs(buffer[idx]), abs(buffer[idx + 1]))
            envelope = if (level > envelope) {
                attackCoeff * envelope + (1f - attackCoeff) * level
            } else {
                releaseCoeff * envelope + (1f - releaseCoeff) * level
            }
            val targetGain = if (envelope >= thresholdLinear) {
                1f
            } else {
                val ratio = (envelope / thresholdLinear).coerceIn(0f, 1f)
                floorGain + (1f - floorGain) * (ratio * ratio)
            }
            gain = if (targetGain > gain) {
                gainAttack * gain + (1f - gainAttack) * targetGain
            } else {
                gainRelease * gain + (1f - gainRelease) * targetGain
            }
            buffer[idx] *= gain
            buffer[idx + 1] *= gain
        }
    }

    private fun applyCompressorInPlace(
        interleavedStereo: FloatArray,
        totalFrames: Int,
        sampleRate: Int,
        amount: Float
    ) {
        val threshold = (0.35f - 0.20f * amount).coerceIn(0.10f, 0.50f) // ~ -9 dB down to -16 dB
        val ratio = 1.8f + 2.8f * amount
        val makeupGain = 1.0f + 0.35f * amount

        val attack = exp(-1.0 / (0.008 * sampleRate)).toFloat()
        val release = exp(-1.0 / (0.110 * sampleRate)).toFloat()
        var env = 0f

        for (f in 0 until totalFrames) {
            val idx = f * 2
            val l = interleavedStereo[idx]
            val r = interleavedStereo[idx + 1]
            val peak = sqrt((l * l + r * r) * 0.5f).coerceAtLeast(maxOf(abs(l), abs(r)) * 0.7f)
            env = if (peak > env) {
                attack * env + (1f - attack) * peak
            } else {
                release * env + (1f - release) * peak
            }
            val gain = if (env > threshold && env > 1e-6f) {
                val compressed = threshold + (env - threshold) / ratio
                (compressed / env).coerceIn(0.15f, 1f)
            } else {
                1f
            }
            val finalGain = gain * makeupGain
            interleavedStereo[idx] = l * finalGain
            interleavedStereo[idx + 1] = r * finalGain
        }
    }

    private fun applyStereoReverbInPlace(
        interleavedStereo: FloatArray,
        totalFrames: Int,
        sampleRate: Int,
        wetMix: Float
    ) {
        val scale = sampleRate.toDouble() / 44_100.0
        val combDelaysL = intArrayOf(1116, 1188, 1277, 1356).map { (it * scale).toInt().coerceAtLeast(32) }
        val combDelaysR = combDelaysL.map { it + 23 }
        val allpassDelaysL = intArrayOf(556, 441).map { (it * scale).toInt().coerceAtLeast(16) }
        val allpassDelaysR = allpassDelaysL.map { it + 17 }

        val combsL = combDelaysL.map { CombFilter(it, feedback = 0.76f, damp = 0.25f) }
        val combsR = combDelaysR.map { CombFilter(it, feedback = 0.76f, damp = 0.25f) }
        val apL = allpassDelaysL.map { AllpassFilter(it, feedback = 0.5f) }
        val apR = allpassDelaysR.map { AllpassFilter(it, feedback = 0.5f) }

        val dryMix = 1.0f - (wetMix * 0.35f)
        for (f in 0 until totalFrames) {
            val idx = f * 2
            val dryL = interleavedStereo[idx]
            val dryR = interleavedStereo[idx + 1]
            val monoFeed = (dryL + dryR) * 0.28f

            var wetL = 0f
            var wetR = 0f
            for (c in combsL) wetL += c.process(monoFeed)
            for (c in combsR) wetR += c.process(monoFeed)
            for (a in apL) wetL = a.process(wetL)
            for (a in apR) wetR = a.process(wetR)

            interleavedStereo[idx] = dryL * dryMix + (wetL * 0.35f) * wetMix
            interleavedStereo[idx + 1] = dryR * dryMix + (wetR * 0.35f) * wetMix
        }
    }

    private class CombFilter(size: Int, private val feedback: Float, private val damp: Float) {
        private val buffer = FloatArray(size)
        private var index = 0
        private var filterStore = 0f

        fun process(input: Float): Float {
            val output = buffer[index]
            filterStore = output * (1f - damp) + filterStore * damp
            buffer[index] = input + filterStore * feedback
            index = (index + 1) % buffer.size
            return output
        }
    }

    private class AllpassFilter(size: Int, private val feedback: Float) {
        private val buffer = FloatArray(size)
        private var index = 0

        fun process(input: Float): Float {
            val bufOut = buffer[index]
            val output = -input + bufOut
            buffer[index] = input + bufOut * feedback
            index = (index + 1) % buffer.size
            return output
        }
    }

    private data class BiquadCoeffs(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a1: Double,
        val a2: Double
    ) {
        companion object {
            fun lowShelf(sampleRate: Double, freqHz: Double, gainDb: Double, slope: Double): BiquadCoeffs {
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2.0 * PI * freqHz / sampleRate
                val cosW = cos(w0)
                val sinW = sin(w0)
                val alpha = sinW / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
                val beta = 2.0 * sqrt(a) * alpha

                val b0 = a * ((a + 1) - (a - 1) * cosW + beta)
                val b1 = 2.0 * a * ((a - 1) - (a + 1) * cosW)
                val b2 = a * ((a + 1) - (a - 1) * cosW - beta)
                val a0 = (a + 1) + (a - 1) * cosW + beta
                val a1 = -2.0 * ((a - 1) + (a + 1) * cosW)
                val a2 = (a + 1) + (a - 1) * cosW - beta
                return BiquadCoeffs(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }

            fun highShelf(sampleRate: Double, freqHz: Double, gainDb: Double, slope: Double): BiquadCoeffs {
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2.0 * PI * freqHz / sampleRate
                val cosW = cos(w0)
                val sinW = sin(w0)
                val alpha = sinW / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
                val beta = 2.0 * sqrt(a) * alpha

                val b0 = a * ((a + 1) + (a - 1) * cosW + beta)
                val b1 = -2.0 * a * ((a - 1) + (a + 1) * cosW)
                val b2 = a * ((a + 1) + (a - 1) * cosW - beta)
                val a0 = (a + 1) - (a - 1) * cosW + beta
                val a1 = 2.0 * ((a - 1) - (a + 1) * cosW)
                val a2 = (a + 1) - (a - 1) * cosW - beta
                return BiquadCoeffs(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }

            fun peakingEq(sampleRate: Double, freqHz: Double, gainDb: Double, q: Double): BiquadCoeffs {
                val a = 10.0.pow(gainDb / 40.0)
                val w0 = 2.0 * PI * freqHz / sampleRate
                val cosW = cos(w0)
                val alpha = sin(w0) / (2.0 * q)

                val b0 = 1.0 + alpha * a
                val b1 = -2.0 * cosW
                val b2 = 1.0 - alpha * a
                val a0 = 1.0 + alpha / a
                val a1 = -2.0 * cosW
                val a2 = 1.0 - alpha / a
                return BiquadCoeffs(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }

            fun highPass(sampleRate: Double, freqHz: Double, q: Double): BiquadCoeffs {
                val w0 = 2.0 * PI * freqHz / sampleRate
                val cosW = cos(w0)
                val alpha = sin(w0) / (2.0 * q)

                val b0 = (1.0 + cosW) / 2.0
                val b1 = -(1.0 + cosW)
                val b2 = (1.0 + cosW) / 2.0
                val a0 = 1.0 + alpha
                val a1 = -2.0 * cosW
                val a2 = 1.0 - alpha
                return BiquadCoeffs(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
            }
        }
    }

    private fun applyBiquadInPlace(
        buffer: FloatArray,
        totalFrames: Int,
        c: BiquadCoeffs
    ) {
        var x1L = 0.0
        var x2L = 0.0
        var y1L = 0.0
        var y2L = 0.0
        var x1R = 0.0
        var x2R = 0.0
        var y1R = 0.0
        var y2R = 0.0

        for (f in 0 until totalFrames) {
            val idx = f * 2
            val inL = buffer[idx].toDouble()
            val outL = c.b0 * inL + c.b1 * x1L + c.b2 * x2L - c.a1 * y1L - c.a2 * y2L
            x2L = x1L
            x1L = inL
            y2L = y1L
            y1L = outL

            val inR = buffer[idx + 1].toDouble()
            val outR = c.b0 * inR + c.b1 * x1R + c.b2 * x2R - c.a1 * y1R - c.a2 * y2R
            x2R = x1R
            x1R = inR
            y2R = y1R
            y1R = outR

            buffer[idx] = outL.toFloat()
            buffer[idx + 1] = outR.toFloat()
        }
    }
}
