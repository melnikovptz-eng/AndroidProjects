package ru.voiceactivation.next

import android.content.Context
import kotlin.math.max

/**
 * Adaptive voice activity detector profile.
 * Background noise is measured locally and tracked only while a frame is classified as silence.
 */
internal class AdaptiveVad(context: Context) {
    private val prefs = context.getSharedPreferences("vad_profile", Context.MODE_PRIVATE)

    @Volatile private var noiseRms = prefs.getFloat("noise_rms", 0.002f).toDouble()
    @Volatile private var minRms = prefs.getFloat("min_rms", 0.006f).toDouble()
    @Volatile private var minPeak = prefs.getFloat("min_peak", 0.018f).toDouble()
    @Volatile private var multiplier = prefs.getFloat("noise_multiplier", 3.0f).toDouble()
    @Volatile private var silenceMs = prefs.getInt("silence_ms", 250)
    @Volatile private var minSpeechMs = prefs.getInt("min_speech_ms", 200)
    @Volatile private var maxSpeechMs = prefs.getInt("max_speech_ms", 5000)

    val startRms: Double get() = max(minRms, noiseRms * multiplier)
    val peakThreshold: Double get() = max(minPeak, noiseRms * multiplier * 2.0)
    val endSilenceSamples: Int get() = 16 * silenceMs
    val minSpeechSamples: Int get() = 16 * minSpeechMs
    val maxSpeechSamples: Int get() = 16 * maxSpeechMs

    fun classify(rms: Double, peak: Double): Boolean =
        rms >= startRms || peak >= peakThreshold

    /** Slow adaptation prevents speech frames from raising the floor. */
    fun observeSilence(rms: Double) {
        if (!rms.isFinite() || rms < 0.0) return
        val bounded = rms.coerceAtMost(max(minRms * 4.0, noiseRms * 2.0))
        noiseRms = (noiseRms * 0.98 + bounded * 0.02).coerceIn(0.00005, 0.25)
    }

    fun calibrateNoise(measuredRms: Double): Boolean {
        if (!measuredRms.isFinite() || measuredRms <= 0.0 || measuredRms > 0.5) return false
        noiseRms = measuredRms.coerceIn(0.00005, 0.25)
        prefs.edit().putFloat("noise_rms", noiseRms.toFloat()).apply()
        return true
    }

    fun save() {
        prefs.edit()
            .putFloat("noise_rms", noiseRms.toFloat())
            .putFloat("min_rms", minRms.toFloat())
            .putFloat("min_peak", minPeak.toFloat())
            .putFloat("noise_multiplier", multiplier.toFloat())
            .putInt("silence_ms", silenceMs)
            .putInt("min_speech_ms", minSpeechMs)
            .putInt("max_speech_ms", maxSpeechMs)
            .apply()
    }

    fun summary(): String =
        "АДАПТИВНЫЙ VAD\\n" +
        "Фоновый RMS: ${"%.6f".format(java.util.Locale.US, noiseRms)}\\n" +
        "Порог старта RMS: ${"%.6f".format(java.util.Locale.US, startRms)}\\n" +
        "Порог PEAK: ${"%.6f".format(java.util.Locale.US, peakThreshold)}\\n" +
        "Множитель шума: ${"%.2f".format(java.util.Locale.US, multiplier)}\\n" +
        "Пауза завершения: ${silenceMs} мс; минимальная речь: ${minSpeechMs} мс; максимум фразы: ${maxSpeechMs} мс"
}
