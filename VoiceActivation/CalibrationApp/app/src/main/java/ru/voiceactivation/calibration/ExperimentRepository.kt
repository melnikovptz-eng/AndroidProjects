package ru.voiceactivation.calibration

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CalibrationSample(
    val timestampMs: Long,
    val peakSnrDb: Float,
    val peakDbfs: Float,
    val speechExpected: Boolean
)

data class ThresholdRecommendation(
    val ready: Boolean,
    val relativeThresholdDb: Float,
    val absoluteFloorDbfs: Float,
    val precision: Double,
    val recall: Double,
    val f1: Double,
    val positiveSamples: Int,
    val negativeSamples: Int,
    val explanation: String
)

class ExperimentRepository(context: Context) {
    private val prefs = context.getSharedPreferences("calibration_experiments_v1", Context.MODE_PRIVATE)

    fun samples(): List<CalibrationSample> {
        val raw = prefs.getString("samples", "[]") ?: "[]"
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    CalibrationSample(
                        timestampMs = o.optLong("timestampMs"),
                        peakSnrDb = o.optDouble("peakSnrDb").toFloat(),
                        peakDbfs = o.optDouble("peakDbfs").toFloat(),
                        speechExpected = o.optBoolean("speechExpected")
                    )
                )
            }
        }
    }

    fun addSample(snr: Float, peak: Float, speechExpected: Boolean) {
        val all = samples().toMutableList()
        all += CalibrationSample(System.currentTimeMillis(), snr, peak, speechExpected)
        val array = JSONArray()
        all.takeLast(2000).forEach { s ->
            array.put(JSONObject()
                .put("timestampMs", s.timestampMs)
                .put("peakSnrDb", s.peakSnrDb.toDouble())
                .put("peakDbfs", s.peakDbfs.toDouble())
                .put("speechExpected", s.speechExpected))
        }
        prefs.edit().putString("samples", array.toString()).apply()
    }

    fun clear() { prefs.edit().remove("samples").apply() }

    fun recommend(): ThresholdRecommendation {
        val data = samples()
        val positives = data.count { it.speechExpected }
        val negatives = data.size - positives
        if (positives < 10 || negatives < 10) {
            return ThresholdRecommendation(
                false, 10f, -48f, 0.0, 0.0, 0.0, positives, negatives,
                "Нужно минимум 10 размеченных окон речи и 10 окон фонового шума."
            )
        }

        var best: ThresholdRecommendation? = null
        for (relative in 2..24 step 1) {
            for (floor in -60..-24 step 2) {
                var tp = 0; var fp = 0; var fn = 0
                data.forEach { sample ->
                    val predicted = sample.peakSnrDb >= relative && sample.peakDbfs >= floor
                    when {
                        predicted && sample.speechExpected -> tp++
                        predicted && !sample.speechExpected -> fp++
                        !predicted && sample.speechExpected -> fn++
                    }
                }
                val precision = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
                val recall = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
                val f1 = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
                val candidate = ThresholdRecommendation(
                    true, relative.toFloat(), floor.toFloat(), precision, recall, f1,
                    positives, negatives, "Подбор по размеченным окнам; проверить повторной серией."
                )
                if (best == null || candidate.f1 > best!!.f1 ||
                    (candidate.f1 == best!!.f1 && candidate.precision > best!!.precision)) best = candidate
            }
        }
        return best!!
    }

    fun exportJson(): String {
        val array = JSONArray()
        samples().forEach { s ->
            array.put(JSONObject()
                .put("timestampMs", s.timestampMs)
                .put("peakSnrDb", s.peakSnrDb.toDouble())
                .put("peakDbfs", s.peakDbfs.toDouble())
                .put("speechExpected", s.speechExpected))
        }
        return JSONObject().put("schemaVersion", 1).put("samples", array).toString(2)
    }

    fun importJson(json: String) {
        val root = JSONObject(json)
        require(root.optInt("schemaVersion") == 1) { "Неподдерживаемая версия данных" }
        val input = root.getJSONArray("samples")
        val output = JSONArray()
        for (i in 0 until input.length()) {
            val item = input.getJSONObject(i)
            output.put(JSONObject()
                .put("timestampMs", item.getLong("timestampMs"))
                .put("peakSnrDb", item.getDouble("peakSnrDb"))
                .put("peakDbfs", item.getDouble("peakDbfs"))
                .put("speechExpected", item.getBoolean("speechExpected")))
        }
        prefs.edit().putString("samples", output.toString()).apply()
    }
}
