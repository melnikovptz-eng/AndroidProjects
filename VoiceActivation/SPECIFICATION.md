# VoiceActivation — technical specification

## Product behavior

The application continuously monitors audio only while the user has explicitly enabled the listening service and Android permits microphone foreground operation.

After successful wake-phrase detection and owner verification, the application launches the Android app selected by the user in VoiceActivation settings.

## User configuration

- Wake phrase: configurable by the user, subject to the keyword model's supported phrases/language.
- Owner voice profile: created through guided enrollment.
- Target application: selected from installed applications that expose a launchable activity.
- The user can change the target app or disable automatic launching at any time.
- Show the selected app name and icon in settings.

## Recognition and launch pipeline

1. Capture microphone frames.
2. Run keyword spotting for the configured wake phrase.
3. On a wake phrase, collect a bounded speech segment.
4. Compute speaker representation and compare it with the enrolled owner profile.
5. If the similarity exceeds the validated threshold, emit OWNER_VERIFIED.
6. Resolve the selected target package and launch its main/launcher activity using Android package/intent APIs.
7. Report LAUNCH_SUCCEEDED or LAUNCH_FAILED, then return to wake-word monitoring.
8. If the speaker is not verified, do not launch anything; report REJECTED and return to monitoring.

## Target-app picker

- Query installed apps that have a launcher activity.
- Display app icon and human-readable label.
- Persist the selected package name, not a fragile list position.
- Revalidate that the package remains installed and launchable before opening.
- If it has been uninstalled or disabled, notify the user and request a new selection.
- Respect Android package visibility rules and declare only the necessary package visibility configuration for the target SDK. Avoid requesting broad package visibility unless justified.

## Initial acceptance criteria

- No network permission or audio upload required for core recognition.
- Owner enrollment requires multiple clean utterances and confirmation.
- Wake-word and speaker thresholds are configurable internally and calibrated with real device recordings.
- No app launch or activation callback for an unknown speaker.
- A user-selected installed app launches after successful owner verification.
- Clear foreground notification while microphone is active.
- Service stops cleanly and exposes microphone/service state.
- Audio buffers are not persisted by default.
- Voice profile stored locally; provide reset/delete control.
- Test logs contain timestamps, state transitions and scores, but never raw audio or voice embeddings.
- Launch failure is visible and does not crash the listening service.

## Android constraints

Android background microphone access is restricted. Use a user-started microphone foreground service with the required foreground-service type and permissions for the target SDK. Do not promise automatic always-on behavior after reboot or while another app owns the microphone until verified on the target device and Android build. Starting another activity from the background is also subject to Android background-activity-launch restrictions; test the exact foreground-service/notification flow on TECNO KJ6 and use an allowed user-visible flow if direct launch is blocked.

## Model validation gate

Do not assume every sherpa-onnx feature has a ready-made Android wrapper. First validate the exact Android KWS API/model and speaker embedding API/model available in the selected upstream release. Benchmark ARM64 CPU load, RAM, latency and battery on TECNO KJ6.

## Planned public API (draft)

- VoiceActivation.initialize(context, config)
- VoiceActivation.enrollOwner(guidedEnrollmentSession)
- VoiceActivation.selectTargetApp(packageName)
- VoiceActivation.startListening()
- VoiceActivation.stopListening()
- VoiceActivation.setListener(listener)

Callbacks:
- onWakeWordDetected()
- onOwnerVerified(confidence)
- onTargetAppLaunchSucceeded(packageName)
- onTargetAppLaunchFailed(packageName, reason)
- onSpeakerRejected(confidence)
- onError(code, message)

The public API is provisional until the prototype proves the underlying pipeline.
