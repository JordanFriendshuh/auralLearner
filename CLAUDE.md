# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew assembleRelease      # Build release APK
./gradlew test                 # Run unit tests (JVM)
./gradlew connectedAndroidTest # Run instrumented tests (requires device/emulator)
./gradlew lint                 # Run Android lint
./gradlew clean                # Clean build artifacts

# Run a specific unit test class
./gradlew test --tests "com.example.aurallearner.ExampleUnitTest"
```

## Architecture

Single-module, single-activity Jetpack Compose app. No MVVM/MVI — all UI state and business logic live in `MainActivity.kt` using `remember`/`mutableStateOf`.

**Key files:**
- `app/src/main/java/com/example/aurallearner/MainActivity.kt` — The entire app: Activity lifecycle, SoundPool/TTS setup, `IntervalTrainerUI` composable, and the `playIntervalsLoop` audio playback function.
- `app/src/main/java/com/example/aurallearner/ui/theme/` — Material 3 theme (Color, Type, Theme).
- `app/src/main/assets/piano/` — MP3 piano samples named `piano_<MIDI_NUMBER>.mp3` (MIDI range 48–84), loaded into SoundPool at startup.

**Audio pipeline:**
- Piano notes: `SoundPool` (max 6 concurrent streams) loaded from assets at `onCreate`.
- Interval names: Android `TextToSpeech` (US English, 0.75× speech rate).
- Playback loop: raw `Thread` (no coroutines) with an `AtomicBoolean` stop signal.

## Stack

- Kotlin 2.0.21, AGP 8.9.1
- Min SDK 24 / Target SDK 35
- Jetpack Compose BOM 2024.09.00, Material 3
- JUnit 4 + Espresso for tests (only placeholder tests exist)
