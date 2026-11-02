package ru.voiceactivation.next

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.*
import android.graphics.Typeface
import kotlin.concurrent.thread
import kotlin.math.*
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var command: EditText
    private lateinit var matcher: AcousticMatcher
    private lateinit var adaptiveVad: AdaptiveVad
    private lateinit var testButton: Button
    private lateinit var battleButton: Button
    @Volatile private var battleMode = false
    private lateinit var elapsedText: TextView
    private lateinit var remainingText: TextView

    private val mainHandler = Handler(Looper.getMainLooper())
    private val testActive = AtomicBoolean(false)
    @Volatile private var testRecorder: AudioRecord? = null
    @Volatile private var testStartedAt = 0L
    @Volatile private var testDeadline = 0L
    @Volatile private var stopMessage = "Проверка завершена."
    private var pendingTestPermission = false
    private var pendingBattleMode = false

    private val timerTick = object : Runnable {
        override fun run() {
            if (!testActive.get() || battleMode) return
            val now = SystemClock.elapsedRealtime()
            val elapsed = (now - testStartedAt).coerceAtLeast(0L)
            val remaining = (testDeadline - now).coerceAtLeast(0L)
            elapsedText.text = "Прошло: " + clock(elapsed)
            remainingText.text = "Осталось: " + clock(remaining)
            if (remaining == 0L) {
                stopMessage = "Проверка завершена: 15 секунд истекли."
                stopTest(false)
            } else {
                mainHandler.postDelayed(this, 100L)
            }
        }
    }

    private fun ui(s: String) {
        runOnUiThread { status.text = s }
    }

    private fun appendResult(s: String) {
        getSharedPreferences("battle_journal", MODE_PRIVATE).edit()
            .putString("entries", (getSharedPreferences("battle_journal", MODE_PRIVATE).getString("entries", "") + "\n" + s).takeLast(24000))
            .apply()
        runOnUiThread {
            val old = status.text.toString()
            val combined = if (old.length > 6500) old.takeLast(4500) else old
            status.text = (combined + "\n" + s).takeLast(7000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        matcher = AcousticMatcher(this)
        adaptiveVad = AdaptiveVad(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "VOICE ACTIVATION"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Команды: MFCC + DTW. Проверка владельца выполняется отдельным акустическим профилем."
            textSize = 14f
            setPadding(0, 8, 0, 8)
        })

        command = EditText(this).apply { hint = "Название команды" }
        root.addView(command)

        fun button(label: String, action: () -> Unit): Button {
            val b = Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { action() }
            }
            root.addView(b)
            return b
        }

        button("Записать профиль владельца") {
            capture { audio ->
                matcher.enrollOwner(audio)
                ui("Профиль владельца записан.")
            }
        }

        button("Записать образец команды") {
            val name = command.text.toString().trim()
            if (name.isBlank()) {
                ui("Введите название команды.")
                return@button
            }
            capture { audio ->
                matcher.enrollCommand(name, audio)
                ui("Образец команды «" + name + "» сохранён. Всего команд: " + matcher.commands().size)
            }
        }

        button("Калибровка: записать правильную команду") {
            val expected = command.text.toString().trim()
            if (expected.isBlank() || expected !in matcher.commands()) {
                ui("Сначала выберите существующую команду в поле названия.")
            } else recordCalibrationSample(expected, true)
        }

        button("Калибровка: записать постороннюю фразу своим голосом") {
            recordCalibrationSample(null, true)
        }

        button("Калибровка: записать чужой голос") {
            recordCalibrationSample(null, false)
        }

        button("Рассчитать и проверить пороги") {
            runCalibration()
        }

        button("Измерить фоновый шум (4 секунды)") { calibrateNoise() }

        button("Показать параметры обнаружения речи") { ui(adaptiveVad.summary()) }

        button("Показать данные калибровки") {
            ui(CalibrationEngine.summary(CalibrationEngine.load(this)))
        }

        testButton = button("Проверить голосовую команду") {
            if (testActive.get()) {
                stopMessage = "Проверка отменена пользователем."
                stopTest(true)
            } else {
                requestTestPermissionAndStart(false)
            }
        }

        battleButton = button("Боевой режим — начать") {
            if (testActive.get() && battleMode) {
                stopMessage = "Боевой режим остановлен пользователем."
                stopTest(true)
            } else if (!testActive.get()) {
                requestTestPermissionAndStart(true)
            }
        }

        val timerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 4)
        }
        elapsedText = TextView(this).apply { text = "Прошло: 00:00"; textSize = 15f }
        remainingText = TextView(this).apply {
            text = "Осталось: 00:15"
            textSize = 15f
            setPadding(28, 0, 0, 0)
        }
        timerRow.addView(elapsedText, LinearLayout.LayoutParams(0, -2, 1f))
        timerRow.addView(remainingText, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(timerRow)

        button("Показать журнал боевого режима") {
            val saved = getSharedPreferences("battle_journal", MODE_PRIVATE).getString("entries", "")
            ui(if (saved.isNullOrBlank()) "Журнал пока пуст." else saved)
        }

        button("Показать команды") {
            ui("Команды: " + matcher.commands().ifEmpty { listOf("нет") }.joinToString())
        }

        status = TextView(this).apply {
            text = "Готово. Запишите профиль владельца и образцы команд. Пороги: DTW < ${matcher.dtwThreshold()}, owner cosine > ${matcher.ownerThreshold()}."
            textSize = 15f
            setPadding(0, 18, 0, 0)
        }
        root.addView(status)
        setContentView(ScrollView(this).apply { addView(root) })

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        }
    }

    private fun requestTestPermissionAndStart(isBattle: Boolean) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingTestPermission = true
            pendingBattleMode = isBattle
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            ui("Разрешите доступ к микрофону для проверки.")
            return
        }
        startTest(isBattle)
    }

    @Deprecated("Platform permission callback")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_MIC) return
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (pendingTestPermission) {
            pendingTestPermission = false
            if (granted) startTest(pendingBattleMode) else ui("Доступ к микрофону не предоставлен.")
        }
    }

    private fun startTest(isBattle: Boolean) {
        if (matcher.commands().isEmpty()) {
            ui("Сначала запишите хотя бы один образец команды.")
            return
        }
        if (testActive.get()) return

        battleMode = isBattle
        testStartedAt = SystemClock.elapsedRealtime()
        testDeadline = if (isBattle) Long.MAX_VALUE else testStartedAt + TEST_DURATION_MS
        stopMessage = if (isBattle) "Боевой режим остановлен." else "Проверка завершена."
        testActive.set(true)
        testButton.text = if (isBattle) "Проверка голосовой команды недоступна во время боевого режима" else "Отменить проверку голосовой команды"
        battleButton.text = if (isBattle) "Остановить боевой режим" else "Боевой режим — начать"
        testButton.isEnabled = !isBattle
        elapsedText.text = "Прошло: 00:00"
        remainingText.text = "Осталось: 00:15"
        status.text = if (isBattle) "БОЕВОЙ РЕЖИМ АКТИВЕН. Произносите любые фразы подряд. После каждой паузы будет показан результат. Пороги не изменяются." else "Проверка запущена на 15 секунд. Произносите команды с любыми паузами."
        mainHandler.removeCallbacks(timerTick)
        if (!isBattle) mainHandler.post(timerTick)

        thread(name = "VoiceCommandContinuousTest") {
            runContinuousTest()
        }
    }

    private fun stopTest(manual: Boolean) {
        if (!testActive.compareAndSet(true, false)) return
        mainHandler.removeCallbacks(timerTick)
        try {
            testRecorder?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
            }
        } catch (_: Exception) { }
        runOnUiThread {
            val stoppedInBattleMode = battleMode
            testButton.text = "Проверить голосовую команду"
            testButton.isEnabled = true
            battleButton.text = "Боевой режим — начать"
            val elapsed = (SystemClock.elapsedRealtime() - testStartedAt).coerceAtLeast(0L)
            elapsedText.text = "Прошло: " + clock(elapsed)
            remainingText.text = if (stoppedInBattleMode) "Без ограничения времени" else "Осталось: " + clock((TEST_DURATION_MS - elapsed).coerceIn(0L, TEST_DURATION_MS))
            battleMode = false
            appendResult(stopMessage)
        }
    }

    private fun runContinuousTest() {
        val rate = SAMPLE_RATE
        var recorder: AudioRecord? = null
        val utterance = ArrayList<Double>(rate * 3)
        val preRoll = ArrayDeque<Double>()
        var speechActive = false
        var silenceSamples = 0
        var processedCount = 0
        var matchedCount = 0
        var unmatchedCount = 0
        var ownerAcceptedCount = 0
        var ownerRejectedCount = 0

        fun processUtterance() {
            if (speechActive && utterance.isNotEmpty()) {
                processedCount++
                val samples = utterance.toDoubleArray()
                if (samples.size < adaptiveVad.minSpeechSamples) {
                    unmatchedCount++
                    appendResult(
                        "Фраза #" + processedCount +
                            ": обнаружена, но слишком короткая для надёжного сравнения (" +
                            samples.size + " отсчётов). DTW: не оценён; владелец: не оценён."
                    )
                } else {
                    val match = try { matcher.match(samples) } catch (_: Exception) { null }
                    val ownerScore = try { matcher.ownerSimilarity(samples) } catch (_: Exception) { null }
                    val ownerAccepted = ownerScore != null && ownerScore > matcher.ownerThreshold()
                    if (ownerScore != null) {
                        if (ownerAccepted) ownerAcceptedCount++ else ownerRejectedCount++
                    }

                    val message = if (match == null) {
                        unmatchedCount++
                        "ФРАЗА #" + processedCount + ": НЕ ПРОШЛА — совпадение с образцами не найдено." +
                            " | DTW: нет результата" +
                            " | владелец: " + ownerLabel(ownerScore, ownerAccepted)
                    } else if (match.accepted) {
                        matchedCount++
                        "ФРАЗА #" + processedCount + ": ПРОШЛА ✓ — «" + match.command + "»" +
                            " | DTW = " + fmt(match.bestDistance) +
                            " | порог DTW < " + fmt(matcher.dtwThreshold()) +
                            " | второе = " + fmt(match.secondDistance) +
                            " | отрыв: " + (if (match.secondDistance.isInfinite()) "единственный кандидат" else
                                if (match.bestDistance < match.secondDistance * matcher.marginThreshold()) "пройден (< " + fmt(matcher.marginThreshold()) + ")" else "не пройден") +
                            " | владелец: " + ownerLabel(ownerScore, ownerAccepted)
                    } else {
                        unmatchedCount++
                        "ФРАЗА #" + processedCount + ": НЕ ПРОШЛА — ближайшая команда «" + match.command +
                            "», порог не пройден" +
                            " | DTW = " + fmt(match.bestDistance) +
                            " | порог DTW < " + fmt(matcher.dtwThreshold()) +
                            " | второе = " + fmt(match.secondDistance) +
                            " | владелец: " + ownerLabel(ownerScore, ownerAccepted)
                    }
                    appendResult(message)
                }
            }
            utterance.clear()
            speechActive = false
            silenceSamples = 0
        }

        try {
            val minBytes = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            check(minBytes > 0) { "AudioRecord buffer error: " + minBytes }
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBytes * 2, rate * 2)
            )
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Не удалось открыть микрофон." }
            testRecorder = recorder
            recorder.startRecording()

            val frame = ShortArray(FRAME_SAMPLES)
            while (testActive.get() && (battleMode || SystemClock.elapsedRealtime() < testDeadline)) {
                val count = recorder.read(frame, 0, frame.size)
                if (count <= 0) {
                    if (!testActive.get()) break
                    continue
                }
                var squareSum = 0.0
                var peak = 0.0
                for (i in 0 until count) {
                    val v = frame[i] / 32768.0
                    squareSum += v * v
                    peak = maxOf(peak, abs(v))
                }
                val rms = sqrt(squareSum / count)
                val voiced = adaptiveVad.classify(rms, peak)
                if (!voiced) adaptiveVad.observeSilence(rms)

                if (voiced) {
                    if (!speechActive) {
                        utterance.clear()
                        utterance.addAll(preRoll)
                        preRoll.clear()
                    }
                    speechActive = true
                    silenceSamples = 0
                    for (i in 0 until count) utterance.add(frame[i] / 32768.0)
                } else if (speechActive) {
                    for (i in 0 until count) utterance.add(frame[i] / 32768.0)
                    silenceSamples += count
                    if (silenceSamples >= adaptiveVad.endSilenceSamples) processUtterance()
                } else {
                    for (i in 0 until count) {
                        preRoll.addLast(frame[i] / 32768.0)
                        if (preRoll.size > SAMPLE_RATE / 5) preRoll.removeFirst()
                    }
                }

                if (utterance.size >= adaptiveVad.maxSpeechSamples) processUtterance()
            }
            if (speechActive && utterance.isNotEmpty()) processUtterance()
        } catch (e: Exception) {
            if (testActive.get()) appendResult("Ошибка проверки: " + (e.message ?: e.javaClass.simpleName))
        } finally {
            testRecorder = null
            try {
                recorder?.let {
                    if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
                    it.release()
                }
            } catch (_: Exception) { }
            if (testActive.compareAndSet(true, false)) {
                mainHandler.removeCallbacks(timerTick)
                runOnUiThread {
                    testButton.text = "Проверить голосовую команду"
                    testButton.isEnabled = true
                    battleButton.text = "Боевой режим — начать"
                    battleMode = false
                    val elapsed = (SystemClock.elapsedRealtime() - testStartedAt).coerceIn(0L, TEST_DURATION_MS)
                    elapsedText.text = "Прошло: " + clock(elapsed)
                    remainingText.text = "Осталось: " + clock((TEST_DURATION_MS - elapsed).coerceAtLeast(0L))
                    appendResult(stopMessage)
                }
            }
            appendResult(
                "ИТОГ ТЕСТА: обнаружено фраз = " + processedCount +
                    " | совпало с командами = " + matchedCount +
                    " | не совпало / не принято = " + unmatchedCount +
                    " | владелец подтверждён = " + ownerAcceptedCount +
                    " | владелец не подтверждён = " + ownerRejectedCount
            )
        }
    }

    private fun calibrateNoise() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            ui("Разрешите доступ к микрофону.")
            return
        }
        if (testActive.get()) {
            ui("Остановите тест или боевой режим перед измерением шума.")
            return
        }
        ui("Измерение фонового шума: 4 секунды. Не говорите и не трогайте телефон.")
        thread(name = "VoiceActivationNoiseCalibration") {
            var recorder: AudioRecord? = null
            try {
                val rate = SAMPLE_RATE
                val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(min > 0) { "AudioRecord buffer error: " + min }
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(min * 2, rate)
                )
                check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Не удалось открыть микрофон." }
                recorder.startRecording()
                val frame = ShortArray(FRAME_SAMPLES)
                val values = ArrayList<Double>(200)
                val until = SystemClock.elapsedRealtime() + 4000L
                while (SystemClock.elapsedRealtime() < until) {
                    val n = recorder.read(frame, 0, frame.size)
                    if (n <= 0) continue
                    var sum = 0.0
                    for (i in 0 until n) {
                        val v = frame[i] / 32768.0
                        sum += v * v
                    }
                    values.add(sqrt(sum / n))
                }
                check(values.size >= 20) { "Недостаточно аудиоданных для измерения шума." }
                val sorted = values.sorted()
                val measured = sorted[(sorted.size * 0.8).toInt().coerceAtMost(sorted.lastIndex)]
                check(adaptiveVad.calibrateNoise(measured)) { "Некорректное измерение фонового шума." }
                adaptiveVad.save()
                ui("Фоновый шум измерен и сохранён.\\n" + adaptiveVad.summary())
            } catch (e: Exception) {
                ui("Ошибка калибровки шума: " + (e.message ?: e.javaClass.simpleName))
            } finally {
                try {
                    recorder?.let {
                        if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
                        it.release()
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private fun recordCalibrationSample(expectedCommand: String?, owner: Boolean) {
        if (matcher.commands().isEmpty()) {
            ui("Сначала запишите образцы команд.")
            return
        }
        capture { audio ->
            val measured = matcher.match(audio)
            val ownerScore = matcher.ownerSimilarity(audio)
            if (ownerScore == null) {
                ui("Сначала запишите профиль владельца. Калибровочный пример не сохранён.")
                return@capture
            }
            if (measured == null) {
                ui("Не удалось измерить DTW. Повторите запись.")
                return@capture
            }
            val sample = CalibrationEngine.Sample(
                expectedCommand = expectedCommand,
                owner = owner,
                predictedCommand = measured.command,
                bestDistance = measured.bestDistance,
                marginRatio = measured.marginRatio,
                ownerCosine = ownerScore
            )
            CalibrationEngine.append(this, sample)
            ui("Калибровочный пример сохранён: " +
                (expectedCommand ?: "посторонняя речь") +
                ", говорящий=" + (if (owner) "владелец" else "другой") +
                ". Всего: " + CalibrationEngine.load(this).size)
        }
    }

    private fun runCalibration() {
        val result = CalibrationEngine.fit(CalibrationEngine.load(this))
        if (!result.success) {
            ui("КАЛИБРОВКА НЕ ВЫПОЛНЕНА\n" + result.message)
            return
        }
        matcher.applyThresholds(result.dtwThreshold, result.ownerThreshold, result.marginThreshold)
        ui(
            "КАЛИБРОВКА ЗАВЕРШЕНА\n" +
            "DTW < " + fmt(result.dtwThreshold) + "\n" +
            "Owner cosine > " + fmt(result.ownerThreshold) + "\n" +
            "Отрыв best/second < " + fmt(result.marginThreshold) + "\n" +
            "Проверочная выборка: команды " + fmt(result.commandValidation * 100) + "%, " +
            "владелец " + fmt(result.ownerValidation * 100) + "%\n" +
            "Образцов: " + result.totalSamples + ". Пороги сохранены и применяются в тесте и боевом режиме."
        )
    }

    private fun ownerLabel(score: Double?, accepted: Boolean): String {
        if (score == null) return "профиль не найден"
        return (if (accepted) "ПРОЙДЕНА" else "НЕ ПРОЙДЕНА") +
            " (MFCC cosine = " + fmt(score) + ", порог > " + fmt(matcher.ownerThreshold()) + ")"
    }

    private fun fmt(value: Double): String =
        if (value.isInfinite() || value.isNaN()) "—" else String.format(Locale.US, "%.3f", value)

    private fun clock(ms: Long): String {
        val seconds = (ms / 1000L).coerceAtLeast(0L)
        return String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L)
    }

    private fun capture(done: (DoubleArray) -> Unit) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            ui("Разрешите доступ к микрофону.")
            return
        }
        ui("Слушаю 2,5 секунды…")
        thread(name = "VoiceActivationCapture") {
            try {
                val rate = SAMPLE_RATE
                val min = AudioRecord.getMinBufferSize(
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                check(min > 0) { "AudioRecord buffer error: " + min }
                val rec = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(min, rate * 2)
                )
                val pcm = ShortArray(rate * ENROLLMENT_DURATION_MS / 1000)
                try {
                    rec.startRecording()
                    var n = 0
                    while (n < pcm.size) {
                        val k = rec.read(pcm, n, pcm.size - n)
                        check(k > 0) { "Ошибка чтения микрофона: " + k }
                        n += k
                    }
                } finally {
                    if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) rec.stop()
                    rec.release()
                }
                if (pcm.maxOf { abs(it.toInt()) } < 400) {
                    ui("Слишком тихо. Повторите запись.")
                    return@thread
                }
                val audio = DoubleArray(pcm.size) { pcm[it] / 32768.0 }
                done(audio)
            } catch (e: Exception) {
                ui("Ошибка микрофона: " + (e.message ?: e.javaClass.simpleName))
            }
        }
    }

    companion object {
        private const val REQUEST_MIC = 7
        private const val SAMPLE_RATE = 16_000
        private const val TEST_DURATION_MS = 15_000L
        private const val ENROLLMENT_DURATION_MS = 2_500
        private const val FRAME_SAMPLES = 320
        private const val MIN_UTTERANCE_SAMPLES = SAMPLE_RATE / 2
        private const val MIN_ANALYSIS_SAMPLES = SAMPLE_RATE / 5
        private const val MAX_UTTERANCE_SAMPLES = SAMPLE_RATE * 5
        private const val END_SILENCE_SAMPLES = SAMPLE_RATE / 4
        private const val VAD_RMS_THRESHOLD = 0.006
        private const val VAD_PEAK_THRESHOLD = 0.018
        private const val DTW_THRESHOLD = 7.0
        private const val OWNER_THRESHOLD = 0.88
    }
}

