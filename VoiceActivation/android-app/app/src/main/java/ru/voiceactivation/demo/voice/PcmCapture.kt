package ru.voiceactivation.demo.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/** Explicit, bounded microphone capture for guided owner enrollment / verification. */
class PcmCapture(context: Context) {
    private val appContext = context.applicationContext

    fun recordUtterance(durationMs: Int = DEFAULT_DURATION_MS): FloatArray {
        require(durationMs in 1_500..10_000) { "Capture duration must be 1.5-10 seconds" }
        check(
            appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        ) { "Microphone permission is not granted" }

        val minBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBytes > 0) { "AudioRecord is not supported on this device" }

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBytes * 2, SAMPLE_RATE * 2),
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            "Unable to initialize microphone"
        }

        val output = ShortArray(SAMPLE_RATE * durationMs / 1_000)
        try {
            recorder.startRecording()
            var offset = 0
            while (offset < output.size) {
                val read = recorder.read(output, offset, output.size - offset)
                check(read > 0) { "Microphone read failed: $read" }
                offset += read
            }
        } finally {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
            recorder.release()
        }
        return FloatArray(output.size) { output[it] / 32768.0f }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val DEFAULT_DURATION_MS = 4_000
    }
}
