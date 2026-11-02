package ru.voiceactivation.calibration

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.Intent
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Switch
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.ArrayDeque
import java.util.Locale

class CalibrationActivity : Activity() {
    private lateinit var repository: CalibrationRepository
    private lateinit var experiments: ExperimentRepository
    private lateinit var profiles: ProfileRepository
    private lateinit var profileName: EditText
    private lateinit var optimizerStatus: TextView
    private var recommendation: ThresholdRecommendation? = null
    private var windowPeakSnr = -100f
    private var windowPeakDbfs = -140f
    private var windowStartedAtMs = 0L
    @Volatile private var config = CalibrationConfig()
    private var capture: AudioCapture? = null
    private lateinit var status: TextView
    private lateinit var values: TextView
    private lateinit var result: TextView
    private lateinit var chart: LevelChartView
    private lateinit var triggerModeSwitch: Switch
    private lateinit var startButton: Button
    private lateinit var modelStatus: TextView
    private lateinit var transcriptView: TextView
    private val vosk = VoskEngine()
    private val commandMatcher = CommandMatcher()
    private val commandSequence = CommandSequenceTracker(commandMatcher)
    private lateinit var noiseButton: Button
    private lateinit var noiseStats: TextView
    private val noiseSamples = ArrayDeque<Float>()
    private var noiseCalibrationStartedAt = 0L
    private var starts = 0
    private var ends = 0
    private var acceptedSegments = 0
    private var lastFrameAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = CalibrationRepository(this)
        experiments = ExperimentRepository(this)
        profiles = ProfileRepository(this)
        config = repository.load()
        buildUi()
        loadStoredVoskModel()
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
        triggerModeSwitch = Switch(this).apply {
            text = "Использовать PEAK вместо RMS для триггера"
            isChecked = config.usePeakForTrigger
            setOnCheckedChangeListener { _, checked ->
                config = config.copy(usePeakForTrigger = checked)
                repository.save(config)
            }
        }
        content.addView(triggerModeSwitch)

