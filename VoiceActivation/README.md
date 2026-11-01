# VoiceActivation

Reusable Android voice-activation module, initially developed as a standalone test app and later extracted as an Android AAR library.

## Goal

Open an application selected by the user, but only after both checks pass:

1. A configured wake phrase is detected.
2. The speaker matches the enrolled owner voice profile.

After successful verification, VoiceActivation launches the user's selected Android application. The target is configured in the VoiceActivation settings; it is not hard-coded. The user can change it at any time.

All audio processing is intended to run locally on the device. No audio is uploaded to a server.

## User flow

1. User grants microphone permission and explicitly starts the listening service.
2. User enrolls their voice and configures a wake phrase.
3. User selects a target app from the installed launchable applications.
4. VoiceActivation waits for the wake phrase.
5. It verifies that the speaker is the enrolled owner.
6. If verified, it opens the selected application. If not, it ignores the attempt.

## Target device

- TECNO KJ6
- Android 13
- ARM64 Android runtime

## Technology baseline

- Kotlin / Android
- sherpa-onnx for on-device keyword spotting and speaker-model support
- Android AudioRecord for microphone capture
- Android package/launcher APIs for selecting and opening the target app
- Foreground service for user-visible continuous listening, subject to Android microphone/background restrictions

Upstream: https://github.com/k2-fsa/sherpa-onnx

## Planned modules

- audio: microphone capture, sample-rate/channel conversion, audio lifecycle
- wakeword: keyword spotting and wake phrase configuration
- speaker: owner enrollment, embeddings, similarity/threshold evaluation
- verification: two-stage decision and anti-replay limitations
- launcher: installed-app list, user selection, launch and failure handling
- service: foreground lifecycle and restart handling
- api: stable public library interface
- app: enrollment, permissions, target-app selection, test console and diagnostics

## Activation state machine

IDLE -> WAKE_WORD_DETECTED -> VERIFYING_OWNER -> OWNER_VERIFIED -> LAUNCH_SELECTED_APP
                                      \-> REJECTED -> IDLE

No target application is launched before owner verification succeeds.

## Development stages

1. Verify sherpa-onnx Android KWS demo and model compatibility.
2. Verify speaker embedding / identification support on Android.
3. Build a test app with explicit enrollment and live test screens.
4. Add installed-app picker and selected-app launch flow.
5. Implement combined wake-word + owner-verification pipeline.
6. Test false accepts/rejects, noisy environments and screen-off behavior.
7. Extract stable API as an AAR.

## Security notes

Speaker matching is probabilistic, not proof of identity. Recordings and voice imitation can fool voice-only systems. Sensitive actions must require a second factor. Enrollment must be explicit and local. The app must clearly show when microphone monitoring is active.

## Current status

Project scaffold and architecture agreed. Runtime implementation and model selection are pending device-side validation.
