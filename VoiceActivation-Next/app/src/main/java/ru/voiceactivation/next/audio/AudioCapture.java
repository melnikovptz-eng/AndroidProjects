package ru.voiceactivation.next.audio;

import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.AudioFormat;
import android.media.AudioRecord;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Module 01: user-initiated mono PCM16 microphone capture.
 * It owns only acquisition and lifecycle; it does not analyze or classify audio.
 */
public final class AudioCapture {
    public enum State { IDLE, STARTING, RECORDING, STOPPING, ERROR }

    public interface Listener {
        void onPcm(short[] samples, int length, long timestampNanos);
        void onStateChanged(State state);
        void onError(String message, Throwable cause);
    }

    private final AudioCaptureConfig config;
    private final Listener listener;
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private volatile State state = State.IDLE;
    private volatile AudioRecord recorder;
    private Thread worker;

    public AudioCapture(AudioCaptureConfig config, Listener listener) {
        if (config == null || listener == null) throw new IllegalArgumentException("config/listener required");
        this.config = config;
        this.listener = listener;
    }

    public synchronized boolean start() {
        if (state == State.STARTING || state == State.RECORDING || state == State.STOPPING) return false;
        stopRequested.set(false);
        setState(State.STARTING);
        worker = new Thread(this::captureLoop, "VoiceActivation-AudioCapture");
        worker.start();
        return true;
    }

    public synchronized void stop() {
        if (state == State.IDLE || state == State.STOPPING) return;
        stopRequested.set(true);
        setState(State.STOPPING);
        AudioRecord active = recorder;
        if (active != null) {
            try { active.stop(); } catch (IllegalStateException ignored) { }
        }
        if (worker != null) worker.interrupt();
    }

    public State getState() { return state; }

    private void captureLoop() {
        AudioRecord local = null;
        try {
            int minBytes = AudioRecord.getMinBufferSize(config.getSampleRateHz(),
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minBytes <= 0) throw new IllegalStateException("AudioRecord minimum buffer unavailable: " + minBytes);
            int bufferBytes = Math.max(minBytes, config.getBytesPerBuffer() * 2);
            local = new AudioRecord(MediaRecorder.AudioSource.MIC, config.getSampleRateHz(),
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
            if (local.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("AudioRecord initialization failed");
            }
            recorder = local;
            if (stopRequested.get()) return;
            local.startRecording();
            setState(State.RECORDING);
            short[] buffer = new short[Math.max(config.getFramesPerBuffer(), bufferBytes / 2)];
            while (!stopRequested.get()) {
                int count = local.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
                if (count > 0) {
                    short[] frame = new short[count];
                    System.arraycopy(buffer, 0, frame, 0, count);
                    listener.onPcm(frame, count, System.nanoTime());
                } else if (count < 0 && !stopRequested.get()) {
                    throw new IllegalStateException("AudioRecord.read failed: " + count);
                }
            }
        } catch (Throwable error) {
            if (!stopRequested.get()) {
                setState(State.ERROR);
                listener.onError("Microphone capture failed", error);
            }
        } finally {
            recorder = null;
            if (local != null) {
                try { if (local.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) local.stop(); }
                catch (IllegalStateException ignored) { }
                local.release();
            }
            if (state != State.ERROR) setState(State.IDLE);
        }
    }

    private void setState(State newState) {
        state = newState;
        listener.onStateChanged(newState);
    }
}