private class AcousticMatcher(ctx: Context) {
    private val p = ctx.getSharedPreferences("templates", Context.MODE_PRIVATE)
    init {
        if (!p.contains("dtw_threshold")) p.edit().putFloat("dtw_threshold", 7.0f).apply()
        if (!p.contains("owner_threshold")) p.edit().putFloat("owner_threshold", 0.88f).apply()
        if (!p.contains("margin_threshold")) p.edit().putFloat("margin_threshold", 0.88f).apply()
    }
    fun dtwThreshold(): Double = p.getFloat("dtw_threshold", 7.0f).toDouble()
    fun ownerThreshold(): Double = p.getFloat("owner_threshold", 0.88f).toDouble()
    fun marginThreshold(): Double = p.getFloat("margin_threshold", 0.88f).toDouble()

    fun commands(): List<String> = p.getStringSet("names", emptySet())!!.toList().sorted()

    fun enrollOwner(x: DoubleArray) {
        p.edit().putString("owner", enc(Mfcc.extract(x).average())).apply()
    }

    fun ownerSimilarity(x: DoubleArray): Double? {
        val saved = p.getString("owner", null) ?: return null
        return cosine(dec(saved), Mfcc.extract(x).average())
    }

    fun enrollCommand(name: String, x: DoubleArray) {
        val key = "cmd_" + name
        val items = p.getString(key, null)?.split("|")?.toMutableList() ?: mutableListOf()
        items.add(enc(Mfcc.extract(x).flatMap { it.toList() }.toDoubleArray()))
        p.edit()
            .putString(key, items.takeLast(6).joinToString("|"))
            .putStringSet("names", (commands() + name).toSet())
            .apply()
    }

