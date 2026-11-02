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

import java.util.ArrayList;
import java.util.List;

import ru.voiceactivation.next.audio.AudioCapture;
import ru.voiceactivation.next.audio.AudioCaptureConfig;
import ru.voiceactivation.next.analysis.AcousticAnalyzer;
import ru.voiceactivation.next.analysis.SpeechDetector;

public final class MainActivity extends Activity implements AudioCapture.Listener {
    private static final int REQUEST_MIC = 101;
    private static final long UI_UPDATE_INTERVAL_MS = 500L;

    private TextView status;
    private TextView recognizedCommand;
    private AudioCapture capture;
    private final AcousticAnalyzer analyzer = new AcousticAnalyzer();
    private final SpeechDetector speechDetector = new SpeechDetector();
    private static final int SAMPLE_RATE_HZ = 16000;
    private final List<short[]> capturedSegment = new ArrayList<>();
    private int capturedSamples;
    private long totalSamples;
    private long lastUiUpdateMs;
    private boolean activityDestroyed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 36, 28, 28);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("VoiceActivation-Next");
        title.setTextSize(24);
        root.addView(title);

        status = new TextView(this);
        status.setText("Модуль 01 — AudioCapture\nГотов к проверке микрофона");
        status.setTextSize(16);
        root.addView(status);

        TextView recognizedTitle = new TextView(this);
        recognizedTitle.setText("Последняя распознанная фраза");
        recognizedTitle.setTextSize(18);
        recognizedTitle.setPadding(0, 32, 0, 8);
        root.addView(recognizedTitle);

        recognizedCommand = new TextView(this);
        recognizedCommand.setText("Пока ничего не распознано");
        recognizedCommand.setTextSize(22);
        recognizedCommand.setPadding(16, 20, 16, 20);
        recognizedCommand.setMinHeight(100);
        recognizedCommand.setGravity(Gravity.CENTER_VERTICAL);
        recognizedCommand.setBackgroundColor(0xFFEAEAEA);
        root.addView(recognizedCommand,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

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
        speechDetector.reset();
        if (recognizedCommand != null) recognizedCommand.setText("Слушаю микрофон. Ожидаю голос...");
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
        AcousticAnalyzer.Metrics metrics = analyzer.analyze(samples, length, SAMPLE_RATE_HZ);
        SpeechDetector.Event event = speechDetector.accept(metrics);
        if (event == SpeechDetector.Event.SPEECH_STARTED) {
            capturedSegment.clear();
            capturedSamples = 0;
            showRecognizedCommand("ГОЛОС ОБНАРУЖЕН — идёт захват команды...");
        }
        if (speechDetector.isSpeaking()) {
            short[] copy = new short[length];
            System.arraycopy(samples, 0, copy, 0, length);
            capturedSegment.add(copy);
            capturedSamples += length;
        }
        if (event == SpeechDetector.Event.SPEECH_ENDED) {
            double durationMs = capturedSamples * 1000.0 / SAMPLE_RATE_HZ;
            showRecognizedCommand("ГОЛОСОВОЙ ФРАГМЕНТ ЗАХВАЧЕН: "
                    + String.format(java.util.Locale.US, "%.0f мс", durationMs)
                    + ". Распознанный текст появится после подключения Vosk.");
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastUiUpdateMs >= UI_UPDATE_INTERVAL_MS) {
            lastUiUpdateMs = now;
            showStatus("Непрерывное прослушивание: RECORDING"
                    + "\nУровень RMS: " + String.format(java.util.Locale.US, "%.1f dBFS", metrics.getRmsDbfs())
                    + "\nПик: " + String.format(java.util.Locale.US, "%.1f dBFS", metrics.getPeakDbfs())
                    + "\nФоновый шум: " + String.format(java.util.Locale.US, "%.1f dBFS", speechDetector.getNoiseDbfs())
                    + "\nПорог голоса: " + String.format(java.util.Locale.US, "%.1f dBFS", speechDetector.getStartThresholdDbfs())
                    + "\nСостояние: " + (speechDetector.isSpeaking() ? "ГОЛОСОВОЙ ФРАГМЕНТ" : "ОЖИДАНИЕ РЕЧИ")
                    + "\nПолучено PCM-сэмплов: " + totalSamples);
        }
    }

    @Override public void onStateChanged(AudioCapture.State state) {
        showStatus("Модуль 01 — AudioCapture\nСостояние: " + state);
    }

    @Override public void onError(String message, Throwable cause) {
        showStatus(message + "\n" + (cause == null ? "Неизвестная ошибка" : cause.getMessage()));
    }

    /**
     * Вызывается распознавателем после обработки очередного речевого сегмента.
     * Передаётся именно распознанный текст, а не идентификатор говорящего.
     */
    public void showRecognizedCommand(String text) {
        String value = text == null || text.trim().isEmpty()
                ? "Речь не распознана"
                : text.trim();
        runOnUiThread(() -> {
            if (!activityDestroyed && recognizedCommand != null) {
                recognizedCommand.setText(value);
            }
        });
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
