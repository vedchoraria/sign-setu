# 02. Architecture

## 1. Overview

Sign Setu is a single Android application that runs entirely on the officer's phone. It works without a server; when the build has a department gateway URL and the phone is online it may also call that one host. All content (questions, picture cards, character motions, audio, the vision and sign-reader models) ships inside the signed APK; all session data stays in an encrypted database on the device until the officer exports or clears it. An optional second phone or tablet can show the subject's stage over Bluetooth (§11); it stores nothing. The reviewer portal (§12) is a separate static web page for content sign-off and is not shipped in the app.

This document covers how the application is built.

## 2. Principles

1. **Offline first.** Nothing depends on a network. Online features go through the project gateway only, are off until the officer switches them on, and fall back to the on-device path on any failure (§13).
2. **Content is data.** Questions, cards, lexicon entries and translations live in versioned asset files reviewed under the content policy, not in code.
3. **Write before show.** Every event is persisted before the interface reflects it.
4. **Small and boring.** One app module, platform libraries first, no frameworks the audit has to justify.

## 3. Technology

| Layer | Choice | Reason |
| --- | --- | --- |
| Language, UI | Kotlin, Jetpack Compose, Material 3 | Native, smallest build, direct access to Keystore and platform security APIs |
| SDK levels | minSdk 29; compileSdk and targetSdk 37 | Android 10 floor |
| Media | Media3 ExoPlayer 1.11.1 | Recorded speech at normal and reduced speed |
| Database | Room 2.8.5 (schema version 3) with SQLCipher for Android 4.19.1 | Encrypted at rest |
| Settings | Jetpack DataStore | No personal data |
| Background work | WorkManager | Retention purge |
| App lock | androidx.biometric BiometricPrompt, KeyguardManager | Device credential or strong biometric |
| PDF export | Android PdfDocument to compose, PdfBox-Android to encrypt (AES-256) | Readable in any PDF reader |
| Characters | Compose Canvas, 2D rig, keyframes in `gestures.json`, `signs.json` and `isl_signs.json` | No video files; one motion file plays on both characters |
| Camera mirror | CameraX 1.6.2: Preview and ImageAnalysis on one 16:9 portrait frame (analysis 640 by 360, rotated upright); frames reach the tracker only inside a sign window | Nothing captured or stored |
| Vision engine | MediaPipe Tasks Vision 1.1.1: the Holistic landmarker in video mode, GPU delegate first with CPU fallback, model in the APK; `protobuf-javalite` 4.36.2 runtime | Hands, face and pose on the phone; 64-bit only |
| Sign reader | `LandmarkSignReader`: a 262-word isolated-sign classifier in plain Kotlin, weights in the APK | No inference library to ship or audit |
| Second screen | Bluetooth RFCOMM to bonded devices (`standard`); Google Nearby Connections in the optional `nearby` flavor | No network, scanning or location in the reference build |
| On-device assistant | llama.cpp v0.5.0 built with the NDK and CMake into `libassistant.so` (arm64-v8a; x86_64 for the emulator); an optional GGUF model file imported in Settings | Offline translation with a back-translation; no network |
| Type | Baloo 2 (SIL OFL), bundled, for headings and subject captions in Latin and Devanagari | Friendly, legible at arm's length; other scripts use the system Noto fonts |
| Build | Gradle wrapper, version catalog, R8 full mode, dependency verification; llama.cpp v0.5.0 vendored under `app/src/main/cpp/third_party` | Size, startup, supply chain |

Not used: networking libraries, dependency-injection frameworks, Firebase, Google Play services (except Nearby Connections in the `nearby` flavor), analytics or crash SDKs, WebView, microphone APIs, and camera capture or recording (the camera is a mirror only). Exact versions are pinned in the version catalog.

## 4. Packages

One Gradle module under `app.signsetu`, organised by feature.

