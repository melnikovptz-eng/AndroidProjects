package ru.voiceactivation.next

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationEngineTest {
    @Test
    fun fitsCommandAndOwnerThresholdsOnHeldOutSamples() {
        val samples = mutableListOf<CalibrationEngine.Sample>()
        repeat(10) {
            samples += CalibrationEngine.Sample("START", true, "START", 1.0, 0.50, 0.95)
            samples += CalibrationEngine.Sample("STOP", true, "STOP", 1.2, 0.55, 0.94)
        }
        repeat(10) {
            samples += CalibrationEngine.Sample(null, true, "START", 9.0, 0.95, 0.93)
            samples += CalibrationEngine.Sample(null, false, "STOP", 8.5, 0.96, 0.40)
        }

        val result = CalibrationEngine.fit(samples)

        assertTrue(result.message, result.success)
        assertTrue(result.dtwThreshold > 1.2)
        assertTrue(result.dtwThreshold < 8.5)
        assertTrue(result.ownerThreshold > 0.40)
        assertTrue(result.ownerThreshold < 0.94)
        assertTrue(result.commandValidation > 0.9)
        assertTrue(result.ownerValidation > 0.9)
    }

    @Test
    fun requiresOwnerSpokenUnknownPhrasesSeparatelyFromOtherSpeakers() {
        val samples = mutableListOf<CalibrationEngine.Sample>()
        repeat(10) {
            samples += CalibrationEngine.Sample("START", true, "START", 1.0, 0.5, 0.95)
            samples += CalibrationEngine.Sample(null, false, "START", 9.0, 0.95, 0.4)
        }

        val result = CalibrationEngine.fit(samples)

        assertFalse(result.success)
        assertTrue(result.message.contains("посторонних фраз"))
    }

    @Test
    fun reportsPerCommandSampleCounts() {
        val summary = CalibrationEngine.summary(
            listOf(CalibrationEngine.Sample("START", true, "START", 1.0, 0.5, 0.95))
        )
        assertTrue(summary.contains("START: 1"))
        assertTrue(summary.contains("Посторонняя речь владельца"))
    }
}

class VadCalibrationEngineTest {
    @Test
    fun fitsNoiseSpeechAndTimingCoefficientsWithIndependentSessions() {
        val noise = List(200) { VadCalibrationEngine.Frame(0.001, 0.003) }
        val phrasePattern = buildList {
            repeat(40) { add(VadCalibrationEngine.Frame(0.020, 0.050)) }
            repeat(10) { add(VadCalibrationEngine.Frame(0.001, 0.003)) }
            repeat(40) { add(VadCalibrationEngine.Frame(0.020, 0.050)) }
            repeat(20) { add(VadCalibrationEngine.Frame(0.001, 0.003)) }
            repeat(40) { add(VadCalibrationEngine.Frame(0.020, 0.050)) }
        }

        val sessions = listOf(
            VadCalibrationEngine.Session(false, 1L, noise),
            VadCalibrationEngine.Session(false, 2L, noise),
            VadCalibrationEngine.Session(true, 3L, phrasePattern),
            VadCalibrationEngine.Session(true, 4L, phrasePattern)
        )

        val result = VadCalibrationEngine.fit(sessions)

        assertTrue(result.message, result.success)
        assertEquals(0.001, result.noiseRms, 0.000001)
        assertTrue(result.rmsThreshold > 0.001)
        assertTrue(result.peakThreshold > 0.003)
        assertEquals(300, result.silenceMs)
        assertEquals(500, result.minSpeechMs)
        assertEquals(3000, result.maxSpeechMs)
        assertTrue(result.validationAccuracy > 0.99)
    }

    @Test
    fun refusesVADFitWithoutIndependentNoiseAndSpeechSessions() {
        val oneNoise = VadCalibrationEngine.Session(
            false, 1L, List(200) { VadCalibrationEngine.Frame(0.001, 0.003) }
        )
        val result = VadCalibrationEngine.fit(listOf(oneNoise))
        assertFalse(result.success)
        assertTrue(result.message.contains("минимум"))
    }
}
