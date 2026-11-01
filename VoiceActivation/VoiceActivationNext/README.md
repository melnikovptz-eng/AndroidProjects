# Voice Activation Next

Test application ID: `ru.voiceactivation.next.test` (side-by-side with the existing `ru.voiceactivation.next`). Offline voice-command calibration prototype, version 1.4.0.

## Automatic calibration

The app now includes supervised automatic threshold fitting. Record labelled examples with the three calibration actions:

1. **Correct command, owner voice** — enter an existing command name in the command field, then record it.
2. **Unrelated phrase, owner voice** — record speech that is not any registered command.
3. **Other speaker** — record a sample spoken by another person.

Calibration data stores labels and measured metrics only; raw audio is not retained. The engine requires at least 10 correct command examples overall, at least 5 for each registered command, 10 unrelated phrases, 10 owner samples, and 10 other-speaker samples. It searches DTW and best/second margin thresholds against command labels, then independently fits the owner-cosine threshold. It holds out labelled examples for validation and reports balanced validation accuracy for command rejection/acceptance and owner discrimination.

Press **Рассчитать и проверить пороги** after collecting enough samples. A successful fit persists all three thresholds and both diagnostic and battle modes use them. If the sample requirements are not met, calibration is blocked and the existing thresholds remain unchanged.

The hold-out score is an empirical result for the collected samples, not a guarantee of biometric security or performance in every environment. Include varied pronunciation, distance, volume, background noise, and representative unrelated speech.

## Recognition and measurement

- MFCC features at 16 kHz mono PCM; command templates compared using constrained DTW.
- DTW distance is a measured distance, not a similarity percentage or probability.
- Owner MFCC cosine is a separate experimental speaker-profile check, not a secure biometric model.
- Continuous battle mode listens until manually stopped and reports each phrase immediately.
- The 15-second diagnostic mode reports each detected phrase and final counters.
- No speech-to-text transcription, command execution, app launching, or phone actions occur.

See [PROJECT_PASSPORT.md](PROJECT_PASSPORT.md) for full architecture, calibration requirements, limitations, storage, and validation.