        values = label("RMS: — dBFS     PEAK: — dBFS\nШум: — dBFS     SNR: — dB", 15f, true)
        values.setPadding(0, dp(10), 0, dp(10))
        content.addView(values)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        startButton = Button(this).apply { text = "НАЧАТЬ ИЗМЕРЕНИЕ"; setOnClickListener { toggleCapture() } }
        noiseButton = Button(this).apply { text = "ИЗМЕРИТЬ ФОН 5 С"; isEnabled = false; setOnClickListener { beginNoiseCalibration() } }
        buttons.addView(startButton, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(noiseButton, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(buttons)
        noiseStats = label("ФОНОВЫЙ ШУМ: ещё не измерен", 13f, false)
        content.addView(noiseStats)
        val modelButton = Button(this).apply {
            text = "ВЫБРАТЬ ПАПКУ РУССКОЙ VOSK-МОДЕЛИ"
            setOnClickListener { chooseVoskModel() }
        }
        content.addView(modelButton)
        modelStatus = label("Vosk: модель не загружена", 13f, false)
        content.addView(modelStatus)
        transcriptView = label("РАСПОЗНАННЫЙ ТЕКСТ: —\\nКОМАНДА: —", 16f, true)
        transcriptView.setPadding(0, dp(8), 0, dp(8))
        content.addView(transcriptView)

        content.addView(label("ПАРАМЕТРЫ ТРИГГЕРА", 18f, true).apply { setPadding(0, dp(16), 0, dp(4)) })
        addSlider(content, "Превышение шума для старта", 0f, 30f, config.relativeThresholdDb, " %.1f dB") { config = config.copy(relativeThresholdDb = it) }
        addSlider(content, "Абсолютный нижний порог", -90f, -6f, config.absoluteFloorDbfs, " %.1f dBFS") { config = config.copy(absoluteFloorDbfs = it) }
        addSlider(content, "Гистерезис выключения", 0f, 15f, config.hysteresisDb, " %.1f dB") { config = config.copy(hysteresisDb = it) }
        addSlider(content, "Минимальное превышение", 20f, 1000f, config.minTriggerMs.toFloat(), " %.0f ms") { config = config.copy(minTriggerMs = it.toInt()) }
        addSlider(content, "Минимальная длительность речи", 50f, 2000f, config.minSpeechMs.toFloat(), " %.0f ms") { config = config.copy(minSpeechMs = it.toInt()) }
        addSlider(content, "Тишина до завершения фразы", 100f, 2500f, config.silenceEndMs.toFloat(), " %.0f ms") { config = config.copy(silenceEndMs = it.toInt()) }
        addSlider(content, "Максимальная длина сегмента", 1000f, 15000f, config.maxSegmentMs.toFloat(), " %.0f ms") { config = config.copy(maxSegmentMs = it.toInt()) }
        addSlider(content, "Окно оценки фонового шума", 500f, 10000f, config.noiseWindowMs.toFloat(), " %.0f ms") { config = config.copy(noiseWindowMs = it.toInt()) }
        addSlider(content, "Предзапись до начала сегмента", 0f, 1000f, config.preRollMs.toFloat(), " %.0f ms") { config = config.copy(preRollMs = it.toInt()) }
        addSlider(content, "Скорость адаптации шума", 0.005f, 0.5f, config.noiseAdaptation, " %.3f") { config = config.copy(noiseAdaptation = it) }
        addSlider(content, "Пауза до повторного триггера", 0f, 5000f, config.cooldownMs.toFloat(), " %.0f ms") { config = config.copy(cooldownMs = it.toInt()) }
        content.addView(label("РАСПОЗНАВАНИЕ КОМАНД", 18f, true).apply { setPadding(0, dp(16), 0, dp(4)) })
        addSlider(content, "Минимальное сходство слова «Яша»", 0.3f, 1f, config.wakeSimilarityThreshold, " %.2f") { config = config.copy(wakeSimilarityThreshold = it) }
        addSlider(content, "Минимальное сходство действия", 0.3f, 1f, config.actionSimilarityThreshold, " %.2f") { config = config.copy(actionSimilarityThreshold = it) }
        addSlider(content, "Тайм-аут ожидания второго слова", 500f, 10000f, config.secondWordTimeoutMs.toFloat(), " %.0f ms") { config = config.copy(secondWordTimeoutMs = it.toInt()) }
        addSlider(content, "Максимальная пауза между словами", 100f, 5000f, config.maxInterWordPauseMs.toFloat(), " %.0f ms") { config = config.copy(maxInterWordPauseMs = it.toInt()) }

        content.addView(label("РЕЗУЛЬТАТЫ ИЗМЕРЕНИЯ", 18f, true).apply { setPadding(0, dp(16), 0, dp(4)) })
        result = label("Сегменты: 0 · Завершено: 0 · Достаточная длительность: 0", 14f, true)
        content.addView(result)
        content.addView(label("РАЗМЕТКА ДЛЯ АВТОПОДБОРА ПОРОГА", 16f, true).apply { setPadding(0, dp(12), 0, dp(4)) })
        val labels = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        labels.addView(Button(this).apply { text = "ЭТО БЫЛА РЕЧЬ"; setOnClickListener { markSample(true) } }, LinearLayout.LayoutParams(0, -2, 1f))
        labels.addView(Button(this).apply { text = "ЭТО БЫЛ ШУМ"; setOnClickListener { markSample(false) } }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(labels)
        content.addView(Button(this).apply { text = "РАССЧИТАТЬ РЕКОМЕНДУЕМЫЙ ПОРОГ"; setOnClickListener { showRecommendation() } })
        content.addView(Button(this).apply { text = "ПРИМЕНИТЬ РЕКОМЕНДАЦИЮ"; setOnClickListener { applyRecommendation() } })
        optimizerStatus = label("Размечено окон: 0", 13f, false)
        content.addView(optimizerStatus)
        content.addView(label("ПРОФИЛИ И ДАННЫЕ", 18f, true).apply { setPadding(0, dp(16), 0, dp(4)) })
        profileName = EditText(this).apply { hint = "Название профиля" }
        content.addView(profileName)
        val profileButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        profileButtons.addView(Button(this).apply { text = "СОХРАНИТЬ"; setOnClickListener { saveNamedProfile() } }, LinearLayout.LayoutParams(0, -2, 1f))
        profileButtons.addView(Button(this).apply { text = "ЗАГРУЗИТЬ"; setOnClickListener { loadNamedProfile() } }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(profileButtons)
        val dataButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dataButtons.addView(Button(this).apply { text = "ЭКСПОРТ JSON"; setOnClickListener { chooseExport() } }, LinearLayout.LayoutParams(0, -2, 1f))
        dataButtons.addView(Button(this).apply { text = "ИМПОРТ JSON"; setOnClickListener { chooseImport() } }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(dataButtons)
        content.addView(label("Сохранённых профилей: ${profiles.names().size} · размеченных окон: ${experiments.samples().size}", 12f, false))
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
            } },
            onSegment = { segment ->
                val pcm = segment.pcm
                if (!vosk.isLoaded()) {
                    runOnUiThread { transcriptView.text = "Vosk: выберите папку модели\\nСегмент: ${pcm.size / 16} ms" }
                } else {
                    Thread({
                        if (pcm.size / 16 < config.minSpeechMs) {
                            runOnUiThread { transcriptView.text = "СЕГМЕНТ ОТБРОШЕН: длительность ${pcm.size / 16} ms меньше ${config.minSpeechMs} ms" }
                            return@Thread
                        }
                        val recognized = vosk.recognizeSegment(pcm)?.text.orEmpty()
                        val sequence = commandSequence.accept(recognized, segment.startTimeMs, segment.endTimeMs, config)
                        runOnUiThread {
                            transcriptView.text = "РАСПОЗНАНО: ${recognized.ifBlank { "(пусто)" }}\\n" +
                                "КОМАНДА: ${sequence.message}\\n" +
                                "СТАТУС: ${sequence.state}"
                        }
                    }, "Vosk-Segment-Recognition").start()
                }
            }
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
        if (commandSequence.expire(android.os.SystemClock.elapsedRealtime(), config)) {
            transcriptView.text = "КОМАНДА НЕ ЗАВЕРШЕНА: истекло время ожидания второго слова"
        }
        values.text = String.format(
            Locale.US,
            "RMS: %.1f dBFS     PEAK: %.1f dBFS\nШум: %.1f dBFS     SNR: %.1f dB\nПорог: %.1f dBFS",
            frame.rmsDbfs, frame.peakDbfs, frame.noiseDbfs, frame.snrDb, frame.triggerLevelDbfs, frame.thresholdDbfs
        )
        chart.addFrame(frame.triggerLevelDbfs, frame.noiseDbfs, frame.thresholdDbfs)
        if (windowStartedAtMs == 0L) windowStartedAtMs = System.currentTimeMillis()
        windowPeakSnr = maxOf(windowPeakSnr, frame.snrDb)
        windowPeakDbfs = maxOf(windowPeakDbfs, frame.triggerLevelDbfs)
        if (frame.segment.started) {
            commandSequence.onSegmentStarted(android.os.SystemClock.elapsedRealtime())
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
                val mean = if (sorted.isEmpty()) median else sorted.average().toFloat()
                val p90 = if (sorted.isEmpty()) median else sorted[(sorted.size * 0.90).toInt().coerceIn(0, sorted.lastIndex)]
                val spread = if (sorted.size < 2) 0f else kotlin.math.sqrt(sorted.sumOf { sample ->
                    val delta = (sample - mean).toDouble(); delta * delta
                } / sorted.size).toFloat()
                noiseStats.text = String.format(Locale.US, "ФОН: медиана %.1f · среднее %.1f · P90 %.1f dBFS · разброс %.1f dB", median, mean, p90, spread)
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

    private fun markSample(speech: Boolean) {
        if (capture?.isRunning() != true) { Toast.makeText(this, "Сначала включите микрофон", Toast.LENGTH_SHORT).show(); return }
        val duration = if (windowStartedAtMs == 0L) 5000L else System.currentTimeMillis() - windowStartedAtMs
        experiments.addSample(windowPeakSnr, windowPeakDbfs, speech, duration, config)
        windowPeakSnr = -100f
        windowPeakDbfs = -140f
        windowStartedAtMs = System.currentTimeMillis()
        val count = experiments.samples().size
        optimizerStatus.text = "Размечено окон: $count · последнее: ${if (speech) "РЕЧЬ" else "ФОН"}"
    }

    private fun showRecommendation() {
        val r = experiments.recommend()
        recommendation = r
        optimizerStatus.text = if (r.ready) {
            "Рекомендация: +${r.relativeThresholdDb.toInt()} dB к шуму; пол: ${r.absoluteFloorDbfs.toInt()} dBFS\\n" +
                "Precision ${"%.2f".format(Locale.US, r.precision)} · Recall ${"%.2f".format(Locale.US, r.recall)} · F1 ${"%.2f".format(Locale.US, r.f1)}\\n" +
                "Ложных запусков/мин: ${"%.2f".format(Locale.US, r.falseTriggersPerMinute)}\\n" +
                "Выборка: речь ${r.positiveSamples}, фон ${r.negativeSamples}. Повторно проверьте профиль."
        } else r.explanation + " Размечено: ${r.positiveSamples} речевых / ${r.negativeSamples} фоновых окон."
    }

    private fun applyRecommendation() {
        val r = recommendation
        if (r == null || !r.ready) { Toast.makeText(this, "Сначала получите рекомендацию на достаточной выборке", Toast.LENGTH_LONG).show(); return }
        config = config.copy(relativeThresholdDb = r.relativeThresholdDb, absoluteFloorDbfs = r.absoluteFloorDbfs).normalized()
        repository.save(config)
        recreate()
    }

    private fun beginNoiseCalibration() {
        if (capture?.isRunning() != true) return
        noiseSamples.clear()
        noiseCalibrationStartedAt = System.currentTimeMillis()
        noiseButton.text = "ИЗМЕРЕНИЕ 5 c"
        Toast.makeText(this, "5 секунд сохраняйте тишину", Toast.LENGTH_SHORT).show()
    }

    private fun saveNamedProfile() {
        try {
            profiles.save(profileName.text.toString(), config)
            Toast.makeText(this, "Профиль сохранён", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) { Toast.makeText(this, t.message ?: "Ошибка профиля", Toast.LENGTH_LONG).show() }
    }

    private fun loadNamedProfile() {
        val name = profileName.text.toString().trim()
        val loaded = profiles.load(name)
        if (loaded == null) { Toast.makeText(this, "Профиль не найден", Toast.LENGTH_SHORT).show(); return }
        config = loaded
        repository.save(config)
        recreate()
    }

    private fun chooseExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "VoiceCalibration-profile.json")
        }
        startActivityForResult(intent, 43)
    }

    private fun chooseImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, 44)
    }

