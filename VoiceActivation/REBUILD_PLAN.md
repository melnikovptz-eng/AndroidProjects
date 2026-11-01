# VoiceActivation — clean rebuild plan

## Product goal

An offline Android application that listens only while the user has explicitly started its microphone foreground service. It recognizes user-enrolled spoken commands from audio, verifies the enrolled owner separately, and launches the application/action bound to the matched command.

## Decisions

- No mandatory wake phrase.
- Command identity is determined from the audio content, not from speaker identity and not by a fixed ASR phrase.
- First command-matching implementation: log-Mel/MFCC feature sequences + Dynamic Time Warping (DTW), with multiple enrolled examples per command and a calibrated rejection margin.
- Speaker identity remains a separate verification stage using the imported 3D-Speaker embedding model.
- Acoustic front end performs VAD, adaptive noise-floor estimation, high-pass/wind rumble handling and level normalization. It must not treat simple RMS/SNR as a complete noise-removal solution.
- Do not lower match thresholds simply because the environment is noisy. If the signal is not reliable or the best/second-best match margin is too small, reject and keep listening.
- No ASR model is required for the first audio-template matcher. ASR may be an optional diagnostic/fallback module, not the authority for command identity.
- All inference is local. Raw audio is transient and is not persisted by default.
- Owner embeddings and command templates are local; owner profile remains encrypted with Android Keystore.
- Launch only the action explicitly bound to a confirmed command and only after owner verification succeeds.

## Runtime pipeline

1. Capture mono PCM audio.
2. Adaptive VAD segments speech and retains pre-roll/post-roll.
3. Estimate noise conditions and apply conservative filtering/normalization.
4. Extract a sequence of MFCC/log-Mel feature frames.
5. Compare the sequence against each stored command example using constrained DTW.
6. Aggregate distances across examples; require absolute threshold and best-vs-second-best margin.
7. Independently verify the speaker with the owner embedding model.
8. Execute the bound action only if both command and owner checks pass.
9. If uncertain, do nothing and resume listening.

## Enrollment

- User creates a named command and chooses an installed target app/action.
- Capture several repetitions at natural pace and normal speaking volume.
- Store feature sequences and metadata, not raw recordings by default.
- Support multiple examples per command and allow re-record/delete.
- Capture a few examples under different realistic conditions, but do not require a separate template for every environment.
- Enroll owner voice separately with multiple utterances.

## Noise robustness

- Estimate the noise floor only during non-speech frames.
- Use VAD hysteresis and short pre-roll/post-roll buffers.
- Apply a conservative high-pass filter to reduce handling/wind rumble; avoid aggressive filtering that removes speech.
- Normalize level without clipping.
- Evaluate MFCC stability under car/road noise, wind, music and nearby speech.
- Reject poor/ambiguous input instead of forcing a match.
- Measure false accept and false reject rates using held-out recordings.

## Safety and Android behavior

- Microphone runs only in a user-started foreground service with a persistent notification.
- Do not claim unrestricted background listening; Android/OEM restrictions must be device-tested.
- Speaker verification is probabilistic and is not replay-proof. Sensitive actions need a second factor.
- Keep applicationId stable: ru.voiceactivation.demo.
- Keep model source files in the user's existing Download/vosk model folder; automatic folder import must use SAF directory permission and verify expected file hashes before copying.
- Never require uninstalling the existing app just to test a new build. Preserve existing branch and data until stable signing is configured.

## Implementation stages

1. Replace legacy wake-phrase service flow with explicit pipeline interfaces and state machine.
2. Implement and unit-test MFCC extraction and constrained DTW matcher using synthetic and recorded PCM fixtures.
3. Add command-template enrollment/storage and action binding.
4. Integrate adaptive acoustic front end and evaluate noisy recordings.
5. Integrate owner verification as a separate gate.
6. Add SAF folder import with integrity checks for models.
7. Configure persistent release/debug signing so updates do not break installed app data.
8. Build, inspect APK, install/update on TECNO KJ6 and run acceptance tests.

## Acceptance criteria

- Different speaking speeds can match the same enrolled command.
- Similar-sounding different commands are rejected when confidence margin is insufficient.
- Quiet intelligible speech is not rejected solely by a fixed SNR cutoff.
- Noise cannot cause thresholds to be relaxed into unsafe false activations.
- A non-owner cannot trigger an action in the tested speaker-verification set.
- Unknown commands and ambiguous matches never launch an app.
- Existing user model files remain outside the APK and can be imported without manual file-by-file selection.
- Update installation preserves app data using the same signing key.
- CPU, RAM, latency, temperature and battery are measured on TECNO KJ6.

## Important limitation

MFCC + DTW is a compact hand-built baseline, not a guarantee of robust recognition in arbitrary noise. It must be benchmarked against real recordings. If it does not meet measured accuracy targets, choose a compact command-content embedding model based on test results rather than assumptions.
