# VoiceActivation

Reusable Android voice-activation module, initially developed as a standalone test app and later extracted as an Android AAR library.

## Goal

Activate only after both checks pass:

1. A configured wake phrase is detected.
2. The speaker matches the enrolled owner voice profile.

All audio processing is intended to run locally on the device. No audio is uploaded to a server.

## Target device

- TECNO KJ6
- Android 13
- ARM64 Android runtime

## Technology baseline

- Kotlin / Android
- sherpa-onnx for on-device keyword spotting and speaker-model support
- Android AudioRecord for microphone capture
- Foreground service for user-visible continuous listening, subject to Android microphone/background restrictions

Upstream: https://github.com/k2-fsa/sherpa-onnx

## Planned modules

- audio: microphone capture, sample-rate/channel conversion, audio lifecycle
- wakeword: keyword spotting and wake phrase configuration
- speaker: owner enrollment, embeddings, similarity/threshold evaluation
- verification: two-stage decision and anti-replay limitations
- service: foreground lifecycle and restart handling
- api: stable public library interface
- app: enrollment, permissions, test console and diagnostics

## Activation state machine

IDLE -> WAKE_WORD_DETECTED -> VERIFYING_OWNER -> ACTIVATED
                                      \-> REJECTED -> IDLE

No app callback is emitted before owner verification succeeds.

## Development stages

1. Verify sherpa-onnx Android KWS demo and model compatibility.
2. Verify speaker embedding / identification support on Android.
3. Build a test app with explicit enrollment and live test screens.
4. Implement combined wake-word + owner-verification pipeline.
5. Test false accepts/rejects, noisy environments and screen-off behavior.
6. Extract stable API as an AAR.

## Security notes

Speaker matching is probabilistic, not proof of identity. Recordings and voice imitation can fool voice-only systems. Sensitive actions must require a second factor. Enrollment must be explicit and local. The app must clearly show when microphone monitoring is active.

## Current status

Project scaffold and architecture agreed. Runtime implementation and model selection are pending device-side validation.
