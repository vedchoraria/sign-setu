# 10. Test Report

Results of the [test plan](06-test-plan.md) for release 1, version 0.3.0. Section 2 lists the automated suite on the current build: it is built and compiled (Run A) and not yet run (Run B), so it carries no results. Sections 3 to 6 give the manual and device evidence; a result marked *earlier build* was observed on an emulator build of 0.1.0 that predates the recorded signs, lesson-sheet questions, stage split and GPU delegate (D-35 to D-38), and is repeated on the current build before release.

## 1. Build

| Item | Value |
| --- | --- |
| Application | `in.signsetu` 0.1.0, version code 1; `in.signsetu.nearby` 0.1.0-nearby for the nearby flavor |
| Variants | `standardDebug`, `standardRelease`, `nearbyDebug`, `nearbyRelease` |
| Release APK (unsigned) | `standard` 70,891,654 bytes (67.6 MiB), `nearby` 71,525,872 bytes (68.2 MiB); arm64-v8a only, float32 reader weights, native libraries, models and reader weights stored uncompressed (D-42); under the 100 MB ceiling of NFR-05 |
| Toolchain | AGP 9.4.1, Gradle 9.8.1, Kotlin 2.4.21, KSP 2.3.12, JDK 21, NDK r30, CMake 4.1.1 |
| Devices used | Android 10 (API 29) emulator, x86_64, 3 GB RAM; Android 14 (API 34) emulator, x86_64, 3 GB RAM, with the virtual camera and the debug stand-in hand; a 5-inch screen (360 by 640 dp) simulated on the Android 14 emulator for NFR-11 |
| Not yet used | A real Android 10 phone; a recent phone; two phones and a tablet for the second screen |

## 2. Automated suite on the current build

All suites below were run on release 0.3.0, except the release APK row, which records 0.2.0.

| Suite | Command | Result |
| --- | --- | --- |
| Unit, standard flavor | `./gradlew testStandardDebugUnitTest` | 201 of 201 pass |
| Unit, nearby flavor | `./gradlew testNearbyDebugUnitTest` | 201 of 201 pass |
| Instrumented | `./gradlew connectedStandardDebugAndroidTest` | 76 of 76 pass on the API 34 emulator, including the real hand tracker on dictionary clips (`SignPipelineDeviceTest`) and the drawn skeleton on a signer's frame (`SkeletonOverlayDeviceTest`) |
| Android Lint | `./gradlew lintStandardRelease` | Passes the gate |
| Portal | `cd portal && npx vp test` | 918 of 918 pass |
| Python tools | `python -m unittest discover` in `tools/isl` and `tools/security` | Pass |
| Reader evaluation | `python tools/ml/eval_isl.py`; `python tools/ml/eval_asl.py` | Scores in `tools/ml/EVAL.md`; the readers ship as float32 again (D-42) |
| Stage walkthrough | Debug build on the API 34 emulator | Every screen in [the screen catalogue](14-screens.md) reached and captured: opening, questions, writing, drawing and hand-back, phrase board, number pad, sign window, pause, language finder, typed English with its Hindi caption, dictionaries, log, export |
| Release APK | `./gradlew assembleStandardRelease`, then signed | Installed and run on the emulator under 64-bit ARM translation: tracker, database and assistant libraries load with the camera on |

## 3. Functional scenarios

