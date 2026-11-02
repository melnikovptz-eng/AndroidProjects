package ru.voiceactivation.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcousticMathTest {
    @Test fun halfScaleSignalIsAboutMinusSixDbfs() {
        val samples = ShortArray(320) { 16384 }
        assertEquals(-6.02, AcousticMath.dbfs(AcousticMath.rms(samples, samples.size)).toDouble(), 0.05)
    }

    @Test fun peakIsNotLowerThanRmsForNonzeroSignal() {
        val samples = shortArrayOf(100, -200, 300, -400)
        val rms = AcousticMath.dbfs(AcousticMath.rms(samples, samples.size))
        val peak = AcousticMath.dbfs(AcousticMath.peak(samples, samples.size))
        assertTrue(peak >= rms)
    }

    @Test fun noiseEstimatorDoesNotLearnFromSpeechFrames() {
        val estimator = NoiseEstimator(-60f)
        estimator.update(-20f, 0.2f, true)
        assertEquals(-60f, estimator.levelDbfs, 0.001f)
        estimator.update(-50f, 0.2f, false)
        assertTrue(estimator.levelDbfs > -60f)
    }
}
