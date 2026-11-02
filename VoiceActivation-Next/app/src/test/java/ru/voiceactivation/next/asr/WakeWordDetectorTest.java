package ru.voiceactivation.next.asr;

import org.junit.Test;
import static org.junit.Assert.*;

public class WakeWordDetectorTest {
    private final WakeWordDetector detector = new WakeWordDetector();

    @Test public void recognizesActivationWordAsStandaloneToken() {
        assertTrue(detector.matches("Яша"));
        assertTrue(detector.matches("Эй, Яша!"));
        assertTrue(detector.matches("яша"));
    }

    @Test public void normalizesYoAndPunctuationWithoutSubstringMatches() {
        assertTrue(detector.matches("Привет, ЯША."));
        assertFalse(detector.matches("Яшап"));
        assertFalse(detector.matches("машина"));
        assertFalse(detector.matches(""));
        assertFalse(detector.matches(null));
    }
}