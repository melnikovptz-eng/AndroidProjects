package ru.voiceactivation.demo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import ru.voiceactivation.demo.voice.AcousticAnalyzer
import ru.voiceactivation.demo.voice.OwnerProfileStore
import ru.voiceactivation.demo.voice.RussianWakePhraseDetector
import ru.voiceactivation.demo.voice.SpeakerEngine
import ru.voiceactivation.demo.voice.VoiceModelRepository
import java.util.concurrent.atomic.AtomicBoolean

class VoiceActivationService : Service() {
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var recorder: AudioRecord? = null
    private var wakeDetector: RussianWakePhraseDetector? = null
    private var speakerEngine: SpeakerEngine? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val targetPackage = intent?.getStringExtra(EXTRA_TARGET_PACKAGE)
        val wakePhrase = intent?.getStringExtra(EXTRA_WAKE_PHRASE)
        if (targetPackage.isNullOrBlank() || wakePhrase.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val initialNotification = buildNotification("Голосовая активация включена")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }
        if (running.compareAndSet(false, true)) {
            worker = Thread({ listenLoop(targetPackage, wakePhrase) }, "VoiceActivationAudio").also {
                it.start()
            }
        }
        return START_STICKY
    }

    private fun listenLoop(targetPackage: String, wakePhrase: String) {
        val models = VoiceModelRepository(this)
        val russianDir = models.russianModelDirectory()
        val speakerModel = models.speakerModelFile()
        val model = java.io.File(russianDir, "model.onnx")
        val tokens = java.io.File(russianDir, "tokens.txt")

        if (!model.isFile || !tokens.isFile || !speakerModel.isFile) {
            updateNotification("Не установлены модели распознавания")
            stopSelf()
            return
        }

        try {
            wakeDetector = RussianWakePhraseDetector(
                modelPath = model.absolutePath,
                tokensPath = tokens.absolutePath,
                wakePhrase = wakePhrase,
            )
            speakerEngine = SpeakerEngine(
                modelPath = speakerModel.absolutePath,
                profileStore = OwnerProfileStore(this),
            )
            if (speakerEngine?.hasOwnerProfile() != true) {
                updateNotification("Сначала зарегистрируйте голос владельца")
                stopSelf()
                return
            }

            val minBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            check(minBytes > 0) { "AudioRecord buffer size error: $minBytes" }
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBytes * 2, SAMPLE_RATE / 5 * 2),
            )
            check(recorder?.state == AudioRecord.STATE_INITIALIZED) { "Microphone initialization failed" }
            recorder?.startRecording()

            val chunk = ShortArray(SAMPLE_RATE / 10)
            val utterance = ArrayList<Float>(SAMPLE_RATE * 4)
            val acousticAnalyzer = AcousticAnalyzer()
            var speechActive = false
            var silenceSamples = 0
            var snrSumDb = 0f
            var snrFrames = 0
            var lastAcousticLogMs = 0L
            while (running.get()) {
                val count = recorder?.read(chunk, 0, chunk.size) ?: -1
                if (count <= 0) {
                    if (count < 0) Log.e(TAG, "AudioRecord read failed: $count")
                    continue
                }
                val samples = FloatArray(count) { chunk[it] / 32768.0f }
                val acoustic = acousticAnalyzer.analyze(samples)
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastAcousticLogMs >= ACOUSTIC_LOG_INTERVAL_MS) {
                    Log.i(
                        TAG,
                        "Acoustic: rms=%.1f dBFS noise=%.1f dBFS SNR=%.1f dB state=%s".format(
                            java.util.Locale.ROOT,
                            acoustic.rmsDbfs,
                            acoustic.noiseFloorDbfs,
                            acoustic.snrDb,
                            acoustic.quality.name,
                        )
                    )
                    lastAcousticLogMs = now
                }

                if (acoustic.speechDetected) {
                    if (!speechActive) {
                        utterance.clear()
                        snrSumDb = 0f
                        snrFrames = 0
                    }
                    speechActive = true
                    silenceSamples = 0
                    utterance.addAll(samples.asList())
                    snrSumDb += acoustic.snrDb
                    snrFrames++
                } else if (speechActive) {
                    utterance.addAll(samples.asList())
                    silenceSamples += count
                }

                // The embedding is computed from the actual wake-phrase utterance,
                // not from a new three-second recording after the phrase.
                val detected = wakeDetector?.acceptAudio16k(samples)
                if (detected != null) {
                    Log.i(TAG, "Wake phrase detected")
                    val candidate = utterance.toFloatArray()
                    val averageSnrDb = if (snrFrames > 0) snrSumDb / snrFrames else Float.NEGATIVE_INFINITY
                    // ASR has already recognized the configured command. A fixed
                    // SNR cutoff rejected quiet but intelligible speech, so use
                    // utterance length here and let speaker verification decide.
                    if (candidate.size >= MIN_VERIFICATION_SAMPLES) {
                        val result = speakerEngine?.verifyOwner(candidate)
                        if (result?.accepted == true) {
                            Log.i(
                                TAG,
                                "Owner voice verified; transcript='%s'; average SNR=%.1f dB".format(
                                    java.util.Locale.ROOT, detected.transcript, averageSnrDb
                                )
                            )
                            launchTarget(targetPackage, detected.transcript)
                        } else {
                            Log.i(
                                TAG,
                                "Speaker rejected; transcript='%s'; average SNR=%.1f dB".format(
                                    java.util.Locale.ROOT, detected.transcript, averageSnrDb
                                )
                            )
                            updateNotification("Команда распознана, но голос владельца не подтверждён")
                        }
                    } else {
                        Log.i(
                            TAG,
                            "Audio too short for owner verification: samples=%d average SNR=%.1f dB".format(
                                java.util.Locale.ROOT, candidate.size, averageSnrDb
                            )
                        )
                        updateNotification("Команда распознана, но записи недостаточно для проверки голоса")
                    }
                    utterance.clear()
                    speechActive = false
                    silenceSamples = 0
                    snrSumDb = 0f
                    snrFrames = 0
                } else if (speechActive && silenceSamples >= SILENCE_RESET_SAMPLES) {
                    utterance.clear()
                    speechActive = false
                    silenceSamples = 0
                    snrSumDb = 0f
                    snrFrames = 0
                }

                if (utterance.size > MAX_UTTERANCE_SAMPLES) {
                    utterance.clear()
                    speechActive = false
                    silenceSamples = 0
                    snrSumDb = 0f
                    snrFrames = 0
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Voice activation stopped", e)
            updateNotification("Ошибка голосового движка: ${e.message ?: "unknown"}")
        } finally {
            running.set(false)
            try {
                recorder?.let {
                    if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
                    it.release()
                }
            } catch (_: Exception) { }
            recorder = null
            wakeDetector?.close()
            wakeDetector = null
            speakerEngine?.close()
            speakerEngine = null
            stopSelf()
        }
    }

    private fun launchTarget(packageName: String, transcript: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            updateNotification("Выбранное приложение недоступно")
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val targetPendingIntent = PendingIntent.getActivity(
            this,
            20,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            startActivity(launchIntent)
            updateNotification("Команда «\${transcript.take(48)}» · голос подтверждён", targetPendingIntent)
        } catch (e: Exception) {
            // Android may block activity starts from a foreground service.
            Log.w(TAG, "Background activity launch blocked; notification action required", e)
            updateNotification("Команда «\${transcript.take(48)}» · нажмите, чтобы открыть", targetPendingIntent)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VoiceActivation microphone",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Visible status while VoiceActivation listens"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        message: String,
        contentIntent: PendingIntent? = mainPendingIntent(),
    ): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("VoiceActivation")
            .setContentText(message)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun mainPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 10, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun updateNotification(message: String, pendingIntent: PendingIntent? = mainPendingIntent()) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(message, pendingIntent))
    }

    override fun onDestroy() {
        running.set(false)
        try { recorder?.stop() } catch (_: Exception) { }
        worker?.interrupt()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "target_package"
        const val EXTRA_WAKE_PHRASE = "wake_phrase"
        private const val TAG = "VoiceActivation"
        private const val CHANNEL_ID = "voice_activation_mic"
        private const val NOTIFICATION_ID = 4102
        private const val SAMPLE_RATE = 16_000
        private const val ACOUSTIC_LOG_INTERVAL_MS = 5_000L
        private const val SILENCE_RESET_SAMPLES = SAMPLE_RATE * 12 / 10
        private const val MAX_UTTERANCE_SAMPLES = SAMPLE_RATE * 8
        private const val MIN_VERIFICATION_SAMPLES = SAMPLE_RATE * 3 / 2
    }
}
