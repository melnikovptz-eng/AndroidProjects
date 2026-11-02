package ru.voiceactivation.next.audio;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioCaptureConfigTest {
    @Test public void defaultsUseMonoPcm16At16Khz() {
        AudioCaptureConfig config = AudioCaptureConfig.defaults();
        assertEquals(16000, config.getSampleRateHz());
        assertEquals(320, config.getFramesPerBuffer());
        assertEquals(640, config.getBytesPerBuffer());
        assertEquals(16, AudioCaptureConfig.CHANNEL_IN_MONO);
        assertEquals(2, AudioCaptureConfig.PCM_16BIT);
    }

    @Test public void acceptsSupportedRates() {
        for (int rate : new int[]{16000, 22050, 44100, 48000}) {
            assertEquals(rate, new AudioCaptureConfig(rate, 320).getSampleRateHz());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsupportedRate() { new AudioCaptureConfig(8000, 320); }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTooSmallBuffer() { new AudioCaptureConfig(16000, 159); }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOversizedBuffer() { new AudioCaptureConfig(16000, 32001); }
}
