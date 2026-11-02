package ru.voiceactivation.next;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import ru.voiceactivation.next.audio.AudioCapture;
import ru.voiceactivation.next.audio.AudioCaptureConfig;

public final class MainActivity extends Activity implements AudioCapture.Listener {
    private static final int REQUEST_MIC = 101;
    private static final long UI_UPDATE_INTERVAL_MS = 500L;

    private TextView status;
    private AudioCapture capture;
    private long totalSamples;
    private long lastUiUpdateMs;
    private boolean activityDestroyed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 36, 28, 28);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        status = new TextView(this);
        status.setText("VoiceActivation-Next\nМодуль 01 — AudioCapture\nГотов к проверке микрофона");
        status.setTextSize(18);
        root.addView(status);

        Button start = new Button(this);
        start.setText("Начать захват");
        start.setOnClickListener(v -> startCapture());
        root.addView(start);

        Button stop = new Button(this);
        stop.setText("Остановить");
        stop.setOnClickListener(v -> {
            if (capture != null) capture.stop();
        });
        root.addView(stop);

        setContentView(root);
    }

    private void startCapture() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
            return;
        }
        if (capture == null || capture.getState() == AudioCapture.State.ERROR
                || capture.getState() == AudioCapture.State.IDLE) {
            capture = new AudioCapture(AudioCaptureConfig.defaults(), this);
        }
        totalSamples = 0L;
        capture.start();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_MIC) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                startCapture();
            } else {
                showStatus("Доступ к микрофону не предоставлен");
            }
        }
    }

    @Override public void onPcm(short[] samples, int length, long timestampNanos) {
        totalSamples += length;
        long now = SystemClock.elapsedRealtime();
        if (now - lastUiUpdateMs >= UI_UPDATE_INTERVAL_MS) {
            lastUiUpdateMs = now;
            showStatus("Модуль 01 — AudioCapture\nСостояние: RECORDING"
                    + "\nПолучено PCM-сэмплов: " + totalSamples);
        }
    }

    @Override public void onStateChanged(AudioCapture.State state) {
        showStatus("Модуль 01 — AudioCapture\nСостояние: " + state);
    }

    @Override public void onError(String message, Throwable cause) {
        showStatus(message + "\n" + (cause == null ? "Неизвестная ошибка" : cause.getMessage()));
    }

    private void showStatus(String text) {
        if (activityDestroyed) return;
        runOnUiThread(() -> {
            if (!activityDestroyed && status != null) status.setText(text);
        });
    }

    @Override protected void onDestroy() {
        activityDestroyed = true;
        if (capture != null) capture.stop();
        super.onDestroy();
    }
}
