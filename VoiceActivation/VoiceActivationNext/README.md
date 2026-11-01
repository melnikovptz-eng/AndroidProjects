# Voice Activation Next

Android application ID: `ru.voiceactivation.next`. Offline voice-command prototype.

## Current implementation — version 1.1.0

- User-recorded command templates matched with 13-coefficient MFCC feature sequences and constrained DTW.
- Multiple examples per command (up to 6); matching uses the best example distance per command.
- Command decision uses an absolute DTW distance threshold (< 7.0) and a best-vs-second-command margin (best < 0.88 × second).
- Owner verification is a separate acoustic-profile comparison using cosine similarity of averaged MFCC vectors (> 0.88).
- A 15-second continuous command-test mode segments successive utterances and reports each match immediately.
- One toggle button changes from “Проверить голосовую команду” to “Отменить проверку голосовой команды”.
- Elapsed and remaining timers share Android monotonic elapsed-realtime time.
- Each test result shows the matched command, DTW distance, threshold, second-best distance and separate owner-profile result.
- Test mode is diagnostic only and does not launch the target application.

## Algorithm units

DTW distance is a dimensionless normalized MFCC-feature distance, not a percentage or probability. Lower is better. Owner cosine similarity is also dimensionless (nominally -1..1), not a probability. Both thresholds are provisional and require real-device calibration.

## Project documentation

See [PROJECT_PASSPORT.md](PROJECT_PASSPORT.md) for the architecture, feature parameters, thresholds, timer/VAD behavior, storage and open validation items. The parent [REBUILD_PLAN.md](../REBUILD_PLAN.md) records the overall product decisions.

## Validation status

The source change must pass GitHub Actions before an APK is published. Runtime behavior still requires testing on TECNO KJ6 / Android 13: quiet speech, pauses between commands, background noise, immediate match display, cancel behavior and timer synchronization. Do not treat CI success as device acceptance.
