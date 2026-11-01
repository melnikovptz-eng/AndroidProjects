# Voice Activation Next

Test application ID: `ru.voiceactivation.next.test` (side-by-side with the existing `ru.voiceactivation.next`). Offline voice-command prototype, version 1.2.0.

## Current diagnostic flow

1. Record the owner's acoustic profile.
2. Record one or more command samples by name (up to 6 per command).
3. Calibrate by repeating the same commands and reviewing measured DTW and owner-cosine values.
4. Use the 15-second phrase test to inspect successive utterances. The later product stage will be the separate command-execution mode; this build does not execute actions.

## Per-phrase test output

- Each detected speech segment is numbered and analysed as soon as its boundary is detected.
- Every detected segment is accounted for; segments shorter than 200 ms are explicitly reported as too short rather than silently discarded.
- Each usable phrase reports nearest command, accepted/rejected DTW result, actual DTW distance, absolute threshold (7.0), second-best distance, margin check (0.88), and separate owner MFCC cosine result (threshold 0.88).
- Final counters report detected phrases, accepted command matches, rejected/unmatched phrases, owner accepted and owner rejected.
- The elapsed and remaining timers share Android `SystemClock.elapsedRealtime()`.
- Phrase boundary: 250 ms silence; a segment is forcibly processed at 5 seconds.

## Important interpretation

MFCC + DTW is the command-template comparison. The owner MFCC cosine is a separate speaker-profile check. The owner may pass while the command fails; the two results are intentionally independent.

There is no speech-to-text transcription in this build: the UI shows the closest registered command label and acoustic metrics, not the literal words spoken.

See [PROJECT_PASSPORT.md](PROJECT_PASSPORT.md) for complete architecture, thresholds, units, limitations, storage and validation requirements.
