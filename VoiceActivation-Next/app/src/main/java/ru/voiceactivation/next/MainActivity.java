package ru.voiceactivation.next;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ru.voiceactivation.next.analysis.AcousticAnalyzer;
import ru.voiceactivation.next.analysis.SpeechDetector;
import ru.voiceactivation.next.asr.VoskEngine;
import ru.voiceactivation.next.asr.VoskModelImporter;
import ru.voiceactivation.next.asr.WakeWordDetector;
import ru.voiceactivation.next.audio.AudioCapture;
import ru.voiceactivation.next.audio.AudioCaptureConfig;

public final class MainActivity extends Activity implements AudioCapture.Listener {
    private static final int SAMPLE_RATE_HZ = 16000;
    private static final int REQUEST_MIC = 101;
    private static final int REQUEST_VOSK_MODEL = 102;
    private static final long UI_UPDATE_INTERVAL_MS = 500L;

    private TextView status;
    private TextView recognizedCommand;
    private Button startButton;
    private AudioCapture capture;
    private final AcousticAnalyzer analyzer = new AcousticAnalyzer();
    private final SpeechDetector speechDetector = new SpeechDetector();
    private final VoskEngine voskEngine = new VoskEngine(SAMPLE_RATE_HZ);
    private final WakeWordDetector wakeWordDetector = new WakeWordDetector();
    private final List<short[]> capturedSegment = new ArrayList<>();
    private int capturedSamples;
    private long totalSamples;
    private long lastUiUpdateMs;
    private volatile boolean activityDestroyed;
    private volatile boolean modelLoading;
    private volatile String lastRecognitionStatus = "Модель Vosk не подключена";

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
        status.setText("Загрузка состояния...");
        status.setTextSize(16);
        root.addView(status);

        TextView recognizedTitle = new TextView(this);
        recognizedTitle.setText("Распознанная речь / команда");
        recognizedTitle.setTextSize(18);
        recognizedTitle.setPadding(0, 24, 0, 8);
        root.addView(recognizedTitle);

        recognizedCommand = new TextView(this);
        recognizedCommand.setText("Сначала подключите русскую модель Vosk");
        recognizedCommand.setTextSize(21);
        recognizedCommand.setPadding(16, 20, 16, 20);
        recognizedCommand.setMinHeight(100);
        recognizedCommand.setGravity(Gravity.CENTER_VERTICAL);
        recognizedCommand.setBackgroundColor(0xFFEAEAEA);
        root.addView(recognizedCommand, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button importModel = new Button(this);
        importModel.setText("Подключить папку русской модели Vosk");
        importModel.setOnClickListener(v -> chooseVoskModel());
        root.addView(importModel);

        startButton = new Button(this);
        startButton.setText("Начать захват и распознавание");
        startButton.setEnabled(false);
        startButton.setOnClickListener(v -> startCapture());
        root.addView(startButton);

        Button stop = new Button(this);
        stop.setText("Остановить");
        stop.setOnClickListener(v -> {
            if (capture != null) capture.stop();
        });
        root.addView(stop);

        setContentView(root);
        loadSavedModel();
    }