| Package | Responsibility | DFD |
| --- | --- | --- |
| (root) | `MainActivity` (FLAG_SECURE, overlay touch filter, BiometricPrompt, screen pinning); `AppContainer` wiring, app lock and relock | 1.0 |
| `ui` | Navigation, lock screen, session route (officer panel or stage), app-wide no-learning keyboard | 1.0, 2.0, 3.0 |
| `ui.session` | Officer panel in two pages, Transcribe and More (conversation, completions, question runs, answer trail and grades, composer, tools, `IslDictionarySheet`); stage (character, caption, mirror, rails, drawing, writing, ground rules, pause layer, glance strip, sign panels, `LessonInputs` answer inputs and phrase board); handover (tilt and volume keys, `StageGuard`); auto mode (`Interview` sequencer, `AutoPilot`, `AutoPanel`); the record (`PromptRecord`, `EvidenceGrade`, `RecordText`, `PersonalDetails`); sheets; session view model | 2.0, 3.0 |
| `ui.pair` | `StageDevice` (the stage device's screens), `SecondScreenSheet` (officer pairing), `PairedBar`, `LinkText` | 2.0, 3.0 |
| `ui.avatar` | `Rig.kt` (anchors, two-bone solver, timeline, Catmull-Rom for recorded keys), `MeasuredShape.kt` (`c:` hand shapes), `Avatar.kt` (drawing, framing to the hips), `MiniFace` | 3.1 |
| `ui.start` | Home (role and profile, language, unfinished session) and first run | 2.0 |
| `ui.settings`, `ui.theme`, `ui.components` | Settings; officer, stage and sun-mode colour schemes, one officer card and tile set, shapes, type and spacing tokens, the ambient-light switch; shared cards, buttons, speech player, overlay filter | 2.0 |
| `data.content` | `Content.kt` loads and verifies the bundle; `IslLexicon.kt` the one sign lookup and the dictionary; `TextSigning.kt` plans typed text; `Lesson.kt` answer types, validation and read-back text; `Completion.kt` word completions | 3.1, D2 |
| `data.session` | Encrypted Room database, wrapped database key, event log, retention worker | 4.0, D1, D4 |
| `data.settings` | DataStore settings and the officer's app language | D3 |
| `export` | Password-protected PDF report | 5.0 |
| `camera` | Camera mirror; `LandmarkTracker` interface and `LandmarkFrame`; `HolisticTracker` (MediaPipe, GPU then CPU), created by `LandmarkTrackers` per build type (release: Holistic; debug: Holistic, or a stand-in hand for tests) | 3.5 |
| `sign` | `SignCapture` and `SignWindow` (windows and pause segmentation); `SignReader`, `LandmarkSignReader` and `SignReaders` (the bundled reader); `SignChoices` (context ranking, alternatives for the subject, the slower cue); `SkeletonPlayer` (slow replay) | 3.5, 3.6 |
| `pair` | `PairCrypto` (handshake, keys, frames), `StageWire` (messages and the stage view), `StageTransport` and `BluetoothTransport`, `StageLink` (link state machine), `StageBridge` (officer-side bridge to the view model), `Drawings` (bounded re-encoding) | 7.0 |
| Flavor source sets | `src/standard` and `src/nearby`, each with `pair/StageTransports`; `NearbyStageTransport` only in `src/nearby` | 7.0 |
| `assistant` | Model import with hash check, llama.cpp bridge, translation and back-translation | 6.0, D5 |

## 5. Content bundle

```
assets/content/
 manifest.json {"version": 1, "files": {path: SHA-256}}; its own SHA-256 is the content version
 triage.json 15 groups (sensitive, rights, roles[], child?) and 75 questions: id, group, phase, kind, after?, text{lang}, cards[], lexicon?, gesture?, answer?, field?, roles[], hiddenFor[], signed
 cards.json 12 categories and 117 cards: id, category, label{lang}
 lexicon.json 33 gesture-lookup entries: id, concept, card, locations[], actions[], label{lang}, how{lang}
 gestures.json 42 authored gestures, keyframes against named contact points
 signs.json 26 letters, 10 digits and 71 draft word signs, generated by tools/content/build_signs.py
 asl_signs.json 250 signs retargeted from PopSign signers, 26 one-hand letters and 10 one-hand digits, by tools/isl/retarget_asl.py; draft
 asl_words.json ASL reader labels to vocabulary words (draft)
 isl_signs.json 262 signs retargeted from INCLUDE signers by tools/isl/retarget.py, in 16 categories
 words.json 123 vocabulary entries, 850 written forms, to sign, gesture and picture
 sign_words.json 49 sign-reader labels to vocabulary words (draft)
 languages.json 17 languages: code, name in its own script, English name, greeting
 subject_ui.json 35 words the stage shows the subject, per language
 phrases.json 2 sentence patterns with 10 slots: signs, and per slot the picture, its sign and the full sentence
 places.json 36 states and union territories; Chhattisgarh with its 33 districts
 models/ holistic_landmarker.task (13.7 MB), sign_reader.bin (1.4 MB) and sign_reader.json; isl_spell, asl_reader (format 1) and asl_spell (format 2, CTC), each a .bin with a .json
 cards/<id>.webp 117 picture cards, 320 px
 audio/<lang>/<id>.ogg question and phrase audio, Opus, mono (37 English, 37 Hindi)
 audio/greet/<lang>.ogg 14 language finder greetings
```

Each ASL asset is optional and degrades alone:

| File | If missing, or failing its hash or loader |
| --- | --- |
| `asl_signs.json` | ASL text falls back to everyday gestures and pictures, spelling is not offered, and the ASL dictionary is empty |
| `asl_words.json` | ASL reader labels are not paired to vocabulary words, so context ranking and sign-back by concept do not apply |
| `models/isl_spell` | ISL reading uses the 262-word reader alone |
| `models/asl_reader` | No ASL word reader; the officer enters a word per sign |
| `models/asl_spell` | No ASL fingerspelling reader; word reading is unaffected |

The characters play keyframes only; no video clips ship.

- The app checks every file except `models/` against the manifest each time it loads the content, and never parses or shows a file that fails. A failed picture, recording or motion file (`gestures.json`, `signs.json`, `isl_signs.json`) is left out and the rest still works; any other failed JSON file stops new sessions, because the subject could otherwise be shown wrong words. Either way the officer sees a notice on the home screen. Each model is checked by its own loader on first use.
- `tools/content/build_content.py --manifest-only` rewrites the manifest after normalising JSON to LF line endings, so the hashes hold on every checkout (`.gitattributes` sets `eol=lf`).
- All approved languages ship in the APK. Recorded audio averages about 10 KB per item.
- Interface strings use Android resources; subject-facing content uses the bundle, so content changes do not touch code.

## 6. State and data model

One `SessionViewModel` holds `SessionUi` as a `StateFlow`. Every user action becomes an event written to the database first; the state updates from the write. What is on the stage (`onStage`, with its logged row `itemId`) is kept apart from the officer's draft (`pending`), so a second screen never shows a draft. Landmark frames of signed answers are held only in view-model memory, keyed by event.

```
Session id, startedAt, endedAt?, profile, subjectLang, role (NOT_KNOWN | VICTIM | ACCUSED)
Event id, sessionId, ts, type, source, lang?, text?, cardId?, lexiconId?, drawingId?,
 itemId?, reason?, shownId?, via?
 type: QUESTION_SHOWN | ANSWER_CARD | ANSWER_DRAWING | ANSWER_GESTURE | ANSWER_UNCLEAR | ANSWER_TEXT |
 ANSWER_SIGNED | LANGUAGE_CHOSEN | NOTE | SYSTEM | SUGGESTED | APPROVED | SKIPPED |
 AUTO_ADVANCED | LINK | PROMPT
 source: PRESET | TEXT | PHRASE | BOARD | SHOW_BACK | SUBJECT | OFFICER | RULE | MACHINE
Drawing id, sessionId, png
```

- `itemId` names the bank item of a shown or suggested row, and the question of a lesson-sheet answer; `reason` holds the auto-mode reason, `rights_shown`, `caption_missing` or `opening` on a shown row, and `given:<METHOD>:<0|1>` on a lesson-sheet answer (method WRITTEN, PAD, PICTURE, PICKER, SIGN or BOARD; 1 when the subject confirmed the read-back); `shownId` links every answer to the QUESTION_SHOWN it answers; `via` is `stage` or `stage_late` for answers from a second screen.
- PROMPT rows hold the "Shown as" record (caption, motions, pictures in order, content version). A say-back carries `lexiconId` `ref:<answer id>`. SYSTEM rows use machine keys (for example `run_ended:k/n`, `break_given`, `jj_notice`, `juvenile`, `juvenile_off`, `read_signs`, `tell_me`, `mode_auto`), shown in the officer's language.
- Database version 1 to 2 adds the session role; 2 to 3 adds `itemId`, `reason`, `shownId` and `via`. A downgrade drops the tables, because sessions live a day at most.

No table has a column for a name, address, phone number or photograph of the subject. Lesson-sheet answers can hold such details as answer text; they live in the same encrypted rows, under the same retention, and reach only the encrypted report.

## 7. Storage and keys

- A random 256-bit database key (DEK) is generated on first use and stored wrapped by an AES key-encryption key (KEK) in Android Keystore, hardware-backed where available, non-exportable and usable only while the phone is unlocked.
- Unlocking the app (1.0) authorises the KEK; the unwrapped DEK lives in memory only while the app is unlocked.
- Drawings are stored inside the encrypted database, never on shared storage.
- Purge deletes the session rows and runs `VACUUM`.

API-level details for authentication are in [security](04-security.md).

## 8. Export and hand-off

**Session report.** The report is composed with Android `PdfDocument` on A4 pages: header (session start, profile, languages, role when set), the "Personal details given" table, the event log with grades, choice positions and trails, notes, drawings as images, and the readings section; the not-a-statement notice heads and ends every page. It is then encrypted with PdfBox-Android using AES-256 (`StandardProtectionPolicy`, random owner password). The passphrase is 16 characters from the 32-symbol alphabet `0123456789ABCDEFGHJKMNPQRSTVWXYZ` (80 bits, `SecureRandom`), shown once as four groups of four, and shared by the officer separately from the file. The file is written to app-private cache and shared through `FileProvider` with a temporary read grant, then deleted.

**Interpreter hand-off.** The configured contact (D3) is opened in the configured messaging app: for WhatsApp, `https://wa.me/<number>` opens the chat, and the officer starts the video call; for Sandes, the app is launched by package name and the contact is shown on screen for the officer to select [confirm Sandes deep link]. No session data is passed.

## 9. Build and release

- The `applicationId` is `in.signsetu`. The code namespace is `app.signsetu`, because `in` is a Kotlin keyword. The department confirms the identifier before the first signed release, because it cannot change afterwards.
- `BuildConfig.BUILD_EPOCH_DAY` is set at build time for the build-expiry check.
- Two product flavors on the `transport` dimension: `standard` (default, the reference build) and `nearby` (application ID suffix `.nearby`). Tasks are named per variant, for example `assembleStandardRelease`.
- Version `0.1.0`, version code 1 (`app/build.gradle.kts`); the nearby flavor appends `-nearby` to the name.
- Release builds: R8 full mode with resource shrinking, `debuggable=false`, `android.util.Log` calls stripped (`proguard-rules.pro`). The release ABI is arm64-v8a only; 32-bit phones cannot install the app. Nothing is compressed for size: native libraries, the holistic model and the reader weights are stored uncompressed in the APK, and reader weights ship at full float32 precision. Debug builds add x86_64 for the emulator. No app-specific Baseline Profile is generated; it is the next step if cold start on the oldest phone misses its 2 s target.
- Release APKs are built per ABI set (`./gradlew assembleStandardRelease` universal; `-Pabi=arm64-v8a` phones only) and signed offline with the department key; CI runs unit tests, lint and the permission check on every push.

## 10. Performance

| Target | Approach |
| --- | --- |
| Cold start under 2 s | No work on startup beyond unlocking; content manifest checked once per install |
| Preset under 300 ms | The item is logged, then drawn; the camera binds and pinning starts only after the stage's first frame; card images decoded lazily into a 48-entry cache; WebP, OGG and the `.task` model stored uncompressed in the APK |
| Smooth motion | The character's clock is read only in the draw phase, so animation redraws without recomposition; idle breathing redraws at 20 fps and stops inside a sign window |
| APK under 100 MB | WebP cards, Opus audio; the vision model (13.7 MB) and MediaPipe's 64-bit native code are the main cost; 0.5.0 signed release APKs: `sign-setu-0.5.0-arm64-v8a.apk` 82,749,403 bytes (78.9 MiB, phones; `-Pabi=arm64-v8a`) and `sign-setu-0.5.0-universal.apk` 109,242,896 bytes (104.2 MiB, arm64-v8a and x86_64). The phone APK is under the 100 MB target; the universal APK, which adds x86_64 for emulators and tablets, does not |
| Memory under 300 MB | Analysis only inside a sign window; the vision model mapped, not copied; a narrative keeps no face points (about 20 MB for 3 minutes); the assistant and the landmark graph are freed on lock, on memory pressure, and (the assistant) while the stage shows |

## 11. Second screen

`AppContainer.link` is a `StateFlow<StageLink?>`: at most one link, this phone as officer or this device as the stage, held in memory only. All link logic runs on one single-threaded scope; blocking reads and writes go to the IO dispatcher. `AppRoot` puts the stage-device branch before `LockScreen`, so a stage device never opens the session database or the navigation.

```
Officer phone Stage device
 Hello1 (identity, commitment) ->
 <- Hello2 (identity, public key, nonce)
 Reveal (public key, nonce) ->
 [both derive direction keys, code, check number, resume key]
 shows the 6-digit code
 officer types the code, sees the check number
 Confirm -> shows the same check number
 <- Confirm (Match tapped)
 Show (rev, itemId, view) ->
 <- Displayed (itemId)
 <- Answer (itemId, n, body)
 Ack (itemId, n) after commit ->
 Ping every 10 s <-> Ping; lost after 30 s; resume within 120 s
 Bye (end, fallback, exit) -> hand-back screen, pinned
```

The officer view model logs each item before `Show` and each answer before `Ack`. Answers are de-duplicated on (item, n) per link. If the link fails, the officer can reconnect or move the stage back to this phone ("Use this phone"), which logs `LINK fallback`.

## 12. Reviewer portal

`portal/` is a static site built with Vite+ (`vp`), React 19.3 and TypeScript 7.0. It imports the app's content files at build time, draws motions with a TypeScript port of the rig (`src/rig.ts`, checked against `Rig.kt` through `src/rig-fixtures.json`), keeps verdicts in the browser's local storage and exports them as JSON, which is kept as the review record. It has no backend and makes no network calls.

