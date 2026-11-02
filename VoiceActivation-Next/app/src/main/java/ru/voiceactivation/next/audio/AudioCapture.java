package ru.voiceactivation.next.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Module 01: explicit, foreground, mono PCM16 microphone acquisition.
 * Signal analysis, speech detection, persistence, and classification belong
 * to later modules and are deliberately excluded here.
 */
public final class AudioCapture {
    public enum State { IDLE, STARTING, RECORDING, STOPPING, ERROR }

    public interface Listener {
        /** Samples are valid only for the duration of this callback; copy them to retain. */
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
        if (config == null || listener == null) {
            throw new IllegalArgumentException("config and listener are required");
        }
        this.config = config;
        this.listener = listener;
    }

    /** Starts one capture worker. Returns false while a previous worker is active or cleaning up. */
    public synchronized boolean start() {
        if (worker != null || state == State.STARTING
                || state == State.RECORDING || state == State.STOPPING) {
            return false;
        }
        stopRequested.set(false);
        changeState(State.STARTING);
        worker = new Thread(this::captureLoop, "VoiceActivation-AudioCapture");
        worker.start();
        return true;
    }

    /** Requests a prompt stop; AudioRecord is released by the capture worker in all cases. */
    public synchronized void stop() {
        if (state == State.IDLE || state == State.ERROR || state == State.STOPPING) return;
        stopRequested.set(true);
        changeState(State.STOPPING);
        AudioRecord active = recorder;
        if (active != null) {
            try {
                active.stop();
            } catch (IllegalStateException ignored) {
                // The worker may be between initialization and startRecording().
            }
        }
        Thread activeWorker = worker;
        if (activeWorker != null) activeWorker.interrupt();
    }

    public State getState() {
        return state;
    }

    private void captureLoop() {
        AudioRecord local = null;
        boolean failed = false;
        try {
            int minBytes = AudioRecord.getMinBufferSize(
                    config.getSampleRateHz(),
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (minBytes <= 0) {
                throw new IllegalStateException("AudioRecord.getMinBufferSize failed: " + minBytes);
            }

            int bufferBytes = Math.max(minBytes, config.getBytesPerBuffer() * 2);
            local = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    config.getSampleRateHz(),
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes);

            if (local.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("AudioRecord initialization failed");
            }

            recorder = local;
            if (stopRequested.get()) return;

            local.startRecording();
            if (stopRequested.get()) return;
            changeState(State.RECORDING);

            short[] buffer = new short[config.getFramesPerBuffer()];
            while (!stopRequested.get()) {
                int count = local.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
                if (count > 0) {
                    try {
                        listener.onPcm(buffer, count, System.nanoTime());
                    } catch (RuntimeException callbackError) {
                        throw new IllegalStateException("PCM listener failed", callbackError);
                    }
                } else if (count < 0 && !stopRequested.get()) {
                    throw new IllegalStateException("AudioRecord.read failed: " + count);
                }
            }
        } catch (Throwable error) {
            if (!stopRequested.get()) {
                failed = true;
                changeState(State.ERROR);
                notifyError("Microphone capture failed", error);
            }
        } finally {
            recorder = null;
            if (local != null) {
                try {
                    if (local.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        local.stop();
                    }
                } catch (RuntimeException ignored) {
                    // Already stopped or invalidated by the platform.
                }
                try {
                    local.release();
                } catch (RuntimeException releaseError) {
                    if (!failed && !stopRequested.get()) {
                        failed = true;
                        changeState(State.ERROR);
                        notifyError("Microphone release failed", releaseError);
                    }
                }
            }
            if (!failed) changeState(State.IDLE);
            synchronized (this) {
                if (worker == Thread.currentThread()) worker = null;
            }
        }
    }

    /** Listener state callbacks must never be allowed to break recorder cleanup. */
    private void changeState(State next) {
        state = next;
        try {
            listener.onStateChanged(next);
        } catch (RuntimeException ignored) {
            // UI observers are non-critical; capture lifecycle remains authoritative.
        }
    }

    private void notifyError(String message, Throwable cause) {
        try {
            listener.onError(message, cause);
        } catch (RuntimeException ignored) {
            // Error reporting must not prevent AudioRecord from being released.
        }
    }
}