    private void chooseVoskModel() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_VOSK_MODEL);
    }

    private void loadSavedModel() {
        File modelDirectory = new File(getFilesDir(), "vosk-model-ru");
        if (!modelDirectory.isDirectory()) {
            lastRecognitionStatus = "Модель Vosk не подключена";
            showStatus("Выберите распакованную русскую модель Vosk (папка с am, conf, graph).");
            return;
        }
        modelLoading = true;
        showStatus("Загружаю сохранённую модель Vosk...");
        new Thread(() -> {
            try {
                voskEngine.load(modelDirectory);
                modelLoading = false;
                lastRecognitionStatus = "Русская модель Vosk готова";
                runOnUiThread(() -> {
                    if (!activityDestroyed && startButton != null) startButton.setEnabled(true);
                    showStatus("Модель загружена. Можно проверять команду «Яша».");
                });
            } catch (Exception error) {
                modelLoading = false;
                lastRecognitionStatus = "Ошибка загрузки модели";
                showStatus("Не удалось загрузить модель Vosk: " + error.getMessage());
            }
        }, "Vosk-Model-Load").start();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_VOSK_MODEL || resultCode != RESULT_OK || data == null) return;
        Uri treeUri = data.getData();
        if (treeUri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(
                    treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
        modelLoading = true;
        if (startButton != null) startButton.setEnabled(false);
        showStatus("Копирую выбранную модель в память приложения...");
        new Thread(() -> {
            try {
                File destination = new File(getFilesDir(), "vosk-model-ru");
                VoskModelImporter.importTree(this, treeUri, destination);
                voskEngine.load(destination);
                modelLoading = false;
                lastRecognitionStatus = "Русская модель Vosk готова";
                runOnUiThread(() -> {
                    if (!activityDestroyed && startButton != null) startButton.setEnabled(true);
                    showStatus("Русская модель Vosk подключена. Сравнение с «Яша» включено.");
                });
            } catch (Exception error) {
                modelLoading = false;
                lastRecognitionStatus = "Модель не загружена";
                showStatus("Ошибка импорта модели: " + error.getMessage());
            }
        }, "Vosk-Model-Import").start();
    }

    private void startCapture() {
        if (!voskEngine.isLoaded() || modelLoading) {
            showStatus("Сначала подключите и загрузите русскую модель Vosk.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MIC);
            return;
        }
        if (capture == null || capture.getState() == AudioCapture.State.ERROR
                || capture.getState() == AudioCapture.State.IDLE) {
            capture = new AudioCapture(AudioCaptureConfig.defaults(), this);
        }
        totalSamples = 0L;
        capturedSegment.clear();
        capturedSamples = 0;
        speechDetector.reset();
        showRecognizedCommand("Слушаю. Скажите «Яша»...");
        capture.start();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_MIC) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                startCapture();
            } else {
                showStatus("Доступ к микрофону не предоставлен.");
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
            showRecognizedCommand("Голос обнаружен. Захватываю команду...");
        }
        if (speechDetector.isSpeaking()) {
            short[] copy = new short[length];
            System.arraycopy(samples, 0, copy, 0, length);
            capturedSegment.add(copy);
            capturedSamples += length;
        }
        if (event == SpeechDetector.Event.SPEECH_ENDED) {
            short[] utterance = flattenCapturedSegment();
            double durationMs = capturedSamples * 1000.0 / SAMPLE_RATE_HZ;
            showRecognizedCommand("Фрагмент " + String.format(java.util.Locale.US, "%.0f мс", durationMs)
                    + ". Распознаю...");
            try {
                String recognized = voskEngine.recognize(utterance);
                if (recognized.isEmpty()) {
                    lastRecognitionStatus = "Речь не распознана";
                    showRecognizedCommand("Vosk не распознал речь");
                } else {
                    showRecognizedCommand(recognized);
                    if (wakeWordDetector.matches(recognized)) {
                        lastRecognitionStatus = "КОМАНДА «ЯША» ОБНАРУЖЕНА";
                    } else {
                        lastRecognitionStatus = "Команда «Яша» не обнаружена";
                    }
                }
            } catch (Exception error) {
                lastRecognitionStatus = "Ошибка распознавания";
                showRecognizedCommand("Ошибка Vosk: " + error.getMessage());
            }
        }

        long now = SystemClock.elapsedRealtime();
        if (now - lastUiUpdateMs >= UI_UPDATE_INTERVAL_MS) {
            lastUiUpdateMs = now;
            showStatus("Прослушивание: RECORDING"
                    + "\nRMS: " + String.format(java.util.Locale.US, "%.1f dBFS", metrics.getRmsDbfs())
                    + " | Peak: " + String.format(java.util.Locale.US, "%.1f dBFS", metrics.getPeakDbfs())
                    + "\nФоновый шум: " + String.format(java.util.Locale.US, "%.1f dBFS", speechDetector.getNoiseDbfs())
                    + " | Порог: " + String.format(java.util.Locale.US, "%.1f dBFS", speechDetector.getStartThresholdDbfs())
                    + "\n" + lastRecognitionStatus
                    + "\nPCM-сэмплов: " + totalSamples);
        }
    }

    private short[] flattenCapturedSegment() {
        short[] result = new short[capturedSamples];
        int offset = 0;
        for (short[] part : capturedSegment) {
            int count = Math.min(part.length, result.length - offset);
            if (count <= 0) break;
            System.arraycopy(part, 0, result, offset, count);
            offset += count;
        }
        return result;
    }

    @Override public void onStateChanged(AudioCapture.State state) {
        showStatus("Захват микрофона: " + state + "\n" + lastRecognitionStatus);
    }

    @Override public void onError(String message, Throwable cause) {
        showStatus(message + "\n" + (cause == null ? "Неизвестная ошибка" : cause.getMessage()));
    }

    public void showRecognizedCommand(String text) {
        String value = text == null || text.trim().isEmpty() ? "Речь не распознана" : text.trim();
        runOnUiThread(() -> {
            if (!activityDestroyed && recognizedCommand != null) recognizedCommand.setText(value);
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
        voskEngine.close();
        super.onDestroy();
    }
}