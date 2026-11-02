package ru.voiceactivation.next.asr;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.IOException;

/** Offline Vosk recognizer for mono PCM16 audio at the configured sample rate. */
public final class VoskEngine implements AutoCloseable {
    private Model model;
    private final float sampleRateHz;

    public VoskEngine(float sampleRateHz) {
        if (sampleRateHz <= 0) throw new IllegalArgumentException("sampleRateHz must be positive");
        this.sampleRateHz = sampleRateHz;
    }

    public synchronized void load(File modelDirectory) throws IOException {
        if (modelDirectory == null || !modelDirectory.isDirectory()) {
            throw new IOException("Выбрана не папка модели Vosk");
        }
        File acoustic = new File(modelDirectory, "am");
        File config = new File(modelDirectory, "conf");
        File graph = new File(modelDirectory, "graph");
        if (!acoustic.isDirectory() || !config.isDirectory() || !graph.isDirectory()) {
            throw new IOException("Неверная структура модели Vosk: нужны папки am, conf и graph");
        }
        Model loaded = new Model(modelDirectory.getAbsolutePath());
        Model previous = model;
        model = loaded;
        if (previous != null) previous.close();
    }

    public synchronized boolean isLoaded() {
        return model != null;
    }

    /** Recognizes one already-segmented utterance. Returns plain text, not Vosk JSON. */
    public synchronized String recognize(short[] pcm) throws IOException {
        if (model == null) throw new IOException("Русская модель Vosk не загружена");
        if (pcm == null || pcm.length == 0) return "";
        try (Recognizer recognizer = new Recognizer(model, sampleRateHz)) {
            recognizer.acceptWaveForm(pcm, pcm.length);
            String json = recognizer.getFinalResult();
            JSONObject result = new JSONObject(json);
            return result.optString("text", "").trim();
        } catch (Exception error) {
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Ошибка распознавания Vosk", error);
        }
    }

    @Override public synchronized void close() {
        if (model != null) {
            model.close();
            model = null;
        }
    }
}