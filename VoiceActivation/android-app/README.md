# VoiceActivation Android prototype

## Implemented

- Kotlin Android app scaffold and installed-app picker.
- Persistent selected target package and manual launch check.
- sherpa-onnx v2.0.1 runtime dependency, ARM64 ABI.
- Streaming Russian wake-phrase candidate using sherpa-onnx T-one CTC model.
- Streaming sherpa-onnx KWS wrapper for models with supported language/token sets.
- Speaker embedding extraction, multi-utterance owner enrollment and verification.
- AES-GCM encrypted owner embeddings using AndroidKeyStore.
- SAF import of model files into app-private storage.
- Bounded 16 kHz microphone capture for owner enrollment.
- User-started microphone foreground service.
- Two-stage flow: Russian phrase transcript match -> owner voice verification -> selected app launch attempt.
- Notification action fallback when Android blocks launching another app from a background service.

## Russian wake phrase model

The current Russian candidate is `sherpa-onnx-streaming-t-one-russian-2025-09-08`:
- streaming Russian CTC ASR used as a phrase detector (not a dedicated KWS model);
- model input is 8 kHz; the microphone path captures 16 kHz and downsamples;
- the app requires `model.onnx` and `tokens.txt`;
- model/code license is Apache-2.0 according to the upstream T-one project.

Termux downloader:
`VoiceActivation/tools/download_russian_wake_model.sh`

Import both files in the app using **Импортировать русскую модель**.

## Owner voice model

Import a compatible sherpa-onnx speaker embedding ONNX model using **Импортировать модель голоса**. Then record three clean owner utterances. Embeddings are encrypted locally; raw audio is not saved.

The model's language coverage and verification threshold must be validated with Russian recordings. A speaker model trained on another language is only a candidate until it passes device tests.

## Build

- Android Gradle Plugin 8.6.1
- Kotlin Android plugin 2.0.20
- compileSdk / targetSdk 35, minSdk 28
- ARM64 only (`arm64-v8a`)
- sherpa-onnx v2.0.1 via JitPack
- GitHub Actions workflow: `.github/workflows/voice-activation-build.yml`

## Validation still required

- Compile and run the APK on TECNO KJ6 / Android 13.
- Validate T-one wake phrase recognition with the intended phrase, noise and distance.
- Validate speaker false-accept / false-reject rates with owner and non-owner recordings.
- Calibrate the threshold; the current 0.6 default is provisional.
- Benchmark CPU, RAM, latency and battery.
- Test foreground microphone restrictions and direct target-app launch behavior on the device.
- Verify model license and provenance for the selected speaker ONNX file before distribution.

Speaker verification is probabilistic and does not prevent replay or voice imitation. Do not use voice-only verification as the sole protection for sensitive actions.
