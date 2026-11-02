package ru.voiceactivation.next.audio;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class AudioCaptureDeviceTest {
    @Test public void capturesPcmStopsAndStartsAgain() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("RECORD_AUDIO permission must be granted before this device test",
                PackageManager.PERMISSION_GRANTED,
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));

        runCaptureCycle();
        runCaptureCycle();
    }

    private void runCaptureCycle() throws Exception {
        CountDownLatch firstPcm = new CountDownLatch(1);
        AtomicLong samples = new AtomicLong();
        AudioCapture capture = new AudioCapture(AudioCaptureConfig.defaults(),
                new AudioCapture.Listener() {
                    @Override public void onPcm(short[] pcm, int length, long timestampNanos) {
                        if (length > 0) {
                            samples.addAndGet(length);
                            firstPcm.countDown();
                        }
                    }
                    @Override public void onStateChanged(AudioCapture.State state) { }
                    @Override public void onError(String message, Throwable cause) {
                        fail(message + (cause == null ? "" : ": " + cause));
                    }
                });

        assertTrue("start must accept an idle capture", capture.start());
        assertTrue("PCM must arrive within 5 seconds", firstPcm.await(5, TimeUnit.SECONDS));
        assertTrue("captured sample count must be positive", samples.get() > 0);
        capture.stop();

        long deadline = SystemClock.elapsedRealtime() + 5000;
        while (capture.getState() != AudioCapture.State.IDLE
                && SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(20);
        }
        assertEquals("capture must return to IDLE after stop",
                AudioCapture.State.IDLE, capture.getState());
    }
}
