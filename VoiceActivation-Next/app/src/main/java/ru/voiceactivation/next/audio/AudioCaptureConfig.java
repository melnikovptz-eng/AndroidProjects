package ru.voiceactivation.next.audio;

/** Immutable configuration for microphone PCM capture. */
public final class AudioCaptureConfig {
    public static final int CHANNEL_IN_MONO = 16; // AudioFormat.CHANNEL_IN_MONO
    public static final int PCM_16BIT = 2;        // AudioFormat.ENCODING_PCM_16BIT

    private final int sampleRateHz;
    private final int framesPerBuffer;

    public AudioCaptureConfig(int sampleRateHz, int framesPerBuffer) {
        if (sampleRateHz != 16000 && sampleRateHz != 22050
                && sampleRateHz != 44100 && sampleRateHz != 48000) {
            throw new IllegalArgumentException("Unsupported sample rate: " + sampleRateHz);
        }
        if (framesPerBuffer < 160 || framesPerBuffer > sampleRateHz * 2) {
            throw new IllegalArgumentException("Invalid framesPerBuffer: " + framesPerBuffer);
        }
        this.sampleRateHz = sampleRateHz;
        this.framesPerBuffer = framesPerBuffer;
    }

    public static AudioCaptureConfig defaults() {
        return new AudioCaptureConfig(16000, 320);
    }

    public int getSampleRateHz() { return sampleRateHz; }
    public int getFramesPerBuffer() { return framesPerBuffer; }
    public int getBytesPerBuffer() { return framesPerBuffer * 2; }
}
