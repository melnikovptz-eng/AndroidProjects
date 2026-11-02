package ru.voiceactivation.calibration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechSegmenterTest {
    @Test fun opensAfterConfiguredContinuousThresholdTimeAndClosesAfterSilence() {
        val segmenter = SpeechSegmenter()
        val config = CalibrationConfig(minTriggerMs = 60, silenceEndMs = 40, hysteresisDb = 3f)
        assertFalse(segmenter.process(-20f, -40f, config, 20).started)
        assertFalse(segmenter.process(-20f, -40f, config, 20).started)
        assertTrue(segmenter.process(-20f, -40f, config, 20).started)
        repeat(4) { assertFalse(segmenter.process(-80f, -40f, config, 20).ended) }
        assertTrue(segmenter.process(-80f, -40f, config, 20).ended)
    }

    @Test fun shortTransientDoesNotOpenSegment() {
        val segmenter = SpeechSegmenter()
        val config = CalibrationConfig(minTriggerMs = 100)
        assertFalse(segmenter.process(-10f, -40f, config, 20).started)
        assertFalse(segmenter.process(-80f, -40f, config, 20).started)
        assertFalse(segmenter.process(-80f, -40f, config, 20).active)
    }
}
