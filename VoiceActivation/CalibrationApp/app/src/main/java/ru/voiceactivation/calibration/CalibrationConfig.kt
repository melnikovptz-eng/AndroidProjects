package ru.voiceactivation.calibration

data class CalibrationConfig(
    val relativeThresholdDb: Float = 10f,
    val absoluteFloorDbfs: Float = -48f,
    val hysteresisDb: Float = 4f,
    val minTriggerMs: Int = 120,
    val minSpeechMs: Int = 180,
    val silenceEndMs: Int = 650,
    val maxSegmentMs: Int = 6000,
    val noiseWindowMs: Int = 2500,
    val preRollMs: Int = 250,
    val noiseAdaptation: Float = 0.08f,
    val cooldownMs: Int = 450
) {
    fun normalized() = copy(
        relativeThresholdDb = relativeThresholdDb.coerceIn(0f, 30f),
        absoluteFloorDbfs = absoluteFloorDbfs.coerceIn(-90f, -6f),
        hysteresisDb = hysteresisDb.coerceIn(0f, 15f),
        minTriggerMs = minTriggerMs.coerceIn(20, 1000),
        minSpeechMs = minSpeechMs.coerceIn(50, 2000),
        silenceEndMs = silenceEndMs.coerceIn(100, 2500),
        maxSegmentMs = maxSegmentMs.coerceIn(1000, 15000),
        noiseWindowMs = noiseWindowMs.coerceIn(500, 10000),
        preRollMs = preRollMs.coerceIn(0, 1000),
        noiseAdaptation = noiseAdaptation.coerceIn(0.005f, 0.5f),
        cooldownMs = cooldownMs.coerceIn(0, 5000)
    )
}
