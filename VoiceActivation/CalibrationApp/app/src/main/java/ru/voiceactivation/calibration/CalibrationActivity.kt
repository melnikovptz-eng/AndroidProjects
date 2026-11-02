package ru.voiceactivation.calibration

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.ArrayDeque
import java.util.Locale

class CalibrationActivity : Activity() {
    private lateinit var repository: CalibrationRepository
    private var config = CalibrationConfig()
    private var capture: AudioCapture? = null
    private lateinit var status: TextView
    private lateinit var values: TextView
    private lateinit var result: TextView
    private lateinit var chart: LevelChartView
    private lateinit var startButton: Button
    private lateinit var noiseButton: Button
    private val noiseSamples = ArrayDeque<Float>()
    private var noiseCalibrationStartedAt = 0L
    private var starts = 0
    private var ends = 0
    private var acceptedSegments = 0
    private var lastFrameAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = CalibrationRepository(this)
        config = repository.load()
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
            setBackgroundColor(0xfff5f7fa.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        content.addView(label("VOICE ACTIVATION — КАЛИБРОВКА", 22f, true))
        content.addView(label("Живые измерения микрофона и подбор порогов", 14f, false))

        status = label("МИКРОФОН: ОСТАНОВЛЕН", 16f, true)
        status.setPadding(0, dp(12), 0, dp(8))
        content.addView(status)

        chart = LevelChartView(this).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(190)) }
        content.addView(chart)
        content.addView(label("Синий — сигнал · зелёный — фон · красный — порог", 12f, false))

        values = label("RMS: — dBFS     PEAK: — dBFS\nШум: — dBFS     SNR: — dB", 15f, true)
        values.setPadding(0, dp(10), 0, dp(10))
        content.addView(values)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        startButton = Button(this).apply { text = "НАЧАТЬ ИЗМЕРЕНИЕ"; setOnClickListener { toggleCapture() } }
        noiseButton = Button(this).apply { text = "ИЗМЕРИТЬ ФОН 5 С"; isEnabled = false; setOnClickListener { beginNoiseCalibration() } }
        buttons.addView(startButton, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(noiseButton, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(buttons)

        content.addView(label("ПАРАМЕТРЫ ТРИГГЕРА", 18f, true).apply { setPadding(0, dp(16), 0, dp(4)) })
        addSlider(content, "Превышение шума для старта", 0f, 30f, config.relativeThresholdDb, " %.1f dB") { config = config.copy(relativeThresholdDb = it) }
        addSlider(content, "Абсолютный нижний порог", -90f, -6f, config.absoluteFloorDbfs, " %.1f dBFS") { config = config.copy(absoluteFloorDbfs = it) }
        addSlider(content, "Гистерезис выключения", 0f, 15f, config.hysteresisDb, " %.1f dB") { config = config.copy(hysteresisDb = it) }
        addSlider(content, "Минимальное превышение", 20f, 1000f, config.minTriggerMs.toFloat(), " %.0f ms") { config = config.copy(minTriggerMs = it.toInt()) }
        addSlider(content, "Минимальная длительность речи", 50f, 2000f, config.minSpeechMs.toFloat(), " %.0f ms") { config = config.copy(minSpeechMs = it.toInt()) }
        addSlider(content, "Тишина до завершения фразы", 100f, 2500f, config.silenceEndMs.toFloat(), " %.0f ms") { config = config.copy(silenceEndMs = it.toInt()) }
        addSlider(content, "Максимальная длина сегмента", 1000f, 15000f, config.maxSegmentMs.toFloat(), " %.0f ms") { config = config.copy(maxSegmentMs = it.toInt()) }
        addSlider(content, "Окно оценки фонового шума", 500f, 10000f, config.noiseWindowMs.toFloat(), " %.0f ms") { config = config.copy(noiseWindowMs = it.toInt()) }
        addSlider(content, "Скорость адаптации шума", 0.005f, 0.5f, config.noiseAdaptation, " %.3f") { config = config.copy(noiseAdaptation = it) }
        addSlider(content, "Пауза до повторного триггера", 0f, 5000f, config.cooldownMs.toFloat(), " %.0f ms") { config = config.copy(cooldownMs = it.toInt()) }

        content.addView(label("РЕЗУЛЬТАТЫ ИЗМЕРЕНИЯ", 18f, true).apply { setPadding(0, dp(16), 0, dp(4)) })
        result = label("Сегменты: 0 · Завершено: 0 · Достаточная длительность: 0", 14f, true)
        content.addView(result)
        val reset = Button(this).apply {
            text = "СБРОСИТЬ СЧЁТЧИКИ И ПАРАМЕТРЫ"
            setOnClickListener {
                starts = 0; ends = 0; acceptedSegments = 0
                config = CalibrationConfig()
                repository.save(config)
                recreate()
            }
        }
        content.addView(reset)
        content.addView(label("Измерения локальные. Аудио не сохраняется. Параметры сохраняются автоматически.", 12f, false).apply { setPadding(0, dp(10), 0, 0) })

        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun addSlider(
        parent: LinearLayout,
        title: String,
        min: Float,
        max: Float,
        initial: Float,
        format: String,
        update: (Float) -> Unit
    ) {
        val caption = label("", 14f, true)
        parent.addView(caption)
        val seek = SeekBar(this)
        seek.max = 1000
        fun valueAt(progress: Int) = min + (max - min) * progress / seek.max
        fun progressAt(value: Float) = (((value - min) / (max - min)) * seek.max).toInt().coerceIn(0, seek.max)
        seek.progress = progressAt(initial)
        fun refresh(value: Float) {
            caption.text = title + String.format(Locale.US, format, value)
        }
        refresh(initial)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = valueAt(progress)
                refresh(v)
                if (fromUser) {
                    update(v)
                    config = config.normalized()
                    repository.save(config)
                }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        parent.addView(seek)
    }

    private fun toggleCapture() {
        if (capture?.isRunning() == true) {
            capture?.stop()
            capture = null
            startButton.text = "НАЧАТЬ ИЗМЕРЕНИЕ"
            noiseButton.isEnabled = false
            status.text = "МИКРОФОН: ОСТАНОВЛЕН"
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 41)
            return
        }
        capture = AudioCapture(
            configProvider = { config },
            onFrame = { frame -> runOnUiThread { renderFrame(frame) } },
            onError = { message -> runOnUiThread {
                status.text = "МИКРОФОН: ОШИБКА — $message"
                capture?.stop()
                startButton.text = "НАЧАТЬ ИЗМЕРЕНИЕ"
                noiseButton.isEnabled = false
            } }
        )
        capture?.start()
        if (capture?.isRunning() == true) {
            startButton.text = "ОСТАНОВИТЬ"
            noiseButton.isEnabled = true
            status.text = "МИКРОФОН: СЛУШАЕТ"
        }
    }

    private fun renderFrame(frame: AudioFrame) {
        if (capture?.isRunning() != true) return
        values.text = String.format(
            Locale.US,
            "RMS: %.1f dBFS     PEAK: %.1f dBFS\nШум: %.1f dBFS     SNR: %.1f dB\nПорог: %.1f dBFS",
            frame.rmsDbfs, frame.peakDbfs, frame.noiseDbfs, frame.snrDb, frame.thresholdDbfs
        )
        chart.addFrame(frame.rmsDbfs, frame.noiseDbfs, frame.thresholdDbfs)
        if (frame.segment.started) {
            starts++
            status.text = "МИКРОФОН: СЕГМЕНТ НАЧАЛСЯ"
        }
        if (frame.segment.ended) {
            ends++
            if (frame.segment.segmentDurationMs >= config.minSpeechMs) acceptedSegments++
            status.text = "МИКРОФОН: СЛУШАЕТ"
        }
        result.text = "Сегменты: $starts · Завершено: $ends · Достаточная длительность: $acceptedSegments"
        if (noiseCalibrationStartedAt > 0L) {
            noiseSamples.addLast(frame.rmsDbfs)
            val elapsed = System.currentTimeMillis() - noiseCalibrationStartedAt
            if (elapsed >= 5000L) {
                val sorted = noiseSamples.sorted()
                val median = if (sorted.isEmpty()) -65f else sorted[sorted.size / 2]
                capture?.setNoiseBaseline(median)
                noiseCalibrationStartedAt = 0L
                noiseSamples.clear()
                noiseButton.text = "ИЗМЕРИТЬ ФОН 5 С"
                Toast.makeText(this, "Базовый шум установлен: %.1f dBFS".format(Locale.US, median), Toast.LENGTH_LONG).show()
            } else {
                noiseButton.text = "ИЗМЕРЕНИЕ ${(5 - elapsed / 1000).coerceAtLeast(0)} c"
            }
        }
        lastFrameAt = System.currentTimeMillis()
    }

    private fun beginNoiseCalibration() {
        if (capture?.isRunning() != true) return
        noiseSamples.clear()
        noiseCalibrationStartedAt = System.currentTimeMillis()
        noiseButton.text = "ИЗМЕРЕНИЕ 5 c"
        Toast.makeText(this, "5 секунд сохраняйте тишину", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) toggleCapture()
    }

    override fun onPause() {
        capture?.stop()
        capture = null
        super.onPause()
    }

    private fun label(textValue: String, size: Float, bold: Boolean) = TextView(this).apply {
        text = textValue
        textSize = size
        setTextColor(0xff18212b.toInt())
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(5), 0, dp(5))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
