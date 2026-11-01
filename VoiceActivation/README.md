# VoiceActivation

Android voice activation prototype. The selected application is opened only after:

1. A configured Russian wake phrase is detected.
2. The detected utterance is verified against the enrolled owner voice profile.

All recognition is local. The Android app imports model files into private storage; the core manifest does not request Internet access.

## Current implementation

The Android prototype is in [android-app](android-app/README.md).

- Installed-app picker; selected package name is persisted.
- Russian streaming phrase detector using sherpa-onnx T-one CTC ASR (8 kHz model input).
- Speaker embedding enrollment and verification using sherpa-onnx Kotlin APIs.
- Three 4-second enrollment utterances.
- Encrypted owner embeddings (AES-GCM / Android Keystore), bound to the speaker model SHA-256.
- User-started microphone foreground service.
- Verification uses the same captured utterance that triggered the wake phrase.
- Target app launch attempt after both checks; notification action fallback for Android background-activity restrictions.
- Termux scripts download the Russian wake model and a speaker-model candidate into Android Downloads.

## Target device

- TECNO KJ6
- Android 13 / ARM64

## Validation still required

- Build and install the APK on the target phone.
- Validate Russian wake phrase recognition in real environments.
- Validate speaker false-accept / false-reject rates using Russian owner and non-owner recordings.
- Calibrate the speaker threshold and measure CPU, memory, latency and battery.
- Test background microphone operation and target-app launch behavior on the device.

The public AAR API is not frozen yet. Do not treat this prototype as a high-security identity system: replay and voice imitation remain possible.
