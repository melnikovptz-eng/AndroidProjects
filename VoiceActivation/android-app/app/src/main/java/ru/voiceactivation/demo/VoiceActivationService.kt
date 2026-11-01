package ru.voiceactivation.demo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
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
        startForeground(NOTIFICATION_ID, buildNotification("Голосовая активация включена"))
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
            var verificationAudio: FloatArray? = null
            var verificationOffset = 0
            while (running.get()) {
                val count = recorder?.read(chunk, 0, chunk.size) ?: -1
                if (count <= 0) {
                    if (count < 0) Log.e(TAG, "AudioRecord read failed: $count")
                    continue
                }
                val samples = FloatArray(count) { chunk[it] / 32768.0f }
                if (verificationAudio == null) {
                    val detected = wakeDetector?.acceptAudio16k(samples)
                    if (detected != null) {
                        Log.i(TAG, "Wake phrase detected")
                        verificationAudio = FloatArray(VERIFY_DURATION_SAMPLES)
                        verificationOffset = 0
                    }
                } else {
                    val copyCount = minOf(samples.size, verificationAudio.size - verificationOffset)
                    samples.copyInto(
                        destination = verificationAudio,
                        destinationOffset = verificationOffset,
                        startIndex = 0,
                        endIndex = copyCount,
                    )
                    verificationOffset += copyCount
                    if (verificationOffset >= verificationAudio.size) {
                        val result = speakerEngine?.verifyOwner(verificationAudio)
                        if (result?.accepted == true) {
                            Log.i(TAG, "Owner voice verified")
                            launchTarget(targetPackage)
                        } else {
                            Log.i(TAG, "Speaker rejected")
                        }
                        verificationAudio = null
                        verificationOffset = 0
                    }
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

    private fun launchTarget(packageName: String) {
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
            updateNotification("Голос подтверждён · приложение открыто или доступно по нажатию", targetPendingIntent)
        } catch (e: Exception) {
            // Android may block activity starts from a foreground service.
            Log.w(TAG, "Background activity launch blocked; notification action required", e)
            updateNotification("Голос подтверждён · нажмите, чтобы открыть приложение", targetPendingIntent)
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
        private const val VERIFY_DURATION_SECONDS = 3
        private const val VERIFY_DURATION_SAMPLES = SAMPLE_RATE * VERIFY_DURATION_SECONDS
    }
}