    fun match(x: DoubleArray): MatchResult? {
        val sequence = Mfcc.extract(x)
        if (sequence.isEmpty()) return null
        val perCommand = mutableMapOf<String, Double>()
        for (name in commands()) {
            var bestForCommand = Double.MAX_VALUE
            val stored = p.getString("cmd_" + name, null)?.split("|") ?: emptyList()
            for (item in stored) {
                val flat = dec(item)
                if (flat.isEmpty() || flat.size % MFCC_COEFFICIENTS != 0) continue
                val reference = flat.toList().chunked(MFCC_COEFFICIENTS)
                    .map { it.toDoubleArray() }
                val distance = dtw(sequence, reference)
                if (distance < bestForCommand) bestForCommand = distance
            }
            if (bestForCommand < Double.MAX_VALUE) perCommand[name] = bestForCommand
        }
        if (perCommand.isEmpty()) return null
        val sorted = perCommand.entries.sortedBy { it.value }
        val best = sorted[0]
        val second = sorted.getOrNull(1)?.value ?: Double.POSITIVE_INFINITY
        val accepted = best.value < dtwThreshold() &&
            (second.isInfinite() || best.value < second * marginThreshold())
        val marginRatio = if (second.isInfinite() || second <= 0.0) 0.0 else best.value / second
        return MatchResult(best.key, best.value, second, accepted, marginRatio)
    }

