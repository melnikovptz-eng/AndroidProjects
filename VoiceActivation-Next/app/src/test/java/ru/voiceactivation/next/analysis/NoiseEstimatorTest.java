package ru.voiceactivation.next.analysis;

import org.junit.Test;
import static org.junit.Assert.*;

public class NoiseEstimatorTest {
    @Test public void startsWithoutAnEstimate() {
        NoiseEstimator estimator = new NoiseEstimator(5, 0.2);
        assertTrue(Double.isNaN(estimator.estimateDbfs()));
        assertEquals(0, estimator.getFrameCount());
    }

    @Test public void estimatesLowPercentileToReduceSpeechOutlierInfluence() {
        NoiseEstimator estimator = new NoiseEstimator(5, 0.2);
        estimator.addFrame(-55);
        estimator.addFrame(-54);
        estimator.addFrame(-20);
        estimator.addFrame(-53);
        assertEquals(-55, estimator.estimateDbfs(), 0.0);
    }

    @Test public void rollingWindowDropsOldFrames() {
        NoiseEstimator estimator = new NoiseEstimator(3, 0.5);
        estimator.addFrame(-60);
        estimator.addFrame(-50);
        estimator.addFrame(-40);
        assertEquals(-50, estimator.estimateDbfs(), 0.0);
        estimator.addFrame(-30);
        assertEquals(-40, estimator.estimateDbfs(), 0.0);
        assertEquals(3, estimator.getFrameCount());
    }

    @Test public void resetClearsHistory() {
        NoiseEstimator estimator = new NoiseEstimator(4, 0.25);
        estimator.addFrame(-60);
        estimator.reset();
        assertEquals(0, estimator.getFrameCount());
        assertTrue(Double.isNaN(estimator.estimateDbfs()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonFiniteMeasurement() {
        new NoiseEstimator(4, 0.25).addFrame(Double.NaN);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidPercentile() {
        new NoiseEstimator(4, 1.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTooSmallWindow() {
        new NoiseEstimator(2, 0.25);
    }
}
