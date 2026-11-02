package ru.voiceactivation.calibration

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.exp

data class AudioFrame(
    val rmsDbfs: Float,
    val peakDbfs: Float,
    val noiseDbfs: Float,
    val snrDb: Float,
    val thresholdDbfs: Float,
    val segment: SegmentEvent,
    val frameDurationMs: Long
)

class AudioCapture(
    private val configProvider: () -> CalibrationConfig,
    private val onFrame: (AudioFrame) -> Unit,
    private val onError: (String) -> Unit
) {
    @Volatile private var running = false
    private var worker: Thread? = null
    private var recorder: AudioRecord? = null
    private val noise = NoiseEstimator()
    private val segmenter = SpeechSegmenter()

    fun start() {
        if (running) return
        val sampleRate = 16_000
        val minBytes = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBytes <= 0) {
            onError("AudioRecord не поддерживает 16 кГц mono PCM16 (код $minBytes)")
            return
        }
        val bufferBytes = maxOf(minBytes * 2, sampleRate * 2 / 5)
        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes
            )
            val audio = recorder ?: throw IllegalStateException("AudioRecord не создан")
            if (audio.state != AudioRecord.STATE_INITIALIZED) {
                audio.release()
                recorder = null
                onError("AudioRecord не инициализирован")
                return
            }
            noise.reset()
            segmenter.reset()
            audio.startRecording()
            running = true
            worker = Thread({ captureLoop(audio) }, "Calibration-AudioCapture").apply { start() }
        } catch (t: Throwable) {
            running = false
            recorder?.release()
            recorder = null
            onError("Ошибка запуска микрофона: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun captureLoop(audio: AudioRecord) {
        val frameSamples = 320
        val samples = ShortArray(frameSamples)
        try {
            while (running) {
                val count = audio.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                if (count <= 0) {
                    if (count < 0) onError("Ошибка чтения AudioRecord: $count")
                    continue
                }
                val frameMs = (count * 1000L) / 16_000L
                val rmsDb = AcousticMath.dbfs(AcousticMath.rms(samples, count))
                val peakDb = AcousticMath.dbfs(AcousticMath.peak(samples, count))
                val c = configProvider().normalized()
                val threshold = maxOf(noise.levelDbfs + c.relativeThresholdDb, c.absoluteFloorDbfs)
                val probableSpeech = rmsDb >= threshold - c.hysteresisDb
                val alpha = (1.0 - exp(-frameMs.toDouble() / c.noiseWindowMs.coerceAtLeast(1))) *
                    (c.noiseAdaptation / 0.08f).toDouble()
                noise.update(rmsDb, alpha.toFloat().coerceIn(0.001f, 0.5f), probableSpeech)
                val newThreshold = maxOf(noise.levelDbfs + c.relativeThresholdDb, c.absoluteFloorDbfs)
                val event = segmenter.process(rmsDb, newThreshold, c, frameMs)
                onFrame(
                    AudioFrame(
                        rmsDbfs = rmsDb,
                        peakDbfs = peakDb,
                        noiseDbfs = noise.levelDbfs,
                        snrDb = AcousticMath.snrDb(rmsDb, noise.levelDbfs),
                        thresholdDbfs = newThreshold,
                        segment = event,
                        frameDurationMs = frameMs
                    )
                )
            }
        } catch (t: Throwable) {
            if (running) onError("Ошибка аудиопотока: ${t.message ?: t.javaClass.simpleName}")
        } finally {
            try { audio.stop() } catch (_: Throwable) {}
        }
    }

    fun stop() {
        running = false
        try { recorder?.stop() } catch (_: Throwable) {}
        try { worker?.join(700) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        worker = null
        recorder?.release()
        recorder = null
    }

    fun isRunning() = running
}
