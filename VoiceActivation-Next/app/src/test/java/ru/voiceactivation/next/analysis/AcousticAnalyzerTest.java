package ru.voiceactivation.next.analysis;

import org.junit.Test;
import static org.junit.Assert.*;

public class AcousticAnalyzerTest {
    private final AcousticAnalyzer analyzer = new AcousticAnalyzer();

    @Test public void silenceHasZeroAmplitudeAndDbfsFloor() {
        AcousticAnalyzer.Metrics m = analyzer.analyze(new short[]{0, 0, 0, 0}, 4, 16000);
        assertEquals(0.0, m.getRms(), 0.0);
        assertEquals(0.0, m.getPeak(), 0.0);
        assertEquals(-120.0, m.getRmsDbfs(), 0.0);
        assertEquals(-120.0, m.getPeakDbfs(), 0.0);
        assertEquals(0.25, m.getDurationMs(), 1e-9);
    }

    @Test public void fullScaleConstantSignalMeasuresZeroDbfsPeak() {
        AcousticAnalyzer.Metrics m = analyzer.analyze(new short[]{32767, 32767}, 2, 16000);
        assertEquals(32767.0 / 32768.0, m.getPeak(), 1e-9);
        assertEquals(0.0, m.getPeakDbfs(), 0.001);
        assertEquals(0.0, m.getZeroCrossingsPerSecond(), 0.0);
    }

    @Test public void rmsAndDcOffsetAreCalculatedFromRequestedRangeOnly() {
        AcousticAnalyzer.Metrics m = analyzer.analyze(new short[]{1000, 1000, 30000}, 2, 1000);
        assertEquals(1000.0 / 32768.0, m.getRms(), 1e-9);
        assertEquals(1000.0 / 32768.0, m.getDcOffset(), 1e-9);
        assertEquals(1000.0 / 32768.0, m.getPeak(), 1e-9);
    }

    @Test public void zeroCrossingRateIgnoresExactZeroSamples() {
        AcousticAnalyzer.Metrics m = analyzer.analyze(new short[]{-100, 0, 100, 0, -100}, 5, 1000);
        assertEquals(400.0, m.getZeroCrossingsPerSecond(), 1e-9);
    }

    @Test public void negativeFullScaleIsHandledWithoutOverflow() {
        AcousticAnalyzer.Metrics m = analyzer.analyze(new short[]{Short.MIN_VALUE}, 1, 16000);
        assertEquals(1.0, m.getPeak(), 0.0);
        assertEquals(0.0, m.getPeakDbfs(), 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyFrame() {
        analyzer.analyze(new short[]{1}, 0, 16000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsLengthBeyondArray() {
        analyzer.analyze(new short[]{1}, 2, 16000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidSampleRate() {
        analyzer.analyze(new short[]{1}, 1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullSamples() {
        analyzer.analyze(null, 1, 16000);
    }
}
