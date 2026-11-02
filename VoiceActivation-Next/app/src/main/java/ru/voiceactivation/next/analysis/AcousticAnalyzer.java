package ru.voiceactivation.next.analysis;

/**
 * Module 02: deterministic time-domain analysis of mono signed PCM16 samples.
 * This class has no Android dependencies and does not classify speech.
 */
public final class AcousticAnalyzer {
    private static final double PCM16_SCALE = 32768.0;
    private static final double DBFS_FLOOR = -120.0;

    /** Immutable measurements for one PCM frame. */
    public static final class Metrics {
        private final int sampleCount;
        private final int sampleRateHz;
        private final double rms;
        private final double peak;
        private final double rmsDbfs;
        private final double peakDbfs;
        private final double zeroCrossingsPerSecond;
        private final double dcOffset;
        private final double durationMs;

        private Metrics(int sampleCount, int sampleRateHz, double rms, double peak,
                        double rmsDbfs, double peakDbfs, double zeroCrossingsPerSecond,
                        double dcOffset, double durationMs) {
            this.sampleCount = sampleCount;
            this.sampleRateHz = sampleRateHz;
            this.rms = rms;
            this.peak = peak;
            this.rmsDbfs = rmsDbfs;
            this.peakDbfs = peakDbfs;
            this.zeroCrossingsPerSecond = zeroCrossingsPerSecond;
            this.dcOffset = dcOffset;
            this.durationMs = durationMs;
        }

        public int getSampleCount() { return sampleCount; }
        public int getSampleRateHz() { return sampleRateHz; }
        /** RMS normalized to the signed PCM16 full-scale amplitude [0..1]. */
        public double getRms() { return rms; }
        /** Peak absolute amplitude normalized to PCM16 full scale [0..1]. */
        public double getPeak() { return peak; }
        public double getRmsDbfs() { return rmsDbfs; }
        public double getPeakDbfs() { return peakDbfs; }
        /** Number of sign transitions per second, excluding transitions through exact zero. */
        public double getZeroCrossingsPerSecond() { return zeroCrossingsPerSecond; }
        /** Mean signed sample value normalized to PCM16 full scale [-1..1]. */
        public double getDcOffset() { return dcOffset; }
        public double getDurationMs() { return durationMs; }
    }

    public Metrics analyze(short[] pcm, int length, int sampleRateHz) {
        if (pcm == null) throw new IllegalArgumentException("pcm is required");
        if (length <= 0 || length > pcm.length) {
            throw new IllegalArgumentException("length must be in 1..pcm.length");
        }
        if (sampleRateHz <= 0) throw new IllegalArgumentException("sampleRateHz must be positive");

        double sumSquares = 0.0;
        long sum = 0L;
        int peakAbs = 0;
        int crossings = 0;
        int previousSign = 0;

        for (int i = 0; i < length; i++) {
            int value = pcm[i];
            int absolute = Math.abs(value);
            if (absolute > peakAbs) peakAbs = absolute;
            sum += value;
            sumSquares += (double) value * value;

            int sign = value > 0 ? 1 : (value < 0 ? -1 : 0);
            if (sign != 0) {
                if (previousSign != 0 && sign != previousSign) crossings++;
                previousSign = sign;
            }
        }

        double rmsRaw = Math.sqrt(sumSquares / length);
        double rms = rmsRaw / PCM16_SCALE;
        double peak = peakAbs / PCM16_SCALE;
        double dcOffset = (sum / (double) length) / PCM16_SCALE;
        double durationMs = length * 1000.0 / sampleRateHz;
        double crossingRate = crossings * (double) sampleRateHz / length;

        return new Metrics(length, sampleRateHz, rms, peak,
                toDbfs(rms), toDbfs(peak), crossingRate, dcOffset, durationMs);
    }

    private static double toDbfs(double normalizedAmplitude) {
        if (normalizedAmplitude <= 0.0) return DBFS_FLOOR;
        return Math.max(DBFS_FLOOR, 20.0 * Math.log10(normalizedAmplitude));
    }
}
