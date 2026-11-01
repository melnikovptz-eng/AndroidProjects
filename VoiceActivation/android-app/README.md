# VoiceActivation Android prototype

## Included

- Kotlin Android application scaffold.
- Installed launcher-app picker and persistent selected package.
- Manual foreground launch test for the selected target.
- sherpa-onnx v2.0.1 dependency with ARM64 ABI selection.
- Speaker embedding engine using upstream `SpeakerEmbeddingExtractor` and `SpeakerEmbeddingManager`.
- Owner enrollment from at least three clean 16 kHz utterances.
- Owner verification against the enrolled in-memory profile.
- Explicit two-stage activation state gate.

## Not yet complete

- Persistent encrypted owner voice profile storage.
- Microphone capture/enrollment UI and lifecycle.
- Keyword spotting integration and model delivery.
- Russian wake phrase model or validated Russian phrase detector.
- Foreground microphone service and background launch policy.
- End-to-end tests and APK validation on TECNO KJ6.

The speaker profile is currently held in memory only and is lost when the process exits. Automatic target-app launch is not yet wired to verification. The manual launch button remains a UI test only.

## Runtime baseline

- Android Gradle Plugin 8.6.1
- Kotlin Android plugin 2.0.20
- compileSdk / targetSdk 35, minSdk 26
- ARM64 only (`arm64-v8a`)
- sherpa-onnx v2.0.1 via JitPack

Recognition thresholds are provisional and must be calibrated using genuine owner and non-owner recordings on the target device.
