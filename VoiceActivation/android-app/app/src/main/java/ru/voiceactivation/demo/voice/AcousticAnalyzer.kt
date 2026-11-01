package ru.voiceactivation.demo.voice

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Adaptive acoustic front-end for mono float PCM.
 *
 * Estimates the local noise floor during non-speech frames, uses hysteresis
 * for voice activity detection, and reports an approximate signal-to-noise
 * ratio. This is a level/VAD analyzer, not a neural denoiser.
 */
class AcousticAnalyzer(
    private val minimumSpeechRms: Float = 0.0035f,
    private val speechToNoiseRatio: Float = 2.5f,
    private val releaseToNoiseRatio: Float = 1.7f,
) {
    private var noiseFloorRms = 0.0025f
    private var speechActive = false
    private var calibrationFramesRemaining = CALIBRATION_FRAMES

    @Synchronized
    fun analyze(samples: FloatArray): Frame {
        require(samples.isNotEmpty()) { "Audio frame is empty" }

        var squareSum = 0.0
        var peak = 0f
        for (sample in samples) {
            val value = sample.coerceIn(-1f, 1f)
            squareSum += value * value
            peak = maxOf(peak, kotlin.math.abs(value))
        }
        val rms = sqrt(squareSum / samples.size).toFloat()
        // Calibrate only on frames that look like background noise. Do not
        // suppress speech during startup: the first spoken command must be kept.
        if (calibrationFramesRemaining > 0) {
            val initialNoise = noiseFloorRms.coerceAtLeast(MIN_NOISE_RMS)
            val startupThreshold = maxOf(minimumSpeechRms, initialNoise * speechToNoiseRatio)
            speechActive = rms >= startupThreshold
            if (!speechActive) {
                noiseFloorRms = (noiseFloorRms + CALIBRATION_ALPHA * (rms - noiseFloorRms))
                    .coerceIn(MIN_NOISE_RMS, MAX_NOISE_RMS)
                calibrationFramesRemaining--
            }
            val calibratedNoise = noiseFloorRms.coerceAtLeast(MIN_NOISE_RMS)
            val startupSnr = 20f * log10((rms / calibratedNoise).coerceAtLeast(1e-6f))
            return Frame(
                rms = rms,
                peak = peak,
                rmsDbfs = toDbfs(rms),
                noiseFloorDbfs = toDbfs(calibratedNoise),
                snrDb = startupSnr,
                speechDetected = speechActive,
                quality = if (!speechActive) Quality.NO_SPEECH else when {
                    startupSnr >= GOOD_SNR_DB -> Quality.GOOD
                    startupSnr >= USABLE_SNR_DB -> Quality.WEAK
                    else -> Quality.POOR
                },
            )
        }

        val noiseBefore = noiseFloorRms.coerceAtLeast(MIN_NOISE_RMS)

        val speechOnThreshold = maxOf(minimumSpeechRms, noiseBefore * speechToNoiseRatio)
        val speechOffThreshold = maxOf(minimumSpeechRms * 0.65f, noiseBefore * releaseToNoiseRatio)

        speechActive = if (speechActive) {
            rms >= speechOffThreshold
        } else {
            rms >= speechOnThreshold
        }

        // Adapt only on frames currently classified as non-speech. Faster
        // upward tracking handles a changing noisy environment; downward
        // tracking is slower to avoid mistaking pauses for a quieter room.
        if (!speechActive) {
            val alpha = if (rms > noiseFloorRms) 0.08f else 0.015f
            noiseFloorRms = (noiseFloorRms + alpha * (rms - noiseFloorRms))
                .coerceIn(MIN_NOISE_RMS, MAX_NOISE_RMS)
        }

        val noise = noiseFloorRms.coerceAtLeast(MIN_NOISE_RMS)
        val snrDb = 20f * log10((rms / noise).coerceAtLeast(1e-6f))
        return Frame(
            rms = rms,
            peak = peak,
            rmsDbfs = toDbfs(rms),
            noiseFloorDbfs = toDbfs(noise),
            snrDb = snrDb,
            speechDetected = speechActive,
            quality = when {
                !speechActive -> Quality.NO_SPEECH
                snrDb >= GOOD_SNR_DB -> Quality.GOOD
                snrDb >= USABLE_SNR_DB -> Quality.WEAK
                else -> Quality.POOR
            },
        )
    }

    @Synchronized
    fun reset() {
        noiseFloorRms = 0.0025f
        speechActive = false
        calibrationFramesRemaining = CALIBRATION_FRAMES
    }

    data class Frame(
        val rms: Float,
        val peak: Float,
        val rmsDbfs: Float,
        val noiseFloorDbfs: Float,
        val snrDb: Float,
        val speechDetected: Boolean,
        val quality: Quality,
    )

    enum class Quality { NO_SPEECH, POOR, WEAK, GOOD }

    private fun toDbfs(value: Float): Float =
        20f * log10(value.coerceAtLeast(1e-6f))

    companion object {
        const val GOOD_SNR_DB = 12f
        const val USABLE_SNR_DB = 6f
        private const val MIN_NOISE_RMS = 0.0005f
        private const val MAX_NOISE_RMS = 0.15f
        private const val CALIBRATION_FRAMES = 15
        private const val CALIBRATION_ALPHA = 0.35f
    }
}
