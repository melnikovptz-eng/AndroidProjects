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
    private const val MIN_PER_COMMAND = 5

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
        // Unknown phrases must be spoken by the owner; samples from another speaker
        // are counted separately and must not satisfy this requirement.
        val unknown = samples.count { it.expectedCommand == null && it.owner }
        val owners = samples.count { it.owner }
        val others = samples.count { !it.owner }
        val perCommand = samples.filter { it.expectedCommand != null }
            .groupBy { it.expectedCommand!! }
            .toSortedMap()
            .entries.joinToString("\n") { (name, rows) ->
                "  $name: ${rows.size} / минимум $MIN_PER_COMMAND"
            }
        return "КАЛИБРОВОЧНЫЕ ДАННЫЕ\n" +
            "Правильные команды: $positives / минимум $MIN_PER_CLASS\n" +
            "По командам:\n" + (perCommand.ifBlank { "  пока нет образцов" }) + "\n" +
            "Посторонняя речь владельца: $unknown / минимум $MIN_PER_CLASS\n" +
            "Голос владельца: $owners / минимум $MIN_PER_CLASS\n" +
            "Другие голоса: $others / минимум $MIN_PER_CLASS\n" +
            "Всего сохранено: ${samples.size}\n" +
            "Сырые аудиозаписи не сохраняются."
    }

    fun fit(samples: List<Sample>): Result {
        val positives = samples.filter { it.expectedCommand != null }
        val unknown = samples.filter { it.expectedCommand == null && it.owner }
        val owners = samples.filter { it.owner }
        val others = samples.filter { !it.owner }
        val counts = listOf(
            "правильных команд" to positives.size,
            "посторонних фраз" to unknown.size,
            "записей владельца" to owners.size,
            "записей других голосов" to others.size
        )
        val commandGroups = positives.groupBy { it.expectedCommand ?: "" }
        val weakCommands = commandGroups.filterValues { it.size < MIN_PER_COMMAND }
        val missing = counts.filter { it.second < MIN_PER_CLASS }
        if (missing.isNotEmpty() || weakCommands.isNotEmpty()) {
            return Result(false,
                "Недостаточно размеченных примеров. Нужно не менее $MIN_PER_CLASS для каждой группы:\n" +
                missing.joinToString("\n") { it.first + ": " + it.second + " / $MIN_PER_CLASS" } +
                (if (weakCommands.isEmpty()) "" else "\nПовторы по отдельным командам: " +
                    weakCommands.entries.joinToString { it.key + " — " + it.value.size + "/" + MIN_PER_COMMAND }) +
                "\nЗаписывайте правильные команды своим голосом, посторонние фразы своим голосом и образцы другого говорящего.")
        }

        // Select validation rows by their original index, not data-class equality:
        // two independently recorded examples can legitimately have identical metrics.
        val indexed = samples.withIndex().toList()
        val validationIndices = linkedSetOf<Int>()
        val indexedCommandGroups = indexed.filter { it.value.expectedCommand != null }
            .groupBy { it.value.expectedCommand ?: "" }
        indexedCommandGroups.values.forEach { group ->
            validationIndices.addAll(group.takeLast(max(1, ceil(group.size * 0.2).toInt())).map { it.index })
        }
        val indexedUnknown = indexed.filter { it.value.expectedCommand == null && it.value.owner }
        val indexedOwners = indexed.filter { it.value.owner }
        val indexedOthers = indexed.filter { !it.value.owner }
        listOf(indexedUnknown, indexedOwners, indexedOthers).forEach { group ->
            val holdoutCount = max(2, ceil(group.size * 0.2).toInt())
            validationIndices.addAll(group.takeLast(holdoutCount).map { it.index })
        }
        val validation = indexed.filter { it.index in validationIndices }.map { it.value }
        val training = indexed.filter { it.index !in validationIndices }.map { it.value }
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
            val tpr = trainPos.count { correctlyAccepted(it, d, m) }.toDouble() / trainPos.size
            val tnr = trainNeg.count { !wouldAccept(it, d, m) }.toDouble() / trainNeg.size
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
            valPos.count { correctlyAccepted(it, bestDtw, bestMargin) }.toDouble() / valPos.size +
            valNeg.count { !wouldAccept(it, bestDtw, bestMargin) }.toDouble() / valNeg.size
        ) / 2.0
        val ownerAccuracy = (
            valOwners.count { it.ownerCosine > bestOwner }.toDouble() / valOwners.size +
            valOthers.count { it.ownerCosine <= bestOwner }.toDouble() / valOthers.size
        ) / 2.0
        if (commandAccuracy <= 0.5 || ownerAccuracy <= 0.5) {
            return Result(false,
                "Отложенная проверка не подтвердила качество выше случайного уровня. " +
                "Пороги не сохранены. Запишите более разнообразные и правильно размеченные примеры.")
        }

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

    private fun wouldAccept(s: Sample, dtw: Double, margin: Double): Boolean =
        s.bestDistance < dtw && s.marginRatio < margin

    private fun correctlyAccepted(s: Sample, dtw: Double, margin: Double): Boolean =
        wouldAccept(s, dtw, margin) && s.expectedCommand != null &&
            s.predictedCommand == s.expectedCommand

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
        result.add(sorted.first() - 0.000001)
        result.add(sorted.last() + 0.000001)
        return result.toList()
    }
}