    fun applyThresholds(dtw: Double, owner: Double, margin: Double) {
        p.edit()
            .putFloat("dtw_threshold", dtw.toFloat())
            .putFloat("owner_threshold", owner.toFloat())
            .putFloat("margin_threshold", margin.toFloat())
            .apply()
    }

    data class MatchResult(
        val command: String,
        val bestDistance: Double,
        val secondDistance: Double,
        val accepted: Boolean,
        val marginRatio: Double
    )

    private fun enc(a: DoubleArray): String =
        android.util.Base64.encodeToString(
            a.joinToString(",").toByteArray(),
            android.util.Base64.NO_WRAP
        )

    private fun dec(s: String): DoubleArray =
        String(android.util.Base64.decode(s, android.util.Base64.DEFAULT))
            .split(",").mapNotNull { it.toDoubleOrNull() }.toDoubleArray()

    private fun cosine(a: DoubleArray, b: DoubleArray): Double {
        if (a.size != b.size || a.isEmpty()) return -1.0
        val dot = a.indices.sumOf { a[it] * b[it] }
        val na = sqrt(a.sumOf { it * it })
        val nb = sqrt(b.sumOf { it * it })
        return if (na * nb == 0.0) -1.0 else dot / (na * nb)
    }

    private fun dtw(a: List<DoubleArray>, b: List<DoubleArray>): Double {
        if (a.isEmpty() || b.isEmpty()) return Double.POSITIVE_INFINITY
        var previous = DoubleArray(b.size + 1) { 1e9 }
        previous[0] = 0.0
        for (i in a.indices) {
            val current = DoubleArray(b.size + 1) { 1e9 }
            val from = max(0, i - DTW_BAND)
            val to = min(b.size - 1, i + DTW_BAND)
            if (from <= to) {
                for (j in from..to) {
                    val cost = sqrt(
                        a[i].indices.sumOf { k -> (a[i][k] - b[j][k]).pow(2) } /
                            MFCC_COEFFICIENTS.toDouble()
                    )
                    current[j + 1] = cost + minOf(previous[j + 1], current[j], previous[j])
                }
            }
            previous = current
        }
        return previous[b.size] / max(a.size, b.size)
    }

