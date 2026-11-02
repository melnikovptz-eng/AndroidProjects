package ru.voiceactivation.calibration

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CalibrationSample(
    val timestampMs: Long,
    val peakSnrDb: Float,
    val maxTriggerLevelDbfs: Float,
    val speechExpected: Boolean,
    val durationMs: Long,
    val capturedRelativeThresholdDb: Float,
    val capturedAbsoluteFloorDbfs: Float,
    val capturedHysteresisDb: Float,
    val capturedMinTriggerMs: Int,
    val capturedUsePeakForTrigger: Boolean
)

data class ThresholdRecommendation(
    val ready: Boolean,
    val relativeThresholdDb: Float,
    val absoluteFloorDbfs: Float,
    val precision: Double,
    val recall: Double,
    val f1: Double,
    val falseTriggersPerMinute: Double,
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
                        maxRmsDbfs = o.optDouble("maxRmsDbfs", o.optDouble("peakDbfs")).toFloat(),
                        speechExpected = o.optBoolean("speechExpected"),
                        durationMs = o.optLong("durationMs", 5000L),
                        capturedRelativeThresholdDb = o.optDouble("capturedRelativeThresholdDb", 10.0).toFloat(),
                        capturedAbsoluteFloorDbfs = o.optDouble("capturedAbsoluteFloorDbfs", -48.0).toFloat(),
                        capturedHysteresisDb = o.optDouble("capturedHysteresisDb", 4.0).toFloat(),
                        capturedMinTriggerMs = o.optInt("capturedMinTriggerMs", 120),
                        capturedUsePeakForTrigger = o.optBoolean("capturedUsePeakForTrigger", false)
                    )
                )
            }
        }
    }

    fun addSample(snr: Float, maxRms: Float, speechExpected: Boolean, durationMs: Long, config: CalibrationConfig) {
        val all = samples().toMutableList()
        all += CalibrationSample(
            System.currentTimeMillis(), snr, maxRms, speechExpected, durationMs.coerceAtLeast(1L),
            config.relativeThresholdDb, config.absoluteFloorDbfs, config.hysteresisDb, config.minTriggerMs, config.usePeakForTrigger
        )
        val array = JSONArray()
        all.takeLast(2000).forEach { s ->
            array.put(JSONObject()
                .put("timestampMs", s.timestampMs)
                .put("peakSnrDb", s.peakSnrDb.toDouble())
                .put("maxRmsDbfs", s.maxRmsDbfs.toDouble())
                .put("speechExpected", s.speechExpected)
                .put("durationMs", s.durationMs)
                .put("capturedRelativeThresholdDb", s.capturedRelativeThresholdDb.toDouble())
                .put("capturedAbsoluteFloorDbfs", s.capturedAbsoluteFloorDbfs.toDouble())
                .put("capturedHysteresisDb", s.capturedHysteresisDb.toDouble())
                .put("capturedMinTriggerMs", s.capturedMinTriggerMs)
                .put("capturedUsePeakForTrigger", s.capturedUsePeakForTrigger))
        }
        prefs.edit().putString("samples", array.toString()).apply()
    }

    fun clear() { prefs.edit().remove("samples").apply() }

    fun evaluate(config: CalibrationConfig): ExperimentMetrics {
        val c = config.normalized()
        val data = samples().filter { it.capturedUsePeakForTrigger == c.usePeakForTrigger }
        var tp = 0; var fp = 0; var fn = 0; var tn = 0
        data.forEach { sample ->
            val predicted = sample.peakSnrDb >= c.relativeThresholdDb && sample.maxTriggerLevelDbfs >= c.absoluteFloorDbfs
            when {
                predicted && sample.speechExpected -> tp++
                predicted && !sample.speechExpected -> fp++
                !predicted && sample.speechExpected -> fn++
                else -> tn++
            }
        }
        val backgroundMinutes = data.filterNot { it.speechExpected }.sumOf { it.durationMs } / 60_000.0
        return ExperimentMetrics(tp, fp, fn, tn, backgroundMinutes, emptyList())
    }

    fun recommend(usePeakForTrigger: Boolean): ThresholdRecommendation {
        val data = samples().filter { it.capturedUsePeakForTrigger == usePeakForTrigger }
        val positives = data.count { it.speechExpected }
        val negatives = data.size - positives
        if (positives < 10 || negatives < 10) {
            return ThresholdRecommendation(
                false, 10f, -48f, 0.0, 0.0, 0.0, 0.0, positives, negatives,
                "Нужно минимум 10 размеченных окон речи и 10 окон фонового шума."
            )
        }

        val backgroundDurationMs = data.filterNot { it.speechExpected }.sumOf { it.durationMs }
        var best: ThresholdRecommendation? = null
        for (relative in 2..24) {
            for (floor in -60..-24 step 2) {
                var tp = 0
                var fp = 0
                var fn = 0
                data.forEach { sample ->
                    val predicted = sample.peakSnrDb >= relative.toFloat() && sample.maxRmsDbfs >= floor.toFloat()
                    when {
                        predicted && sample.speechExpected -> tp++
                        predicted && !sample.speechExpected -> fp++
                        !predicted && sample.speechExpected -> fn++
                    }
                }
                val precision = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
                val recall = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
                val f1 = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
                val falseRate = if (backgroundDurationMs <= 0L) 0.0 else fp * 60_000.0 / backgroundDurationMs
                val candidate = ThresholdRecommendation(
                    true, relative.toFloat(), floor.toFloat(), precision, recall, f1, falseRate,
                    positives, negatives, "Подбор по размеченным окнам; проверить повторной серией."
                )
                if (best == null || candidate.f1 > best!!.f1 ||
                    (candidate.f1 == best!!.f1 && candidate.falseTriggersPerMinute < best!!.falseTriggersPerMinute)) best = candidate
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
                .put("maxRmsDbfs", s.maxRmsDbfs.toDouble())
                .put("speechExpected", s.speechExpected)
                .put("durationMs", s.durationMs)
                .put("capturedRelativeThresholdDb", s.capturedRelativeThresholdDb.toDouble())
                .put("capturedAbsoluteFloorDbfs", s.capturedAbsoluteFloorDbfs.toDouble())
                .put("capturedHysteresisDb", s.capturedHysteresisDb.toDouble())
                .put("capturedMinTriggerMs", s.capturedMinTriggerMs)
                .put("capturedUsePeakForTrigger", s.capturedUsePeakForTrigger))
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
                .put("maxRmsDbfs", item.optDouble("maxRmsDbfs", item.optDouble("peakDbfs")))
                .put("speechExpected", item.getBoolean("speechExpected"))
                .put("durationMs", item.optLong("durationMs", 5000L))
                .put("capturedRelativeThresholdDb", item.optDouble("capturedRelativeThresholdDb", 10.0))
                .put("capturedAbsoluteFloorDbfs", item.optDouble("capturedAbsoluteFloorDbfs", -48.0))
                .put("capturedHysteresisDb", item.optDouble("capturedHysteresisDb", 4.0))
                .put("capturedMinTriggerMs", item.optInt("capturedMinTriggerMs", 120))
                .put("capturedUsePeakForTrigger", item.optBoolean("capturedUsePeakForTrigger", false)))
        }
        prefs.edit().putString("samples", output.toString()).apply()
    }
}
