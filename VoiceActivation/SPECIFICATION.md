# VoiceActivation — technical specification

## Target product behavior

VoiceActivation continuously analyzes microphone audio while the user has explicitly started the microphone foreground service and Android permits microphone use. There is no mandatory wake phrase.

The intended activation pipeline is:

1. Capture mono PCM audio continuously.
2. Estimate background noise, speech level and signal-to-noise ratio (SNR).
3. Detect speech with adaptive thresholds and segment it into utterances.
4. Compare the utterance with user-recorded command templates using a command-content model. A command match must be based on what was said, not only on who said it.
5. Verify the speaker against the enrolled owner profile.
6. Execute the action bound to the matched command only when command and owner checks pass.
7. If audio quality is insufficient or a match is ambiguous, do nothing and continue listening.

A separate speaker embedding model answers “who is speaking”; it does not recognize command meaning. Command matching therefore requires its own ASR or audio-embedding model.

## Acoustic front end

Implemented in `voice/AcousticAnalyzer.kt` and connected to the microphone service:

- Per-frame RMS level and peak level.
- Adaptive background-noise estimate updated during non-speech frames.
- Hysteresis-based voice activity detection (VAD).
- Approximate SNR estimate in dB.
- Quality bands: no speech, poor, weak, good.
- Current service rejects an utterance before speaker verification when average estimated SNR is below 6 dB.

This is an acoustic level/VAD analyzer, not a neural noise-removal system. The SNR estimate is provisional and must be calibrated using real recordings on the target phone. A low SNR gate may reject quiet speech; thresholds must be tuned against false accepts and false rejects.

## Current prototype status

The currently built prototype still contains the earlier Russian wake-phrase detector and its UI. The acoustic analyzer has been added to the microphone path, but the no-wake-phrase command-template flow is not yet implemented. Do not treat the current APK as the final command-vector design.

Existing pieces:
- 16 kHz mono microphone capture in a user-started foreground service.
- Adaptive acoustic analysis and speech segmentation.
- Speaker embedding enrollment and verification.
- AES-GCM encrypted owner embeddings using Android Keystore.
- Selected-app launch with notification fallback for Android background-launch restrictions.

## Planned command-template subsystem

- Record one or more example utterances for each user-defined command.
- Extract a content representation or transcript for each example.
- Store the command representation and its bound action locally.
- At runtime, compare each speech segment against saved command templates.
- Require a calibrated similarity margin between the best and second-best match; otherwise reject.
- Optionally require owner-speaker verification before executing the action.
- Do not confuse command embeddings with speaker embeddings.

## Models

- Existing Russian phrase detector: sherpa-onnx streaming T-one CTC ASR model. It is only part of the legacy wake-phrase prototype.
- Speaker verification: imported sherpa-onnx speaker embedding ONNX model. The supplied downloader retrieves a 3D-Speaker ERes2Net VoxCeleb English candidate; performance on Russian speech remains unvalidated.
- Command-content recognition: model and representation format are not yet selected or implemented.
- Speaker threshold 0.6 and acoustic SNR gate 6 dB are provisional, not production-calibrated.

## Target-app and action handling

- Query installed apps with a launcher activity and persist package names.
- Revalidate package availability before launch.
- Use narrow package visibility; do not request QUERY_ALL_PACKAGES.
- Android may block direct app launches from a foreground service; provide a notification action fallback.
- Future command actions must be explicitly mapped and confirmed by the user before activation.

## Privacy and security

- Audio processing is local.
- Raw audio is held in memory only and is not written to storage.
- Owner embeddings and command templates should be stored locally; protect owner embeddings with AES-GCM / Android Keystore.
- Do not log raw audio, transcripts containing sensitive content, or embeddings.
- Voice verification is probabilistic; replay and voice imitation can succeed. Sensitive actions require a second factor.

## Android constraints

- Microphone monitoring is user-started through a foreground service with microphone service type and visible notification.
- Android 13+ notification permission is requested before starting listening.
- Automatic start after reboot, unrestricted screen-off operation and battery-optimization exemptions are not claimed until tested on TECNO KJ6.

## Acceptance tests still required

- Build, install and run on TECNO KJ6 / Android 13 / ARM64.
- Measure command recognition accuracy with Russian speech, accents, distance and noise.
- Calibrate SNR/VAD thresholds using quiet speech and multiple noise environments.
- Test false command activation and false rejection.
- Test owner/non-owner speaker verification, replay resistance limits and threshold calibration.
- Benchmark CPU, RAM, latency, thermal behavior and battery.
- Test microphone lifecycle, screen-off behavior and background launch policy.
- Verify license/provenance of all selected models before redistribution.

## Model storage on the Android device

The canonical source location for model files is the shared Android Download directory. Keep this layout and filenames stable:

```text
/storage/emulated/0/Download/vosk model/
├── speaker-model/
│   └── 3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx
└── russian-asr-model/
    ├── model.onnx
    └── tokens.txt
```

Equivalent Termux path: `$HOME/storage/downloads/vosk model/`.

Verified files currently expected at these paths:

- Speaker model: `speaker-model/3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx`, 26,485,263 bytes, SHA-256 `c59158379255ad66e161679cca6af8d52d51e389e3224ab7d7a7baae295c2db5`.
- Russian ASR model: `russian-asr-model/model.onnx`, 144,193,702 bytes, SHA-256 `5ded080e2a6c86ecc11bcb0902d77524eb3e8b0844cb0c0754347f5aafb4dabc`.
- Russian tokens: `russian-asr-model/tokens.txt`, 202 bytes, SHA-256 `27f7b3ba2096c572375fba1a6b29af1f80d86e08a329940612908112695f97e0`.

### Automatic import requirement

The current prototype does **not** automatically scan this Download folder: it opens the Android document picker and copies selected files into app-private storage. The next import implementation must ask the user to grant access to the `Download/vosk model` directory once using Android's Storage Access Framework directory picker, persist the read permission, and then discover/import the three known filenames from their subfolders automatically on later launches. Validate file presence, size and SHA-256 before accepting a model. Copy verified files into app-private storage for inference; keep Download as the canonical user-managed source. If the directory permission is revoked or a file is missing/changed, show a clear repair status and request folder access again. Do not silently claim automatic import until this flow is implemented and device-tested.

## Planned public API (not frozen)

- `VoiceActivation.initialize(context, config)`
- `VoiceActivation.enrollOwner(guidedEnrollmentSession)`
- `VoiceActivation.addCommandTemplate(audio, action)`
- `VoiceActivation.startListening()`
- `VoiceActivation.stopListening()`
- `VoiceActivation.setListener(listener)`

A stable AAR API is deferred until the standalone prototype passes device validation.
