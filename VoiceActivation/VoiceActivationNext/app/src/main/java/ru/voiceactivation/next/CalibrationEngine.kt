package ru.voiceactivation.next

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Supervised calibration from explicitly labelled acoustic measurements.
 * Raw audio is never persisted; only the three measured metrics and labels are stored.
 */
internal object CalibrationEngine {
    private const val PREFS = "voice_calibration"
    private const val KEY = "samples"
    private const val MIN_PER_CLASS = 10

    data class Sample(
        val expectedCommand: String?,
        val owner: Boolean,
        val predictedCommand: String,
        val bestDistance: Double,
        val marginRatio: Double,
        val ownerCosine: Double
    )

    data class Result(
        val success: Boolean,
        val message: String,
        val dtwThreshold: Double = 7.0,
        val ownerThreshold: Double = 0.88,
        val marginThreshold: Double = 0.88,
        val commandValidation: Double = 0.0,
        val ownerValidation: Double = 0.0,
        val totalSamples: Int = 0
    )

    fun load(context: Context): List<Sample> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                Sample(
                    expectedCommand = o.optString("expected").takeIf { it.isNotEmpty() },
                    owner = o.optBoolean("owner"),
                    predictedCommand = o.optString("predicted"),
                    bestDistance = o.optDouble("dtw", Double.NaN),
                    marginRatio = o.optDouble("margin", Double.NaN),
                    ownerCosine = o.optDouble("cosine", Double.NaN)
                ).takeIf {
                    it.bestDistance.isFinite() && it.marginRatio.isFinite() && it.ownerCosine.isFinite()
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun append(context: Context, sample: Sample) {
        val all = load(context).toMutableList()
        all.add(sample)
        val array = JSONArray()
        all.takeLast(500).forEach { s ->
            array.put(JSONObject()
                .put("expected", s.expectedCommand ?: "")
                .put("owner", s.owner)
                .put("predicted", s.predictedCommand)
                .put("dtw", s.bestDistance)
                .put("margin", s.marginRatio)
                .put("cosine", s.ownerCosine))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }

    fun summary(samples: List<Sample>): String {
        val positives = samples.count { it.expectedCommand != null }
        val unknown = samples.size - positives
        val owners = samples.count { it.owner }
        val others = samples.size - owners
        return "КАЛИБРОВОЧНЫЕ ДАННЫЕ\n" +
            "Правильные команды: $positives / минимум $MIN_PER_CLASS\n" +
            "Посторонняя речь: $unknown / минимум $MIN_PER_CLASS\n" +
            "Голос владельца: $owners / минимум $MIN_PER_CLASS\n" +
            "Другие голоса: $others / минимум $MIN_PER_CLASS\n" +
            "Всего сохранено: ${samples.size}\n" +
            "Сырые аудиозаписи не сохраняются."
    }

    fun fit(samples: List<Sample>): Result {
        val positives = samples.filter { it.expectedCommand != null }
        val unknown = samples.filter { it.expectedCommand == null }
        val owners = samples.filter { it.owner }
        val others = samples.filter { !it.owner }
        val counts = listOf(
            "правильных команд" to positives.size,
            "посторонних фраз" to unknown.size,
            "записей владельца" to owners.size,
            "записей других голосов" to others.size
        )
        val missing = counts.filter { it.second < MIN_PER_CLASS }
        if (missing.isNotEmpty()) {
            return Result(false,
                "Недостаточно размеченных примеров. Нужно не менее $MIN_PER_CLASS для каждой группы:\n" +
                missing.joinToString("\n") { it.first + ": " + it.second + " / $MIN_PER_CLASS" } +
                "\nЗаписывайте правильные команды своим голосом, посторонние фразы своим голосом и образцы другого говорящего.")
        }

        // Keep a deterministic hold-out subset from every class; no sample used for fitting
        // is reused for validation in its corresponding class.
        val validationSet = linkedSetOf<Sample>()
        listOf(positives, unknown, owners, others).forEach { group ->
            val holdoutCount = max(2, ceil(group.size * 0.2).toInt())
            validationSet.addAll(group.takeLast(holdoutCount))
        }
        val validation = samples.filter { it in validationSet }
        val training = samples.filter { it !in validationSet }
        val trainPos = training.filter { it.expectedCommand != null }
        val trainNeg = training.filter { it.expectedCommand == null }
        val trainOwners = training.filter { it.owner }
        val trainOthers = training.filter { !it.owner }
        if (trainPos.isEmpty() || trainNeg.isEmpty() || trainOwners.isEmpty() || trainOthers.isEmpty()) {
            return Result(false, "Не удалось сформировать независимую проверочную выборку. Добавьте больше образцов.")
        }

        val dtwCandidates = candidates((training.map { it.bestDistance } + 0.000001).filter { it.isFinite() && it >= 0.0 })
        val marginCandidates = candidates(training.map { it.marginRatio }.filter { it.isFinite() && it >= 0.0 })
        var bestDtw = 7.0
        var bestMargin = 0.88
        var bestScore = -1.0
        for (d in dtwCandidates) for (m in marginCandidates) {
            val tpr = trainPos.count { accepted(it, d, m) }.toDouble() / trainPos.size
            val tnr = trainNeg.count { !accepted(it, d, m) }.toDouble() / trainNeg.size
            val score = (tpr + tnr) / 2.0
            if (score > bestScore) {
                bestScore = score
                bestDtw = d
                bestMargin = m
            }
        }

        val ownerCandidates = candidates(training.map { it.ownerCosine })
        var bestOwner = 0.88
        var bestOwnerScore = -1.0
        for (threshold in ownerCandidates) {
            val tpr = trainOwners.count { it.ownerCosine > threshold }.toDouble() / trainOwners.size
            val tnr = trainOthers.count { it.ownerCosine <= threshold }.toDouble() / trainOthers.size
            val score = (tpr + tnr) / 2.0
            if (score > bestOwnerScore) {
                bestOwnerScore = score
                bestOwner = threshold
            }
        }

        val valPos = validation.filter { it.expectedCommand != null }
        val valNeg = validation.filter { it.expectedCommand == null }
        val valOwners = validation.filter { it.owner }
        val valOthers = validation.filter { !it.owner }
        if (valPos.isEmpty() || valNeg.isEmpty() || valOwners.isEmpty() || valOthers.isEmpty()) {
            return Result(false, "Проверочная выборка не содержит все четыре класса. Запишите дополнительные примеры.")
        }
        val commandAccuracy = (
            valPos.count { accepted(it, bestDtw, bestMargin) }.toDouble() / valPos.size +
            valNeg.count { !accepted(it, bestDtw, bestMargin) }.toDouble() / valNeg.size
        ) / 2.0
        val ownerAccuracy = (
            valOwners.count { it.ownerCosine > bestOwner }.toDouble() / valOwners.size +
            valOthers.count { it.ownerCosine <= bestOwner }.toDouble() / valOthers.size
        ) / 2.0

        return Result(
            success = true,
            message = "Пороги подобраны на обучающей части и проверены на отложенных примерах.",
            dtwThreshold = bestDtw,
            ownerThreshold = bestOwner,
            marginThreshold = bestMargin,
            commandValidation = commandAccuracy,
            ownerValidation = ownerAccuracy,
            totalSamples = samples.size
        )
    }

    private fun accepted(s: Sample, dtw: Double, margin: Double): Boolean =
        s.bestDistance < dtw && s.marginRatio < margin &&
            s.expectedCommand != null && s.predictedCommand == s.expectedCommand

    private fun candidates(values: List<Double>): List<Double> {
        if (values.isEmpty()) return listOf(0.0)
        val sorted = values.filter { it.isFinite() }.sorted()
        if (sorted.isEmpty()) return listOf(0.0)
        val count = minOf(25, sorted.size)
        val result = linkedSetOf<Double>()
        for (i in 0 until count) {
            val index = if (count == 1) 0 else (i.toDouble() * (sorted.size - 1) / (count - 1)).toInt()
            result.add(sorted[index] + 0.000001)
        }
        result.add(sorted.last() + 0.000001)
        return result.toList()
    }
}
