# VoiceActivation Calibration

Standalone calibration application for microphone threshold tuning and Russian command recognition.

## Project isolation
This is a fresh implementation in `VoiceActivation/CalibrationApp`. It does not reuse the previous VoiceActivation implementation. Package: `ru.voiceactivation.calibration`.

## Current source components
- `AudioCapture`: 16 kHz mono PCM16 microphone capture and bounded speech-segment buffering with pre-roll.
- `AcousticMath`: RMS, peak, dBFS and SNR calculations.
- `NoiseEstimator`: adaptive background estimate and manual 5-second noise calibration.
- `SpeechSegmenter`: threshold, hysteresis, trigger duration, silence end, max segment and cooldown.
- `LevelChartView`: signal/noise/threshold chart.
- `VoskEngine`: local Vosk model loading and segment recognition.
- `CommandMatcher`: Russian normalization and fuzzy wake-word matching for «Яша» variants.
- `CommandSequenceTracker`: one-segment and two-segment «Яша» → «поехали» sequence with timeout and inter-word pause.
- `ExperimentRepository`: labelled speech/background windows, threshold search and JSON data.
- `ProfileRepository`: named settings profiles and combined JSON import/export.

## Current UI
- Live RMS, peak, SNR, trigger level and threshold.
- RMS/peak trigger selector.
- Ten acoustic timing/level controls plus command similarity and two-word timing controls.
- Five-second background noise measurement with median, mean, P90 and spread.
- Speech/background labels for threshold calibration.
- Threshold recommendation and same-dataset profile comparison.
- Local Vosk model folder picker, partial/final transcript display and latency measurement.

## Local Vosk model
Choose a directory containing a Vosk model (including `am/final.mdl`) through the Android folder picker. The app copies it to private storage and attempts to restore it on next launch. A sherpa-onnx model directory is not a Vosk model and will be rejected.

## Build gate
Read `PASSPORT.md` and `TASK_STATUS.md`. APK assembly is forbidden until every mandatory passport task is implemented and independently checked. No APK has been built for this branch.
