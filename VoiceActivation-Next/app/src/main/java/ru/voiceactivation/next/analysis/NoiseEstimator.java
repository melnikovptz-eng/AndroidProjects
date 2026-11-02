package ru.voiceactivation.next.analysis;

import java.util.Arrays;

/**
 * Module 03: rolling robust estimate of the acoustic background level.
 * It only estimates a noise baseline; it does not decide whether a frame is speech.
 */
public final class NoiseEstimator {
    private final double[] window;
    private int size;
    private int next;
    private final double percentile;

    public NoiseEstimator(int windowFrames, double percentile) {
        if (windowFrames < 3) throw new IllegalArgumentException("windowFrames must be >= 3");
        if (!(percentile > 0.0 && percentile < 1.0)) {
            throw new IllegalArgumentException("percentile must be between 0 and 1");
        }
        this.window = new double[windowFrames];
        this.percentile = percentile;
    }

    /** Adds one measured frame level in dBFS and returns the current baseline. */
    public synchronized double addFrame(double levelDbfs) {
        if (Double.isNaN(levelDbfs) || Double.isInfinite(levelDbfs)) {
            throw new IllegalArgumentException("levelDbfs must be finite");
        }
        window[next] = levelDbfs;
        next = (next + 1) % window.length;
        if (size < window.length) size++;
        return estimateDbfs();
    }

    /** Returns NaN until at least one frame has been observed. */
    public synchronized double estimateDbfs() {
        if (size == 0) return Double.NaN;
        double[] sorted = new double[size];
        System.arraycopy(window, 0, sorted, 0, size);
        Arrays.sort(sorted);
        int index = (int) Math.floor(percentile * (size - 1));
        return sorted[index];
    }

    public synchronized int getFrameCount() { return size; }
    public int getWindowCapacity() { return window.length; }

    public synchronized void reset() {
        Arrays.fill(window, 0.0);
        size = 0;
        next = 0;
    }
}