    private fun List<DoubleArray>.average(): DoubleArray =
        DoubleArray(MFCC_COEFFICIENTS) { i ->
            if (isEmpty()) 0.0 else map { it[i] }.average()
        }

    companion object {
        private const val MFCC_COEFFICIENTS = 13
        private const val DTW_BAND = 12
        private const val DTW_THRESHOLD = 7.0
    }
}

private object Mfcc {
    fun extract(x: DoubleArray): List<DoubleArray> {
        val frame = 400
        val hop = 160
        val nfft = 512
        val filters = 26
        val coefficients = 13
        val sampleRate = 16_000
        val output = mutableListOf<DoubleArray>()
        var position = 0
        while (position + frame <= x.size) {
            val power = DoubleArray(nfft / 2 + 1)
            for (k in 0..nfft / 2) {
                var real = 0.0
                var imaginary = 0.0
                for (t in 0 until frame) {
                    val value = x[position + t] * (0.54 - 0.46 * cos(2 * PI * t / (frame - 1)))
                    val angle = 2 * PI * k * t / nfft
                    real += value * cos(angle)
                    imaginary -= value * sin(angle)
                }
                power[k] = real * real + imaginary * imaginary
            }
            val highMel = 2595 * ln(1 + sampleRate / 2.0 / 700)
            val points = (0..filters + 1).map { index ->
                val mel = highMel * index / (filters + 1)
                (700 * (exp(mel / 2595) - 1) * nfft / sampleRate)
                    .toInt().coerceIn(0, nfft / 2)
            }
            val melEnergy = DoubleArray(filters)
            for (m in 0 until filters) {
                for (k in points[m]..points[m + 1]) {
                    if (points[m + 1] > points[m]) {
                        melEnergy[m] += (k - points[m]).toDouble() /
                            (points[m + 1] - points[m]) * power[k]
                    }
                }
                for (k in points[m + 1]..points[m + 2]) {
                    if (points[m + 2] > points[m + 1]) {
                        melEnergy[m] += (points[m + 2] - k).toDouble() /
                            (points[m + 2] - points[m + 1]) * power[k]
                    }
                }
            }
            val logs = DoubleArray(filters) { ln(melEnergy[it].coerceAtLeast(1e-10)) }
            output.add(DoubleArray(coefficients) { c ->
                (0 until filters).sumOf { m ->
                    logs[m] * cos(PI * c * (m + 0.5) / filters)
                } / filters
            })
            position += hop
        }
        return output
    }
}