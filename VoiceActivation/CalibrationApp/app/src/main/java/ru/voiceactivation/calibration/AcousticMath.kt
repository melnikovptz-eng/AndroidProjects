package ru.voiceactivation.calibration

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

object AcousticMath {
    fun rms(samples: ShortArray, count: Int): Double {
        if (count <= 0) return 0.0
        var sum = 0.0
        for (i in 0 until count) {
            val v = samples[i].toDouble() / 32768.0
            sum += v * v
        }
        return sqrt(sum / count)
    }

    fun peak(samples: ShortArray, count: Int): Double {
        var peak = 0.0
        for (i in 0 until count) {
            peak = max(peak, kotlin.math.abs(samples[i].toDouble() / 32768.0))
        }
        return peak
    }

    fun dbfs(amplitude: Double): Float =
        (20.0 * ln(max(amplitude, 1e-7)) / ln(10.0)).toFloat().coerceIn(-140f, 0f)

    fun snrDb(signalDbfs: Float, noiseDbfs: Float): Float =
        (signalDbfs - noiseDbfs).coerceIn(-100f, 100f)
}

class NoiseEstimator(initialDbfs: Float = -65f) {
    var levelDbfs: Float = initialDbfs
        private set

    fun update(frameDbfs: Float, adaptation: Float, isSpeech: Boolean) {
        if (!isSpeech && frameDbfs.isFinite()) {
            val a = adaptation.coerceIn(0.001f, 0.5f)
            levelDbfs += a * (frameDbfs - levelDbfs)
            levelDbfs = levelDbfs.coerceIn(-120f, -5f)
        }
    }

    fun reset(value: Float = -65f) { levelDbfs = value }
}

data class SegmentEvent(
    val active: Boolean,
    val started: Boolean,
    val ended: Boolean,
    val thresholdDbfs: Float,
    val segmentDurationMs: Long
)

class SpeechSegmenter {
    private var aboveMs = 0L
    private var speechMs = 0L
    private var silenceMs = 0L
    private var cooldownLeftMs = 0L
    private var active = false

    fun process(
        levelDbfs: Float,
        thresholdDbfs: Float,
        config: CalibrationConfig,
        frameMs: Long
    ): SegmentEvent {
        val c = config.normalized()
        cooldownLeftMs = (cooldownLeftMs - frameMs).coerceAtLeast(0)
        val startThreshold = thresholdDbfs
        val stopThreshold = thresholdDbfs - c.hysteresisDb

        if (!active) {
            if (levelDbfs >= startThreshold && cooldownLeftMs == 0L) aboveMs += frameMs
            else aboveMs = 0L

            if (aboveMs >= c.minTriggerMs) {
                active = true
                speechMs = aboveMs
                silenceMs = 0L
                aboveMs = 0L
                return SegmentEvent(true, true, false, thresholdDbfs, speechMs)
            }
            return SegmentEvent(false, false, false, thresholdDbfs, 0)
        }

        speechMs += frameMs
        if (levelDbfs < stopThreshold) silenceMs += frameMs else silenceMs = 0L

        val ended = silenceMs >= c.silenceEndMs || speechMs >= c.maxSegmentMs
        if (ended) {
            val duration = speechMs
            active = false
            speechMs = 0L
            silenceMs = 0L
            cooldownLeftMs = c.cooldownMs.toLong()
            return SegmentEvent(false, false, true, thresholdDbfs, duration)
        }
        return SegmentEvent(true, false, false, thresholdDbfs, speechMs)
    }

    fun reset() {
        aboveMs = 0L
        speechMs = 0L
        silenceMs = 0L
        cooldownLeftMs = 0L
        active = false
    }
}
