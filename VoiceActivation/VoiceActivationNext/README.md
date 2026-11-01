# Voice Activation Next

New Android application id: ru.voiceactivation.next. This is an initial runnable baseline: user-recorded command templates matched by MFCC + constrained DTW, plus a separate acoustic owner-profile gate. It is not yet a production-grade speaker-verification system or validated for arbitrary vehicle/wind noise. The next milestone is replacing the owner MFCC centroid with the supplied 3D-Speaker ONNX embedding and calibrating thresholds against genuine/impostor/noise samples.
