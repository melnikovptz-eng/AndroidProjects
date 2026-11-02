package ru.voiceactivation.calibration

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ProfileRepository(context: Context) {
    private val prefs = context.getSharedPreferences("named_calibration_profiles_v1", Context.MODE_PRIVATE)

    fun names(): List<String> = prefs.all.keys.filterNot { it == "__CURRENT__" }.sorted()

    fun save(name: String, config: CalibrationConfig) {
        val key = name.trim()
        require(key.isNotBlank()) { "Введите название профиля" }
        prefs.edit().putString(key, encodeConfig(config).toString()).apply()
    }

    fun load(name: String): CalibrationConfig? {
        val raw = prefs.getString(name, null) ?: return null
        return decodeConfig(JSONObject(raw))
    }

    fun exportJson(experiments: ExperimentRepository, current: CalibrationConfig): String {
        val profiles = JSONObject()
        names().forEach { name ->
            load(name)?.let { profiles.put(name, encodeConfig(it)) }
        }
        profiles.put("__CURRENT__", encodeConfig(current))
        return JSONObject()
            .put("schemaVersion", 1)
            .put("profiles", profiles)
            .put("experiments", JSONObject(experiments.exportJson()))
            .toString(2)
    }

    fun importJson(json: String, experiments: ExperimentRepository): CalibrationConfig? {
        val root = JSONObject(json)
        require(root.optInt("schemaVersion") == 1) { "Неподдерживаемая версия файла" }
        val profiles = root.getJSONObject("profiles")
        val editor = prefs.edit()
        val iterator = profiles.keys()
        while (iterator.hasNext()) {
            val name = iterator.next()
            val config = decodeConfig(profiles.getJSONObject(name))
            editor.putString(name, encodeConfig(config).toString())
        }
        editor.apply()
        if (root.has("experiments")) experiments.importJson(root.getJSONObject("experiments").toString())
        return if (profiles.has("__CURRENT__")) decodeConfig(profiles.getJSONObject("__CURRENT__")) else null
    }

    private fun encodeConfig(c: CalibrationConfig) = JSONObject()
        .put("relativeThresholdDb", c.relativeThresholdDb.toDouble())
        .put("absoluteFloorDbfs", c.absoluteFloorDbfs.toDouble())
        .put("hysteresisDb", c.hysteresisDb.toDouble())
        .put("minTriggerMs", c.minTriggerMs)
        .put("minSpeechMs", c.minSpeechMs)
        .put("silenceEndMs", c.silenceEndMs)
        .put("maxSegmentMs", c.maxSegmentMs)
        .put("noiseWindowMs", c.noiseWindowMs)
        .put("preRollMs", c.preRollMs)
        .put("noiseAdaptation", c.noiseAdaptation.toDouble())
        .put("cooldownMs", c.cooldownMs)
        .put("wakeSimilarityThreshold", c.wakeSimilarityThreshold.toDouble())
        .put("actionSimilarityThreshold", c.actionSimilarityThreshold.toDouble())
        .put("secondWordTimeoutMs", c.secondWordTimeoutMs)
        .put("maxInterWordPauseMs", c.maxInterWordPauseMs)
        .put("usePeakForTrigger", c.usePeakForTrigger)

    private fun decodeConfig(o: JSONObject) = CalibrationConfig(
        relativeThresholdDb = o.optDouble("relativeThresholdDb", 10.0).toFloat(),
        absoluteFloorDbfs = o.optDouble("absoluteFloorDbfs", -48.0).toFloat(),
        hysteresisDb = o.optDouble("hysteresisDb", 4.0).toFloat(),
        minTriggerMs = o.optInt("minTriggerMs", 120),
        minSpeechMs = o.optInt("minSpeechMs", 180),
        silenceEndMs = o.optInt("silenceEndMs", 650),
        maxSegmentMs = o.optInt("maxSegmentMs", 6000),
        noiseWindowMs = o.optInt("noiseWindowMs", 2500),
        preRollMs = o.optInt("preRollMs", 250),
        noiseAdaptation = o.optDouble("noiseAdaptation", 0.08).toFloat(),
        cooldownMs = o.optInt("cooldownMs", 450),
        wakeSimilarityThreshold = o.optDouble("wakeSimilarityThreshold", 0.72).toFloat(),
        actionSimilarityThreshold = o.optDouble("actionSimilarityThreshold", 0.78).toFloat(),
        secondWordTimeoutMs = o.optInt("secondWordTimeoutMs", 2500),
        maxInterWordPauseMs = o.optInt("maxInterWordPauseMs", 1200),
        usePeakForTrigger = o.optBoolean("usePeakForTrigger", false)
    ).normalized()
}
