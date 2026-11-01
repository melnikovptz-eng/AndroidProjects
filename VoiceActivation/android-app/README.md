# VoiceActivation Android prototype

## Implemented

- Kotlin Android app scaffold and installed-app picker.
- sherpa-onnx v2.0.1 runtime dependency, ARM64 ABI.
- Speaker embedding enrollment and verification.
- AES-GCM encrypted owner embeddings using Android Keystore.
- SAF import of model files into app-private storage.
- Bounded 16 kHz microphone capture for owner enrollment.
- User-started microphone foreground service.
- Adaptive acoustic analyzer integrated into the live microphone path:
  - RMS and peak level per audio frame;
  - adaptive background-noise estimate;
  - hysteresis-based voice activity detection;
  - approximate SNR and quality classification;
  - provisional 6 dB average-SNR gate before speaker verification.
- Notification action fallback when Android blocks launching another app from a background service.

## Important current limitation

The current prototype still uses the old Russian wake-phrase detector and UI. The agreed target design has no mandatory wake phrase: it continuously segments speech, compares each utterance with user-recorded command templates, optionally verifies the owner, and executes the mapped action. The command-template subsystem is not implemented yet, so the current APK must not be described as supporting that final behavior.

## Acoustic analyzer

Source: `app/src/main/java/ru/voiceactivation/demo/voice/AcousticAnalyzer.kt`.

The analyzer estimates the noise floor during frames classified as non-speech and applies adaptive VAD thresholds. It reports approximate SNR; it does not perform neural denoising. The 6 dB rejection threshold is provisional and needs calibration on the TECNO KJ6.

## Existing model import

- Russian T-one model: legacy wake-phrase detector only.
- Speaker embedding ONNX model: speaker identity candidate, not command recognition.
- Owner profile: created locally from three recordings; embeddings are encrypted and raw recordings are not saved.

## Build

Termux build script: `VoiceActivation/tools/build_termux.sh` (stable output `~/storage/downloads/VoiceActivation.apk`).

GitHub Actions workflow: `.github/workflows/voice-activation-build.yml`.

## Validation still required

- Compile, install and run on TECNO KJ6 / Android 13 / ARM64.
- Calibrate SNR/VAD thresholds with quiet speech, distance and background noise.
- Implement and validate Russian command-template matching.
- Validate speaker false-accept / false-reject rates with Russian owner and non-owner recordings.
- Benchmark CPU, RAM, latency, thermal behavior and battery.
- Test foreground microphone restrictions and target-app launch behavior.
