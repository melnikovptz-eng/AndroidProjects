# VoiceActivation — technical specification

## Product behavior

The application continuously monitors audio only while the user has explicitly enabled the listening service and Android permits microphone foreground operation.

The pipeline is strictly sequential:

1. Capture microphone frames.
2. Run keyword spotting for the configured wake phrase.
3. On a wake phrase, collect a bounded speech segment.
4. Compute speaker representation and compare it with the enrolled owner profile.
5. Emit OWNER_VERIFIED only above a validated threshold; otherwise emit REJECTED.
6. Return to wake-word monitoring.

## Initial acceptance criteria

- No network permission or audio upload required for core recognition.
- Owner enrollment requires multiple clean utterances and confirmation.
- Wake-word and speaker thresholds are configurable internally and calibrated with real device recordings.
- No activation callback for an unknown speaker.
- Clear foreground notification while microphone is active.
- Service stops cleanly and exposes microphone/service state.
- Audio buffers are not persisted by default.
- Voice profile stored locally; provide reset/delete control.
- Test logs contain timestamps, state transitions and scores, but never raw audio or voice embeddings.

## Android constraints

Android background microphone access is restricted. Use a user-started microphone foreground service with the required foreground-service type and permissions for the target SDK. Do not promise automatic always-on behavior after reboot or while another app owns the microphone until verified on the target device and Android build.

## Model validation gate

Do not assume every sherpa-onnx feature has a ready-made Android wrapper. First validate the exact Android KWS API/model and speaker embedding API/model available in the selected upstream release. Benchmark ARM64 CPU load, RAM, latency and battery on TECNO KJ6.

## Planned public API (draft)

- VoiceActivation.initialize(context, config)
- VoiceActivation.enrollOwner(samples / guided enrollment session)
- VoiceActivation.startListening()
- VoiceActivation.stopListening()
- VoiceActivation.setListener(listener)

Callbacks:
- onWakeWordDetected()
- onOwnerVerified(confidence)
- onSpeakerRejected(confidence)
- onError(code, message)

The public API is provisional until the prototype proves the underlying pipeline.