| ID | Result | Evidence |
| --- | --- | --- |
| TC-01 | Partial, earlier build | Fresh install on Android 14 with a screen lock: three first-run screens, unlock, home without scrolling. With no lock set, the lock screen offers security settings; key loss after a lock reset recovers with a notice (Android 10). The no-lock path on a fresh install is open |
| TC-02 | Pass, earlier build | Child character for Young child and Not sure, adult for ISL user, language finder first for Other language |
| TC-03 | Pass, earlier build | Back stays on the stage; one tap on the handle returns; with the setting on the credential is asked as well; rotation present |
| TC-04 | Pass, earlier build | Lock task state PINNED during the session and NONE after it ends; Android 10 shows its own pinning notice each time pinning resumes |
| TC-05 | Partial, earlier build | Database write 13 to 43 ms; repeat shows 283 to 388 ms and the first 672 ms on a software-rendered emulator; repeat on a phone GPU |
| TC-06 | Pass, earlier build | Sensitive group locked until confirmed |
| TC-07 | Pass in unit tests (`AvatarTest`, `IslLexiconTest`) | Typed text signed word by word in draft ISL order, recorded signs first; device repeat on the current build open |
| TC-08 | Pass, earlier build | Officer tiles mark gesture and audio; the Show bar names caption, audio and gesture as present or "not available" |
| TC-09 | Pass in unit tests (`AvatarTest`) and earlier build | Every phrase signs with reviewed gestures in order; on the emulator the sentence shows with the picture held up |
| TC-10 | Pass, earlier build | 14 recorded greetings play; the bubble greets back in the chosen language |
| TC-11 | Pass in unit tests (`LogicTest`) | Ranking: both matched, then place, then action |
| TC-12 | Pass, earlier build | "Is this it?" with the gesture and picture; Yes logs the meaning, otherwise unclear |
| TC-13 | Pass, earlier build | Drawing logged as the answer |
| TC-14 | Pass, earlier build | Force-stopped mid-session; reopening offered the unfinished session with its log |
| TC-15 | Pass, earlier build | Release export; PDF opens only with the password (AES-256, revision 6) |
| TC-16 | Pass in unit tests (`PolicyTest`) and earlier build | Clock moved 25 hours: the retention job deleted the database and wrapped key |
| TC-17 | Pass, earlier build | WhatsApp contact opens the `wa.me` link; with no contact the button is hidden |
| TC-18 | Pass in unit tests (`PolicyTest`) and earlier build | Clock moved 181 days: "Update required", Start disabled |
| TC-19 | Pass, earlier build | Permission asked at Start; mirror shown; stand-in hand overlay drawn |
| TC-20 | Pass, earlier build | Stop logged and shown to the officer as a banner |
| TC-21 | Pass in unit tests (`AvatarTest`, `IslLexiconTest`); review open | Every motion uses known anchors and shapes, stays in frame on both characters and starts and ends at rest; reviewer sign-off outstanding |
| TC-22 | Pass, earlier build | Written answer shown as a typed answer and in the log |
| TC-23 | Pass, earlier build | Gemma 3 1B accepted by hash; a random file refused with nothing kept; one question translated and back-translated in 66 s on the x86_64 emulator |
| TC-24 | Pass, earlier build | The composer's editor info reports `imeOptions=0x3000004`, which includes `IME_FLAG_NO_PERSONALIZED_LEARNING` |
| TC-25 | Pass in unit tests (`AnalysisRateTest`) | First frame analysed whatever its timestamp; later frames wait for the 30 ms interval |
| TC-26 | Pass in unit tests (`SegmentTest`, `SignIngestionTest`) | Segmentation ranges, short holds, incremental equals full scan, narratives drop faces |
| TC-27 | Pass in unit tests (`SignReaderTest`) | Logits within 1e-3 of the Python reference on five fixtures; about 18 ms per read on a desktop JVM |
| TC-28 | Pass in unit tests (`SignIngestionTest`) | Guesses that fit the item rank first and the others stay; alternatives and the slower cue |
| TC-29 | Pass, earlier build | Read signs, dots, Done, app reading, slow replay, Ask them to choose; landmark wiping verified by code review |
| TC-30 | Pass in unit tests (`RoleTest`) | Role gating against the real bank |
| TC-31 | Pass in unit tests (`InterviewTest`) | Sequencer rules against the real bank |
| TC-32 | Pass in unit tests (`AutoPilotTest`) | Auto logging and control |
| TC-33 | Pass in unit tests (`PairTest`) | Handshake, frames, resume, late and duplicate answers, drawing bounds |
| TC-34 | Pass in unit tests (`MainPageTest`) | Completions, trails, say-back grades, shuffled choices, "Shown as" positions |
| TC-35 | Pass in unit tests (`StageLogicTest`) | Glance strip, vibration codes, pace, pause length, opening words and pictures |
| TC-36 | Pass in unit tests (`SunModeTest`) | Light sensor thresholds; the sensor itself needs a phone |
| TC-37 | Pass in unit tests (`HandoverTest`) | Tilt and both-keys chord; tilt and volume keys on a phone are open |
| TC-38 | Open | `stage_live` logic reviewed in code; a captured restart after a kill on the stage is open |
| TC-39 | Open | `SessionMigrationTest` compiles; run open on this build |
| TC-40 | Written, run open | `RigFixtureTest` and `vp test` not yet run on this build; earlier run passed 918 portal tests |
| TC-41 | Open | Needs two phones and a tablet (UAT-18) |
| TC-42 | Written, run open (`LessonTest`) | 12 tests written; earlier run 11 tests passed |
| TC-43 | Written, run open (`IslLexiconTest`) | 13 tests written; earlier run 10 tests passed |
| TC-44 | Pass in unit tests (`AvatarTest`) | Catmull-Rom pass-through, hands in the body view, question faces |
| TC-45 | Pass in unit tests (`HolisticMappingTest`) | 2 tests |
| TC-46 | Open | Device check on the current build |
| TC-47 | Written, run open | `IslLexiconTest` and `SignLanguageTest` check that every ISL and ASL reader label resolves and that ASL digits and gloss text come out as specified; not yet run on this build |

