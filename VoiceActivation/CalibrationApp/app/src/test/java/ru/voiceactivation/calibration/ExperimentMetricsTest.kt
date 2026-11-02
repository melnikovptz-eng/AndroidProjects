package ru.voiceactivation.calibration

import org.junit.Assert.assertEquals
import org.junit.Test

class ExperimentMetricsTest {
    @Test fun computesPrecisionRecallAndF1() {
        val metrics = ExperimentMetrics(8, 2, 2, 10, 2.0, listOf(100, 200, 300, 400, 500))
        assertEquals(0.8, metrics.precision, 0.0001)
        assertEquals(0.8, metrics.recall, 0.0001)
        assertEquals(0.8, metrics.f1, 0.0001)
        assertEquals(1.0, metrics.falseTriggersPerMinute, 0.0001)
        assertEquals(300L, metrics.medianLatencyMs)
        assertEquals(500L, metrics.p95LatencyMs)
    }
}
