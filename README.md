# Sign Setu

An offline Android app that helps a police officer communicate with a person who cannot communicate in words: deaf, non-verbal, traumatised or speaking another language. A code-drawn character signs and gestures, pictures and captions carry the question, typed text is played in draft Indian Sign Language order, and signed answers are read on the phone inside explicit sign windows. The app is a communication aid, not an interpreter, and never records statements.

- Android 10 (API 29) and later; Kotlin and Jetpack Compose; one `app` module.
- Works fully offline. A build with a department gateway (`-PgatewayUrl=https://...`) also uses it while online (D-47); without one, nothing ever connects. No RECORD_AUDIO or location permission; the build fails if one appears.
- Sessions live in an encrypted SQLCipher database under a Keystore-wrapped key and are deleted on end or after the retention period.

## Build and test

```
./gradlew assembleStandardDebug          # debug APK
./gradlew assembleStandardRelease        # release APK, R8, permission gate
./gradlew testStandardDebugUnitTest      # unit tests (also testNearbyDebugUnitTest)
./gradlew connectedStandardDebugAndroidTest   # instrumented tests on an emulator
cd portal && npm install && npx vp test  # reviewer portal tests, including rig parity
python tools/content/build_content.py --manifest-only   # after any content change
cd backend && pip install -e ".[test]" && pytest         # department gateway tests
```

The build needs JDK 21, Android SDK 37, NDK `30.0.16248370` and CMake 4.1.1. Dependencies are pinned in `gradle/libs.versions.toml`, locked in `app/gradle.lockfile` and verified by `gradle/verification-metadata.xml`.

## Repository

| Path | Contents |
| --- | --- |
| `app/` | The Android app (`app.signsetu`; application ID `in.signsetu`), flavors `standard` and `nearby` |
| `app/src/main/assets/content/` | Content bundle: questions, cards, motions, vocabulary, audio, models, hash manifest |
| `backend/` | Department gateway (FastAPI, SQLAlchemy): device enrolment, model manifest, assistant proxy, consented feedback labels; optional, the app works without it |
| `portal/` | Static reviewer portal for content sign-off (Vite+, React, TypeScript); not shipped |
| `tools/` | Content, ISL retargeting, sign-reader training and SBOM scripts (Python) |
| `content/` | Source symbols for the picture cards |
| `docs/` | Requirements, architecture, security, decisions, test plan and report ([index](docs/README.md)) |
