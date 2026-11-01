# VoiceActivation — technical specification

## Product behavior

The user selects an installed Android application and a Russian wake phrase. VoiceActivation listens only while the user has explicitly started the microphone foreground service and Android permits microphone use.

Activation requires two sequential checks:

1. Russian wake phrase detection from the microphone stream.
2. Speaker verification of the same captured utterance against the enrolled owner profile.

Only after both checks pass does VoiceActivation attempt to open the selected application. If Android blocks background activity launch, the persistent notification provides a user-tap action for that target.

## User configuration

- Wake phrase: editable Russian phrase, recognized using a Russian streaming ASR model as a phrase detector.
- Owner voice profile: guided enrollment from at least three clean utterances.
- Speaker model: imported ONNX model, validated against the exact model fingerprint stored with the encrypted profile.
- Target application: selected from installed applications exposing a launcher activity.
- User can change the target, change the wake phrase, stop listening, or delete the voice profile.

## Implemented prototype pipeline

1. Capture mono 16 kHz PCM from AudioRecord.
2. Downsample to 8 kHz for the Russian T-one streaming CTC model.
3. Match the configured phrase in the recognized endpoint transcript.
4. Use the corresponding captured speech utterance for speaker embedding extraction at 16 kHz.
5. Verify the embedding with SpeakerEmbeddingManager and the enrolled owner profile.
6. If verified, resolve the selected package and attempt to launch its launcher activity.
7. If rejected, do not launch anything; resume wake phrase monitoring.

## Model choices

- Russian phrase detection: sherpa-onnx streaming T-one CTC model, `sherpa-onnx-streaming-t-one-russian-2025-09-08`. This is ASR-based phrase detection, not a dedicated Russian KWS model. The upstream T-one project declares Apache-2.0.
- Speaker verification: imported sherpa-onnx speaker embedding ONNX model. The supplied downloader retrieves a 3D-Speaker ERes2Net VoxCeleb English model candidate. Its performance on Russian utterances must be measured before acceptance.
- Threshold 0.6 is provisional, not a calibrated production threshold.

## Target-app picker

- Query installed applications with a launcher activity.
- Display human-readable app labels.
- Persist package name, not list position.
- Revalidate package availability before launch.
- Use narrow package visibility through the launcher intent query; do not request QUERY_ALL_PACKAGES.

## Privacy and security

- No Internet permission in the app manifest.
- Audio processing is local.
- Raw audio is held in memory only and is not written to storage.
- Owner embeddings are encrypted with AES-GCM using an AndroidKeyStore key.
- Encrypted profiles are bound to the SHA-256 fingerprint of the selected speaker model.
- Provide an explicit profile deletion control.
- Do not log raw audio or embeddings.
- Voice verification is probabilistic; replay and voice imitation can succeed. Sensitive actions require a second factor.

## Android constraints

- Microphone monitoring is user-started through a foreground service with microphone service type and visible notification.
- Android 13+ notification permission is requested before starting listening.
- Android background activity launch restrictions may block direct target-app opening from a service. A notification action is provided as fallback.
- Automatic start after reboot, unrestricted screen-off operation and battery-optimization exemptions are not claimed until tested on TECNO KJ6.

## Acceptance tests still required

- Build, install and run on TECNO KJ6 / Android 13 / ARM64.
- Validate configured Russian phrases, accent variation, noise and distance.
- Test false acceptance and false rejection with owner and non-owner recordings.
- Calibrate threshold on device recordings.
- Benchmark CPU, RAM, latency, thermal behavior and battery.
- Test microphone service lifecycle, screen-off behavior and background launch policy.
- Verify license/provenance of the selected speaker model before redistribution.

## Planned public API (not frozen)

- `VoiceActivation.initialize(context, config)`
- `VoiceActivation.enrollOwner(guidedEnrollmentSession)`
- `VoiceActivation.selectTargetApp(packageName)`
- `VoiceActivation.startListening()`
- `VoiceActivation.stopListening()`
- `VoiceActivation.setListener(listener)`

A stable AAR API is deferred until the standalone prototype passes device validation.
