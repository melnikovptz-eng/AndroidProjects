# VoiceActivation

Android voice activation prototype. The target design continuously analyzes speech without a mandatory wake phrase, matches it against user-recorded command templates, optionally verifies the owner, and executes the mapped action.

All processing is intended to remain local. Microphone access is user-started through an Android foreground service.

## Current implementation

The Android prototype is in [android-app](android-app/README.md).

- Installed-app picker and persisted target package.
- 16 kHz mono microphone capture in a foreground service.
- Adaptive acoustic analyzer: RMS/peak, background-noise estimate, VAD hysteresis and approximate SNR.
- Provisional 6 dB average-SNR gate before speaker verification.
- Speaker embedding enrollment and verification using sherpa-onnx.
- Three 4-second owner enrollment utterances.
- Encrypted owner embeddings (AES-GCM / Android Keystore).
- Existing prototype still uses the earlier Russian wake-phrase detector; this is not yet the target no-wake-phrase command-template architecture.

## Next implementation stage

Replace the legacy wake-phrase UI/detector with:
- recording and naming command examples;
- storing command-content representations linked to actions;
- matching incoming utterances to the saved command templates;
- rejecting ambiguous or low-quality matches;
- optional owner verification before executing an action.

Speaker embeddings and command-content representations are different data and must use separate models.

## Model files on Android

Keep the downloaded models in this fixed Android Download layout:

```text
Download/vosk model/
├── speaker-model/3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx
└── russian-asr-model/model.onnx
                            tokens.txt
```

The current app still imports through the document picker. Automatic import is a required next change: grant access to this directory once, persist the SAF folder permission, discover the fixed filenames, verify file size/SHA-256, and copy verified models into app-private storage. This is documented as a requirement, not a feature already implemented.

## Target device

- TECNO KJ6
- Android 13 / ARM64

## Validation still required

- Install and run on the target phone.
- Validate acoustic level/SNR estimates across quiet speech, distance and background noise.
- Validate Russian command matching and owner verification.
- Calibrate thresholds and measure CPU, RAM, latency, heat and battery.
- Test foreground microphone and target-app launch behavior.

Voice verification is probabilistic and does not prevent replay or voice imitation. Do not use voice-only verification as the sole protection for sensitive actions.
