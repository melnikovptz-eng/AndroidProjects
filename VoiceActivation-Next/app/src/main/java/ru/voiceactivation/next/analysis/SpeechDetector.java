package ru.voiceactivation.next.analysis;

/**
 * Detects speech-like acoustic bursts from continuous PCM frame measurements.
 * This is level-based segmentation, not speech recognition.
 */
public final class SpeechDetector {
    public enum Event { NONE, SPEECH_STARTED, SPEECH_ENDED }

    private final NoiseEstimator noise;
    private final double startFloorDbfs;
    private final double startMarginDb;
    private final double endMarginDb;
    private final int startFramesRequired;
    private final int endFramesRequired;
    private boolean speaking;
    private int loudFrames;
    private int quietFrames;
    private double lastNoiseDbfs = -60.0;

    public SpeechDetector() {
        this(new NoiseEstimator(150, 0.25), -48.0, 10.0, 5.0, 3, 12);
    }

    public SpeechDetector(NoiseEstimator noise, double startFloorDbfs,
                          double startMarginDb, double endMarginDb,
                          int startFramesRequired, int endFramesRequired) {
        if (noise == null) throw new IllegalArgumentException("noise is required");
        if (startFramesRequired < 1 || endFramesRequired < 1) {
            throw new IllegalArgumentException("frame counts must be positive");
        }
        this.noise = noise;
        this.startFloorDbfs = startFloorDbfs;
        this.startMarginDb = startMarginDb;
        this.endMarginDb = endMarginDb;
        this.startFramesRequired = startFramesRequired;
        this.endFramesRequired = endFramesRequired;
    }

    public synchronized Event accept(AcousticAnalyzer.Metrics metrics) {
        if (metrics == null) throw new IllegalArgumentException("metrics is required");
        double level = metrics.getRmsDbfs();
        double baseline = noise.estimateDbfs();
        if (!Double.isNaN(baseline)) lastNoiseDbfs = baseline;

        double startThreshold = Math.max(startFloorDbfs, lastNoiseDbfs + startMarginDb);
        double endThreshold = Math.max(startFloorDbfs - 6.0, lastNoiseDbfs + endMarginDb);

        if (!speaking) {
            // Learn the background only while the detector is idle.
            if (level < startThreshold) {
                lastNoiseDbfs = noise.addFrame(level);
            }
            if (level >= startThreshold) {
                loudFrames++;
            } else {
                loudFrames = 0;
            }
            if (loudFrames >= startFramesRequired) {
                speaking = true;
                loudFrames = 0;
                quietFrames = 0;
                return Event.SPEECH_STARTED;
            }
            return Event.NONE;
        }

        if (level < endThreshold) {
            quietFrames++;
        } else {
            quietFrames = 0;
        }
        if (quietFrames >= endFramesRequired) {
            speaking = false;
            quietFrames = 0;
            loudFrames = 0;
            return Event.SPEECH_ENDED;
        }
        return Event.NONE;
    }

    public synchronized boolean isSpeaking() { return speaking; }
    public synchronized double getNoiseDbfs() { return lastNoiseDbfs; }
    public synchronized double getStartThresholdDbfs() {
        return Math.max(startFloorDbfs, lastNoiseDbfs + startMarginDb);
    }

    public synchronized void reset() {
        speaking = false;
        loudFrames = 0;
        quietFrames = 0;
        lastNoiseDbfs = -60.0;
        noise.reset();
    }
}