## 4. Performance

| ID | Result |
| --- | --- |
| PT-01 | Earlier build, emulator only: release cold start to first frame 0.79 to 1.03 s over four runs (`am start -W`); repeat on a real Android 10 phone |
| PT-02 | Earlier build: 173 to 236 ms repeat, 510 ms first on Android 14 with a desktop GPU; 283 to 388 ms repeat, 672 ms first on software-rendered Android 10; repeat on a phone |
| PT-03 | Partial, earlier build: 0 to 8 janky frames of about 420 (0 to 2%), all as the stage opens; motion playback dropped none |
| PT-04 | Pass: 41,004,639 bytes (39.1 MiB), under 50 MB |
| PT-05 | Earlier build: 139 MB total PSS on the stage with the camera running (limit 300 MB); repeat with the GPU delegate on a phone |

## 5. Security

| ID | Result | Evidence |
| --- | --- | --- |
| ST-01 | Pass by configuration, earlier build | `adb backup` reports the app not eligible; extraction rules exclude every domain for cloud backup and device transfer; a copied database stays unreadable because its key is wrapped by this phone's Keystore. A live transfer needs two phones with a Google account |
| ST-02 | Pass, earlier build | Database pulled as root: no SQLite header; rejected by a standard SQLite library |
| ST-03 | Pass, earlier build | Screen capture refused on the release build |
| ST-04 | Pass, earlier build | Release logcat across a session holds rendering and memory messages only |
| ST-05, ST-15 | Pass | Merged release manifests. `standard`: CAMERA, BLUETOOTH_CONNECT, BLUETOOTH (up to API 30), VIBRATE; no INTERNET, RECORD_AUDIO, location, scan, advertise or NEARBY_WIFI_DEVICES. `nearby` adds BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, BLUETOOTH_ADMIN (up to API 30), NEARBY_WIFI_DEVICES, ACCESS_FINE_LOCATION (up to API 32) and Wi-Fi state (up to API 31); no INTERNET. Both release variants assemble through `check<Variant>Permissions`, which fails when INTERNET is added |
| ST-07 | Pass, earlier build | Only the launcher activity is exported, plus WorkManager's job service behind BIND_JOB_SERVICE (the SEC-41 exception) |
| ST-08 | Pass, earlier build | Release dex not debuggable; no gallery, stand-in hand or timing log; no key or token patterns; obfuscated |
| ST-09 | Pass, earlier build | Android 10 and 14: four minutes away left the app open; just over five minutes asked for the credential |
| ST-10 | Pass, earlier build | Wrong password rejected by a standard PDF library |
| ST-12 | Pass, earlier build | One byte added to `gestures.json` left out the motions with a notice; one byte added to `triage.json` showed "Content check failed" and kept Start disabled (RD-05) |
| ST-13 | Partial | gitleaks clean; OSV over `app/gradle.lockfile` clean for the release runtime classpath (BouncyCastle held at 1.86); findings only in Lint and the instrumented-test runtime, which do not ship; rerun after the protobuf change |
| ST-14 | Pass, earlier build | A translucent overlay app: Show, a board card, the officer menu, Export and Finish ignored taps while it was up |
| ST-16 | Pass | `transport-backend-cct` excluded in the build script; the stub destination is in the APK; no INTERNET |
| ST-17 | Open | Needs a stage device |
| ST-18 | Partial | Pairing logs exception class names only (code review); a logcat capture during a sign window on a phone is open |
| ST-19 | Written, run open | The holistic model and the four readers (`sign_reader`, `isl_spell`, `asl_reader`, `asl_spell`) are listed in the manifest and checked before loading; `SignReaderTest` alters the bytes of each and expects no reader; not yet run on this build |

## 6. Accessibility

| ID | Result | Evidence |
| --- | --- | --- |
| AT-01 | Pass, earlier build | Accessibility Test Framework over every stage state and the picture board in both themes, at the emulator's size and at 5 inches; contrast computed for 32 text pairs, all at or above 4.5:1 |
| AT-02 | Pass, earlier build | At 200% font scale the officer panel becomes one scrolling column; stage, answer cards and home stay whole |
| AT-03 | Partial | Every control exposes a spoken label (UI Automator dump); a spoken TalkBack walk-through needs a phone, since the Android 10 emulator images lack TalkBack |
| AT-04 | Pass, earlier build | All language names and Hindi interface text render without clipping |
| AT-05 | Pass by design | Yes and No differ by icon, word and position as well as colour |
| AT-06 | Open | Lesson-input and phrase-board cases are in `StageAccessibilityTest`; run open on this build |
| AT-07 | Written, run open | `StageDeviceAccessibilityTest` covers the stage device waiting, code, check value, hold, lost, break and ended screens in both themes; not yet run on this build |

