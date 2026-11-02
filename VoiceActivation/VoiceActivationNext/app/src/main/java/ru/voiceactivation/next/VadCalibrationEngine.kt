package ru.voiceactivation.next

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.max

/**
 * Labelled 20 ms frame measurements used to fit the speech/noise decision boundary.
 * Each recording is a separate session so validation can hold out complete recordings.
 */
internal object VadCalibrationEngine {
    private const val PREFS = "vad_calibration"
    private const val KEY = "sessions"
    private const val MIN_SESSIONS_PER_CLASS = 2
    private const val MIN_FRAMES_PER_SESSION = 50

    data class Frame(val rms: Double, val peak: Double)
    data class Session(val speech: Boolean, val timestamp: Long, val frames: List<Frame>)
    data class Result(
        val success: Boolean,
        val message: String,
        val noiseRms: Double = 0.002,
        val rmsThreshold: Double = 0.006,
        val peakThreshold: Double = 0.018,
        val silenceMs: Int = 250,
        val minSpeechMs: Int = 200,
        val maxSpeechMs: Int = 5000,
        val validationAccuracy: Double = 0.0,
        val noiseSessions: Int = 0,
        val speechSessions: Int = 0
    )

    fun load(context: Context): List<Session> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val values = item.optJSONArray("frames") ?: return@mapNotNull null
                val frames = (0 until values.length()).mapNotNull { frameIndex ->
                    val pair = values.optJSONArray(frameIndex) ?: return@mapNotNull null
                    if (pair.length() < 2) return@mapNotNull null
                    val rms = pair.optDouble(0, Double.NaN)
                    val peak = pair.optDouble(1, Double.NaN)
                    if (rms.isFinite() && peak.isFinite() && rms >= 0.0 && peak >= 0.0)
                        Frame(rms, peak) else null
                }
                Session(item.optBoolean("speech"), item.optLong("timestamp"), frames)
                    .takeIf { frames.size >= MIN_FRAMES_PER_SESSION }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun append(context: Context, speech: Boolean, frames: List<Frame>) {
        if (frames.size < MIN_FRAMES_PER_SESSION) return
        val all = load(context).toMutableList()
        all.add(Session(speech, System.currentTimeMillis(), frames.take(300)))
        val array = JSONArray()
        all.takeLast(20).forEach { session ->
            val values = JSONArray()
            session.frames.forEach { values.put(JSONArray().put(it.rms).put(it.peak)) }
            array.put(JSONObject()
                .put("speech", session.speech)
                .put("timestamp", session.timestamp)
                .put("frames", values))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }

    fun summary(sessions: List<Session>): String {
        val noise = sessions.count { !it.speech }
        val speech = sessions.count { it.speech }
        return "КАЛИБРОВКА ОБНАРУЖЕНИЯ РЕЧИ\n" +
            "Записи шума: $noise / минимум $MIN_SESSIONS_PER_CLASS\n" +
            "Записи речи: $speech / минимум $MIN_SESSIONS_PER_CLASS\n" +
            "Кадров в сохранённых сессиях: ${sessions.sumOf { it.frames.size }}\n" +
            "Для подбора нужны отдельные записи в разных попытках."
    }

    fun fit(sessions: List<Session>): Result {
        val noiseSessions = sessions.filter { !it.speech }.sortedBy { it.timestamp }
        val speechSessions = sessions.filter { it.speech }.sortedBy { it.timestamp }
        if (noiseSessions.size < MIN_SESSIONS_PER_CLASS ||
            speechSessions.size < MIN_SESSIONS_PER_CLASS) {
            return Result(
                false,
                "Нужно минимум $MIN_SESSIONS_PER_CLASS отдельные записи шума и " +
                    "$MIN_SESSIONS_PER_CLASS отдельные записи речи. " +
                    "Сейчас: шум ${noiseSessions.size}, речь ${speechSessions.size}.",
                noiseSessions = noiseSessions.size,
                speechSessions = speechSessions.size
            )
        }

        // Hold out the newest complete recording of each class, not random frames
        // from the same recording used to fit the thresholds.
        val trainNoise = noiseSessions.dropLast(1).flatMap { it.frames }
        val trainSpeechAll = speechSessions.dropLast(1).flatMap { it.frames }
        val testNoise = noiseSessions.last().frames
        val testSpeechAll = speechSessions.last().frames
        if (trainNoise.isEmpty() || trainSpeechAll.isEmpty() || testNoise.isEmpty() || testSpeechAll.isEmpty()) {
            return Result(false, "Недостаточно кадров для независимой проверки.")
        }
        // Speech sessions intentionally contain pauses between phrases. Do not
        // label obvious quiet gaps as positive speech frames during threshold fitting.
        val noiseRmsCutoff = percentile(trainNoise.map { it.rms }, 0.95)
        val noisePeakCutoff = percentile(trainNoise.map { it.peak }, 0.95)
        val trainSpeech = trainSpeechAll.filter {
            it.rms > noiseRmsCutoff || it.peak > noisePeakCutoff
        }
        val testSpeech = testSpeechAll.filter {
            it.rms > noiseRmsCutoff || it.peak > noisePeakCutoff
        }
        if (trainSpeech.size < 10 || testSpeech.size < 10) {
            return Result(false, "В речевых сессиях слишком мало кадров выше измеренного фона. Повторите запись, произнося фразы громче и чётче.")
        }

        val rmsCandidates = candidates(trainNoise.map { it.rms } + trainSpeech.map { it.rms })
        val peakCandidates = candidates(trainNoise.map { it.peak } + trainSpeech.map { it.peak })
        var bestRms = 0.006
        var bestPeak = 0.018
        var bestScore = -1.0
        for (rms in rmsCandidates) for (peak in peakCandidates) {
            val hitRate = trainSpeech.count { detects(it, rms, peak) }.toDouble() / trainSpeech.size
            val rejectRate = trainNoise.count { !detects(it, rms, peak) }.toDouble() / trainNoise.size
            val score = (hitRate + rejectRate) / 2.0
            if (score > bestScore || (score == bestScore && rms + peak > bestRms + bestPeak)) {
                bestScore = score
                bestRms = rms
                bestPeak = peak
            }
        }

        val validationHit = testSpeech.count { detects(it, bestRms, bestPeak) }.toDouble() / testSpeech.size
        val validationReject = testNoise.count { !detects(it, bestRms, bestPeak) }.toDouble() / testNoise.size
        val validationAccuracy = (validationHit + validationReject) / 2.0
        if (validationAccuracy <= 0.5) {
            return Result(
                false,
                "Порог не подтвердился на новых записях (сбалансированная точность " +
                    "${"%.1f".format(java.util.Locale.US, validationAccuracy * 100)}%). " +
                    "Запишите более разнообразные примеры речи и шума.",
                validationAccuracy = validationAccuracy,
                noiseSessions = noiseSessions.size,
                speechSessions = speechSessions.size
            )
        }

        val noiseRms = percentile(trainNoise.map { it.rms }, 0.80)
            .coerceIn(0.00005, 0.25)
        val timing = inferTiming(speechSessions.dropLast(1), bestRms, bestPeak)
        return Result(
            success = true,
            message = "Пороги RMS/PEAK подобраны по размеченным кадрам; пауза и длительности оценены по речевым сессиям.",
            noiseRms = noiseRms,
            rmsThreshold = bestRms,
            peakThreshold = bestPeak,
            silenceMs = timing.first,
            minSpeechMs = timing.second,
            maxSpeechMs = timing.third,
            validationAccuracy = validationAccuracy,
            noiseSessions = noiseSessions.size,
            speechSessions = speechSessions.size
        )
    }

    private fun inferTiming(
        sessions: List<Session>,
        rmsThreshold: Double,
        peakThreshold: Double
    ): Triple<Int, Int, Int> {
        val voicedRuns = mutableListOf<Int>()
        val internalSilenceRuns = mutableListOf<Int>()
        sessions.forEach { session ->
            var voiced = false
            var run = 0
            val runs = mutableListOf<Pair<Boolean, Int>>()
            session.frames.forEach { frame ->
                val nextVoiced = detects(frame, rmsThreshold, peakThreshold)
                if (nextVoiced == voiced) run++
                else {
                    if (run > 0) runs.add(voiced to run)
                    voiced = nextVoiced
                    run = 1
                }
            }
            if (run > 0) runs.add(voiced to run)
            val firstVoice = runs.indexOfFirst { it.first }
            val lastVoice = runs.indexOfLast { it.first }
            if (firstVoice >= 0) {
                runs.subList(firstVoice, lastVoice + 1).forEach { (isVoice, frames) ->
                    if (isVoice) voicedRuns.add(frames * 20)
                    else internalSilenceRuns.add(frames * 20)
                }
            }
        }
        val minSpeech = if (voicedRuns.isEmpty()) 200 else
            percentileInt(voicedRuns, 0.10).coerceIn(100, 500)
        val maxSpeech = if (voicedRuns.isEmpty()) 5000 else
            max(3000, (percentileInt(voicedRuns, 0.95) * 1.5).toInt()).coerceIn(3000, 10000)
        val sortedGaps = internalSilenceRuns.sorted()
        var pause = 250
        if (sortedGaps.size >= 2) {
            var largestGap = 0
            var split = -1
            for (i in 0 until sortedGaps.lastIndex) {
                val gap = sortedGaps[i + 1] - sortedGaps[i]
                if (gap > largestGap) {
                    largestGap = gap
                    split = i
                }
            }
            if (split >= 0 && largestGap >= 60) {
                pause = ((sortedGaps[split] + sortedGaps[split + 1]) / 2).coerceIn(100, 800)
            }
        }
        return Triple(pause, minSpeech, maxSpeech)
    }

    private fun percentileInt(values: List<Int>, fraction: Double): Int {
        if (values.isEmpty()) return 200
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * fraction).toInt().coerceIn(0, sorted.lastIndex)]
    }

    private fun detects(frame: Frame, rms: Double, peak: Double): Boolean =
        frame.rms >= rms || frame.peak >= peak

    private fun candidates(values: List<Double>): List<Double> {
        val sorted = values.filter { it.isFinite() && it >= 0.0 }.sorted()
        if (sorted.isEmpty()) return listOf(0.0005)
        val result = linkedSetOf<Double>()
        val count = minOf(24, sorted.size)
        for (i in 0 until count) {
            val index = if (count == 1) 0 else
                (i.toDouble() * (sorted.size - 1) / (count - 1)).toInt()
            result.add(sorted[index].coerceAtLeast(0.00005))
        }
        result.add((sorted.first() * 0.8).coerceAtLeast(0.00005))
        result.add(sorted.last() + 0.000001)
        return result.toList()
    }

    private fun percentile(values: List<Double>, fraction: Double): Double {
        if (values.isEmpty()) return 0.002
        val sorted = values.sorted()
        return sorted[(ceil(sorted.size * fraction).toInt() - 1).coerceIn(0, sorted.lastIndex)]
    }
}
