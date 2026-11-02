package ru.voiceactivation.calibration

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.exp

data class CapturedSpeechSegment(val pcm: ShortArray, val startTimeMs: Long, val endTimeMs: Long)

data class AudioFrame(
    val rmsDbfs: Float,
    val peakDbfs: Float,
    val triggerLevelDbfs: Float,
    val noiseDbfs: Float,
    val snrDb: Float,
    val thresholdDbfs: Float,
    val segment: SegmentEvent,
    val frameDurationMs: Long
)

class AudioCapture(
    private val configProvider: () -> CalibrationConfig,
    private val onFrame: (AudioFrame) -> Unit,
    private val onError: (String) -> Unit,
    private val onSegment: (CapturedSpeechSegment) -> Unit = {}
) {
    @Volatile private var running = false
    @Volatile private var pendingNoiseBaseline: Float? = null
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
        val preRoll = java.util.ArrayDeque<ShortArray>()
        val segmentFrames = ArrayList<ShortArray>()
        var preRollSamples = 0
        var segmentActive = false
        var segmentStartedAtMs = 0L
        try {
            while (running) {
                val count = audio.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                if (count <= 0) {
                    if (count < 0) onError("Ошибка чтения AudioRecord: $count")
                    continue
                }
                pendingNoiseBaseline?.let { noise.reset(it); pendingNoiseBaseline = null }
                val frameMs = (count * 1000L) / 16_000L
                val rmsDb = AcousticMath.dbfs(AcousticMath.rms(samples, count))
                val peakDb = AcousticMath.dbfs(AcousticMath.peak(samples, count))
                val c = configProvider().normalized()
                val frameCopy = samples.copyOf(count)
                preRoll.addLast(frameCopy)
                preRollSamples += count
                val maxPreRollSamples = 16_000L * c.preRollMs / 1000L
                while (preRoll.isNotEmpty() && preRollSamples > maxPreRollSamples) {
                    preRollSamples -= preRoll.removeFirst().size
                }
                val triggerLevel = if (c.usePeakForTrigger) peakDb else rmsDb
                val threshold = maxOf(noise.levelDbfs + c.relativeThresholdDb, c.absoluteFloorDbfs)
                val probableSpeech = triggerLevel >= threshold - c.hysteresisDb
                val alpha = (1.0 - exp(-frameMs.toDouble() / c.noiseWindowMs.coerceAtLeast(1))) *
                    (c.noiseAdaptation / 0.08f).toDouble()
                noise.update(triggerLevel, alpha.toFloat().coerceIn(0.001f, 0.5f), probableSpeech)
                val newThreshold = maxOf(noise.levelDbfs + c.relativeThresholdDb, c.absoluteFloorDbfs)
                val event = segmenter.process(triggerLevel, newThreshold, c, frameMs)
                if (event.started) {
                    segmentStartedAtMs = android.os.SystemClock.elapsedRealtime()
                    segmentFrames.clear()
                    preRoll.forEach { segmentFrames.add(it) }
                    if (segmentFrames.isEmpty()) segmentFrames.add(frameCopy)
                    segmentActive = true
                } else if (segmentActive) {
                    segmentFrames.add(frameCopy)
                }
                if (event.ended && segmentActive) {
                    val total = segmentFrames.sumOf { it.size }
                    val joined = ShortArray(total)
                    var offset = 0
                    segmentFrames.forEach { part -> part.copyInto(joined, offset); offset += part.size }
                    segmentActive = false
                    segmentFrames.clear()
                    onSegment(CapturedSpeechSegment(joined, segmentStartedAtMs, android.os.SystemClock.elapsedRealtime()))
                }
                onFrame(
                    AudioFrame(
                        rmsDbfs = rmsDb,
                        peakDbfs = peakDb,
                        triggerLevelDbfs = triggerLevel,
                        noiseDbfs = noise.levelDbfs,
                        snrDb = AcousticMath.snrDb(triggerLevel, noise.levelDbfs),
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

    fun setNoiseBaseline(dbfs: Float) { pendingNoiseBaseline = dbfs.coerceIn(-100f, -10f) }

    fun isRunning() = running
}
