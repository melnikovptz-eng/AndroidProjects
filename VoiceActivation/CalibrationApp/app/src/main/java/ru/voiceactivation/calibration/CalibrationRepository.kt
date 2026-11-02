package ru.voiceactivation.calibration

import android.content.Context

class CalibrationRepository(context: Context) {
    private val prefs = context.getSharedPreferences("calibration_profiles_v1", Context.MODE_PRIVATE)

    fun load(): CalibrationConfig = CalibrationConfig(
        relativeThresholdDb = prefs.getFloat("relativeThresholdDb", 10f),
        absoluteFloorDbfs = prefs.getFloat("absoluteFloorDbfs", -48f),
        hysteresisDb = prefs.getFloat("hysteresisDb", 4f),
        minTriggerMs = prefs.getInt("minTriggerMs", 120),
        minSpeechMs = prefs.getInt("minSpeechMs", 180),
        silenceEndMs = prefs.getInt("silenceEndMs", 650),
        maxSegmentMs = prefs.getInt("maxSegmentMs", 6000),
        noiseWindowMs = prefs.getInt("noiseWindowMs", 2500),
        preRollMs = prefs.getInt("preRollMs", 250),
        noiseAdaptation = prefs.getFloat("noiseAdaptation", 0.08f),
        cooldownMs = prefs.getInt("cooldownMs", 450),
        wakeSimilarityThreshold = prefs.getFloat("wakeSimilarityThreshold", 0.72f),
        actionSimilarityThreshold = prefs.getFloat("actionSimilarityThreshold", 0.78f),
        secondWordTimeoutMs = prefs.getInt("secondWordTimeoutMs", 2500),
        maxInterWordPauseMs = prefs.getInt("maxInterWordPauseMs", 1200)
    )

    fun save(c: CalibrationConfig) {
        prefs.edit()
            .putFloat("relativeThresholdDb", c.relativeThresholdDb)
            .putFloat("absoluteFloorDbfs", c.absoluteFloorDbfs)
            .putFloat("hysteresisDb", c.hysteresisDb)
            .putInt("minTriggerMs", c.minTriggerMs)
            .putInt("minSpeechMs", c.minSpeechMs)
            .putInt("silenceEndMs", c.silenceEndMs)
            .putInt("maxSegmentMs", c.maxSegmentMs)
            .putInt("noiseWindowMs", c.noiseWindowMs)
            .putInt("preRollMs", c.preRollMs)
            .putFloat("noiseAdaptation", c.noiseAdaptation)
            .putInt("cooldownMs", c.cooldownMs)
            .putFloat("wakeSimilarityThreshold", c.wakeSimilarityThreshold)
            .putFloat("actionSimilarityThreshold", c.actionSimilarityThreshold)
            .putInt("secondWordTimeoutMs", c.secondWordTimeoutMs)
            .putInt("maxInterWordPauseMs", c.maxInterWordPauseMs)
            .apply()
    }
}