    private fun loadStoredVoskModel() {
        val directory = File(filesDir, "vosk-model")
        if (!directory.exists() || !directory.isDirectory) return
        Thread({
            try {
                val hasModel = directory.walkTopDown().any { it.isFile && it.name == "final.mdl" }
                if (!hasModel) return@Thread
                vosk.load(directory.absolutePath)
                runOnUiThread { modelStatus.text = "Vosk: сохранённая модель загружена" }
            } catch (t: Throwable) {
                runOnUiThread { modelStatus.text = "Vosk: ошибка повторной загрузки — ${t.message}" }
            }
        }, "Vosk-Model-Restore").start()
    }

    private fun chooseVoskModel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, 42)
    }

    @Deprecated("Using SAF tree result for Android 10+ compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == 43) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(profiles.exportJson(experiments, config).toByteArray(Charsets.UTF_8)) }
                    ?: throw IllegalStateException("Не удалось открыть файл для записи")
                Toast.makeText(this, "Экспорт завершён", Toast.LENGTH_LONG).show()
            } catch (t: Throwable) { Toast.makeText(this, "Экспорт: ${t.message}", Toast.LENGTH_LONG).show() }
            return
        }
        if (requestCode == 44) {
            try {
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalStateException("Не удалось прочитать файл")
                profiles.importJson(json, experiments)?.let { config = it; repository.save(config) }
                Toast.makeText(this, "Импорт завершён", Toast.LENGTH_LONG).show()
                recreate()
            } catch (t: Throwable) { Toast.makeText(this, "Импорт: ${t.message}", Toast.LENGTH_LONG).show() }
            return
        }
        if (requestCode != 42) return
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) {}
        modelStatus.text = "Vosk: копирование модели…"
        Thread({
            try {
                val source = DocumentFile.fromTreeUri(this, uri) ?: throw IllegalStateException("Не удалось открыть выбранную папку")
                val destination = File(filesDir, "vosk-model")
                destination.deleteRecursively()
                destination.mkdirs()
                source.listFiles().forEach { copyDocument(it, destination) }
                val hasAcousticModel = destination.walkTopDown().any { it.isFile && (it.name == "final.mdl" || it.name == "am.mdl") }
                if (!hasAcousticModel) throw IllegalArgumentException("В выбранной папке не найдена структура Vosk-модели (final.mdl)")
                vosk.load(destination.absolutePath)
                runOnUiThread { modelStatus.text = "Vosk: МОДЕЛЬ ЗАГРУЖЕНА · ${destination.walkTopDown().count { it.isFile }} файлов" }
            } catch (t: Throwable) {
                runOnUiThread { modelStatus.text = "Vosk: ОШИБКА — ${t.message ?: t.javaClass.simpleName}" }
            }
        }, "Vosk-Model-Import").start()
    }

    private fun copyDocument(source: DocumentFile, parent: File) {
        val name = source.name ?: return
        if (source.isDirectory) {
            val childDir = File(parent, name)
            childDir.mkdirs()
            source.listFiles().forEach { copyDocument(it, childDir) }
        } else {
            val target = File(parent, name)
            contentResolver.openInputStream(source.uri)?.use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
                ?: throw IllegalStateException("Не удалось прочитать файл $name")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) toggleCapture()
    }

    override fun onDestroy() {
        vosk.close()
        super.onDestroy()
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
