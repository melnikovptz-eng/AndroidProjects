package ru.voiceactivation.calibration

import kotlin.math.ceil

data class ExperimentMetrics(
    val truePositive: Int,
    val falsePositive: Int,
    val falseNegative: Int,
    val trueNegative: Int,
    val backgroundMinutes: Double,
    val latenciesMs: List<Long>
) {
    val precision: Double get() = ratio(truePositive, truePositive + falsePositive)
    val recall: Double get() = ratio(truePositive, truePositive + falseNegative)
    val f1: Double get() = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
    val falseTriggersPerMinute: Double get() =
        if (backgroundMinutes <= 0.0) 0.0 else falsePositive / backgroundMinutes
    val medianLatencyMs: Long get() = percentile(0.50)
    val p95LatencyMs: Long get() = percentile(0.95)

    private fun ratio(a: Int, b: Int) = if (b == 0) 0.0 else a.toDouble() / b

    private fun percentile(p: Double): Long {
        if (latenciesMs.isEmpty()) return 0L
        val sorted = latenciesMs.sorted()
        val index = (ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }
}
