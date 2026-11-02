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
        return "КАЛИБРОВКА ОБНАРУЖЕНИЯ РЕЧИ\\n" +
            "Записи шума: $noise / минимум $MIN_SESSIONS_PER_CLASS\\n" +
            "Записи речи: $speech / минимум $MIN_SESSIONS_PER_CLASS\\n" +
            "Кадров в сохранённых сессиях: ${sessions.sumOf { it.frames.size }}\\n" +
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
        val trainSpeech = speechSessions.dropLast(1).flatMap { it.frames }
        val testNoise = noiseSessions.last().frames
        val testSpeech = speechSessions.last().frames
        if (trainNoise.isEmpty() || trainSpeech.isEmpty() || testNoise.isEmpty() || testSpeech.isEmpty()) {
            return Result(false, "Недостаточно кадров для независимой проверки.")
        }

        val rmsCandidates = candidates(trainNoise.map { it.rms })
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

        val noiseRms = percentile(noiseSessions.flatMap { it.frames }.map { it.rms }, 0.80)
            .coerceIn(0.00005, 0.25)
        return Result(
            success = true,
            message = "Порог RMS/PEAK подобран по размеченным кадрам и проверен на двух новых записях.",
            noiseRms = noiseRms,
            rmsThreshold = bestRms,
            peakThreshold = bestPeak,
            validationAccuracy = validationAccuracy,
            noiseSessions = noiseSessions.size,
            speechSessions = speechSessions.size
        )
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