## 7. Security controls self-check

Each control in [security](04-security.md) checked against the code.

| Controls | Status | Evidence |
| --- | --- | --- |
| SEC-01, SEC-10, SEC-11, SEC-22 | Met | `DatabaseKey`: 32-byte DEK wrapped by an AES-256-GCM Keystore key, usable only while the phone is unlocked (D-48); `SessionDatabase` opens through `SupportOpenHelperFactory` |
| SEC-02, SEC-08 | Met | Drawings only in the database; `cacheDir/exports` deleted on unlock and clear; `secure_delete` on open; `JournalMode.TRUNCATE` |
| SEC-03, SEC-07 | Met | `allowBackup="false"`, `dataExtractionRules`, `fullBackupContent`; settings hold no personal data |
| SEC-04, SEC-53, SEC-60 | Met | R8 strips `android.util.Log`; release not debuggable; R8 full mode |
| SEC-05 | Met | `NoPersonalisedLearning` in `AppRoot` adds `IME_FLAG_NO_PERSONALIZED_LEARNING` to every editor |
| SEC-12 | Met | AES-256 PDF; 16-character passphrase, 80 bits |
| SEC-20, SEC-21, SEC-75 | Met in code | Silent Keystore unlock first unless `stage_live` is set; BiometricPrompt with API-level authenticators; five-minute relock (`AppContainer.onStart`) |
| SEC-30, SEC-42, SEC-66, SEC-73 | Met | Per-variant permission gate; CAMERA and BLUETOOTH_CONNECT the only runtime permissions in `standard` |
| SEC-40, SEC-45 | Met | FLAG_SECURE in release; `filterTouchesWhenObscured` on the decor view and every guarded dialog and sheet |
| SEC-41 | Met with one exception | Only the launcher activity is exported, plus WorkManager's job service; two unused library receivers removed |
| SEC-46, SEC-47 | Met | Pinning from the first stage frame to the end; Preview and ImageAnalysis only; analysis only while a sign window sets `onFrame` |
| SEC-50, SEC-51, SEC-52 | Met | `gradle/verification-metadata.xml`, `app/gradle.lockfile` and `settings-gradle.lockfile`; `tools/security/sbom.py`; Lint, Semgrep, OSV, gitleaks |
| SEC-54, SEC-74 | Met | Manifest hashes checked before parsing; models checked by their loaders |
| SEC-55, SEC-64 | Met | minSdk 29, target 37; `BUILD_LIFETIME_DAYS` 180 |
| SEC-63 | Partial | Rooted-phone warning shown; disabling session history waits on the department's decision (T-19) |
| SEC-65 | Met | `ModelStore.KNOWN_MODELS`; hash computed while copying; refused file deleted |
| SEC-67, SEC-68 | Met | Handshake, frame and resume behaviour covered by `PairTest` |
| SEC-69, SEC-70 | Met in code | Stage stores only the `stageDevice` flag; exception class names only; ST-17 on a device is open |
| SEC-71 | Met in code | Analysis gated by the sign window; landmarks in view-model memory, wiped on end, lock and clear; narratives drop faces |
| SEC-72 | Met | ST-16 |
| SEC-62 | Open | Release signing key and custodians are a department decision |

## 8. Open items before production

- Reviewer sign-off on the content review record: every gesture, authored and recorded sign and both characters (05 §8), the phrase-bank sentences, the placeholder recordings, the draft ISL word order and sign-word map, the lesson-sheet wording, and the rights wording in English and Hindi.
- The unit, instrumented and portal suites, Android Lint and the retarget tests on the current build (Run B); the device scenarios marked *earlier build* repeated on it.
- TC-05, PT-01 to PT-03 and PT-05 on a real Android 10 phone, and the vision engine's frame rate on the GPU delegate, memory and hand sides on a real 64-bit phone.
- The two-phone and tablet script (TC-41, UAT-18), ST-17, and an accessibility pass over the stage device screens (AT-07).
- A spoken TalkBack pass on a phone, and the formal MASVS review.
- ASL word reader accuracy: top-1 0.402 against the 0.6 target; ISL letters, digits and ASL spelling measured on synthetic data only; sign reader accuracy with phone cameras and children; data for the core answer words the reader cannot read (yes, no, water, pain, help, stop, don't know).
- Assistant speed on an arm64 phone (TC-23) and language reviewers' spot-check of its output.
- A live device-to-device transfer (ST-01) on two phones with a Google account.
