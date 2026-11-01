# VoiceActivation Android prototype

This is the first Android application scaffold for the VoiceActivation project.

## Included in this revision

- Kotlin Android application module.
- Target app picker based on launcher activities.
- Selected target package persisted in private app preferences.
- Revalidation before launching the selected app.
- Manual launch test from the foreground UI.
- No network permission and no microphone capture in this scaffold.

## Explicitly not implemented yet

- Wake-word detection.
- Owner voice enrollment or speaker verification.
- Microphone foreground service.
- Automatic launch after voice verification.
- AAR library module.

The UI deliberately reports the recognition engine as **not connected**. The manual launch button only validates app selection and Android launch-intent handling; it is not a voice-activation test.

## Build baseline

- Android Gradle Plugin 8.6.1
- Kotlin Android plugin 2.0.20
- compileSdk 35 / targetSdk 35 / minSdk 26
- Java/Kotlin bytecode target 17

A Gradle wrapper is not included in this commit. Use an installed Gradle 8.7 distribution or add the wrapper after validating the local build environment.
