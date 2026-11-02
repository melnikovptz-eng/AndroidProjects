package ru.voiceactivation.next.asr;

import java.util.Locale;

/** Detects the configured activation word in Vosk's recognized text. */
public final class WakeWordDetector {
    private static final String WAKE_WORD = "яша";

    public boolean matches(String recognizedText) {
        if (recognizedText == null || recognizedText.trim().isEmpty()) return false;
        String normalized = recognizedText.toLowerCase(new Locale("ru"))
                .replace('ё', 'е')
                .replaceAll("[^а-яa-z0-9]+", " ")
                .trim();
        if (normalized.isEmpty()) return false;
        for (String token : normalized.split("\\s+")) {
            if (WAKE_WORD.equals(token)) return true;
        }
        return false;
    }
}