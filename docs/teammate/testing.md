# Testing

Plans and results: `docs/06-test-plan.md`, `docs/10-test-report.md`.

## 1. Inventory

| Type | Location | Runner | Files |
| --- | --- | --- | --- |
| JVM unit | `app/src/test/java/app/signsetu/` | JUnit 4, kotlinx-coroutines-test; `unitTests.isReturnDefaultValues = true` | AslDamageTest, AslSpellTest, AutoPilotTest, AvatarTest, camera/AnalysisRateTest, camera/HolisticMappingTest, camera/OverlayMappingTest, HandoverTest, InterviewTest, IslLexiconTest, LessonTest, LogicTest, MainPageTest, PairTest, PolicyTest, ReadBackTest, RigFixtureTest, RoleTest, SegmentTest, SignIngestionTest, SignLanguageTest, SignReaderTest, SpellGateTest, StageLogicTest, SunModeTest |
| JVM fixture | `app/src/test/resources/*_fixture.json` | | Python-to-Kotlin logits parity for each reader, written by `tools/ml/export.py` and `tools/ml/spell_ctc.py` |
| Instrumented | `app/src/androidTest/java/app/signsetu/` | AndroidJUnitRunner, Compose UI test, Accessibility Test Framework | OverlayTouchTest, SessionMigrationTest, StageAccessibilityTest, StageDeviceAccessibilityTest |
| Portal | `portal/src/*.test.ts` | Vitest through vite-plus (`vp test`) | flags.test.ts, missing.test.ts, rig.test.ts (`rig-fixtures.json` shared with RigFixtureTest) |
| Python | `tools/isl/test_retarget.py`, `tools/content/test_review_record.py` | Plain `python` | Retarget geometry; content review record |
| Evaluation | `tools/ml/eval_isl.py`, `tools/ml/eval_asl.py` | `python` in the ML environment | Top-1 and top-3 per reader, fingerspelling error rate, ISL sentence demo |
| CI | `.github/workflows/ci.yml` | GitHub Actions | Unit tests, lint, release and flavor assembly, portal check, test and build, Python tool tests, content record check, Semgrep, OSV, gitleaks, SBOM, emulator job for the AT-07 classes |

## 2. Commands

| Purpose | Command |
| --- | --- |
| JVM unit tests | `./gradlew testStandardDebugUnitTest` |
| One test class | `./gradlew testStandardDebugUnitTest --tests app.signsetu.SignReaderTest` |
| Instrumented (device or emulator, Android 14+) | `./gradlew connectedStandardDebugAndroidTest` |
| Lint | `./gradlew lintStandardDebug` |
| Nearby flavor | `./gradlew testNearbyDebugUnitTest lintNearbyDebug` |
| Permission gate only | `./gradlew checkStandardReleasePermissions checkNearbyReleasePermissions` |
| Portal | `cd portal && npx vp check && npx vp test run` |
| Python | `C:/Users/Yash/dev/venvs/signsetu-ml/Scripts/python tools/isl/test_retarget.py`; `python tools/content/test_review_record.py` |
| Dependency scan | `osv-scanner scan source --lockfile app/gradle.lockfile` |
| Reader evaluation | `python tools/ml/eval_isl.py`; `python tools/ml/eval_asl.py` (ML environment) |

## 3. Device test procedure

| Step | Action |
| --- | --- |
| 1 | Set a secure screen lock; the app refuses to store sessions without one (SEC-20) |
| 2 | `./gradlew installStandardDebug` (the permission gate runs first) |
| 3 | `./gradlew connectedStandardDebugAndroidTest` |
| 4 | Without a model or camera: `adb shell run-as in.signsetu touch files/demo_hands` swaps in the waving stand-in tracker (debug builds only) |
| 5 | Camera path: start a session, open a sign window, check overlay, capture and reader guesses; on a weak GPU confirm the CPU fallback keeps tracking |
| 6 | Second screen: bond two phones in system settings, pair, check code and check number, drop the link and resume within 120 s |
| 7 | Security spot checks from `docs/06-test-plan.md` section 5, for example ST-01 (`adb backup` holds no app data) and ST-17 (`run-as ls -R` on the stage shows only the settings store) |
| 8 | Accessibility: TalkBack pass over the stage states (AT-07) |
| 9 | Record results in `docs/10-test-report.md` against the device list in `docs/06-test-plan.md` section 8 |

## 4. Run B acceptance checks

Run A built the items below and compiled them; none of the checks has been run. Run B performs each check once and records the result in `docs/10-test-report.md`.

| Item | Check | Pass condition |
| --- | --- | --- |
| T1 Back, Yes, No | Walk the matrix in [back-yes-no.md](back-yes-no.md) on a phone and on a paired stage device: every officer sheet, every stage state S1 to S25 | Every cell behaves as written; Back never loses typed input; Read-back No reopens the input; Yes logs Confirmed |
| T2 Skeleton | Record a clip with the front camera and compare the drawn wrist with the true wrist, in portrait and landscape rotation | Wrist overlay error under 3 % of frame width (`OverlayMappingTest` covers the mapping on the JVM); left and right hands labelled on the subject's own sides; "Sign slower" appears only after a failed read |
| T2 Tracker | Start a sign window on a phone with a GPU and on one without; read the debug frame-rate log | GPU delegate opens first, CPU fallback keeps tracking; frames are skipped while busy; no frame-rate log in a release build |
| T3 ISL transcription | `python tools/ml/eval_isl.py` | Prints top-1 and top-3 for words, letters and digits; the sentence demo passes; then a signed sentence on a phone gives top three per segment, a gloss sequence and English and Hindi text the subject confirms word by word |
| T3 Tell me | Sign an account of about a minute | A timed gloss transcript is produced and confirmed word by word |
| T4 ASL | `python tools/ml/eval_asl.py`; `SignLanguageTest`, `AslSpellTest`, `AslDamageTest`, `SpellGateTest` | Prints top-1, top-3 and fingerspelling error rate; the dictionary holds at least 250 signs; ASL word top-1 is currently 0.402 against the 0.6 target, so this check is expected to fail until better landmarks are trained on |
| T4 Language setting | Change the session sign language between ISL and ASL on a phone | Reader, dictionary, alphabet, numbers and typed-text signing follow the setting; each change is logged; a damaged `asl_*` file turns off ASL only |
| T5 Sign back | `IslLexiconTest` label check, in both languages | Every ISL and ASL reader label resolves through `signBack` to a motion of its own language and is in that language's dictionary |
| Model integrity (ST-19) | `SignReaderTest` altered-bytes cases for `sign_reader`, `isl_spell`, `asl_reader` and `asl_spell` | One changed byte disables that reader and leaves a plain mirror or the other language intact |
| AT-07 | `StageDeviceAccessibilityTest` on an emulator or phone, light and dark | Zero accessibility findings on the waiting, code, check value, hold, lost, break and ended screens |
| K1 Glance strip | `StageLogicTest` | Personal-detail answers are never shown on the glance strip |
| K6 CI | Push to a branch with CI enabled | Every job passes: tools job installs the packages the scripts import; Gradle wrapper is executable |

## 5. Gaps

| Gap | Note |
| --- | --- |
| Few instrumented tests | Four classes; no automated end-to-end session, export or unlock flow |
| No camera or MediaPipe test on a device | `HolisticMappingTest` covers mapping only; the tracker is checked by hand |
| Reader accuracy in the field | Only the INCLUDE and PopSign test splits; ISL letters and digits and ASL spelling are scored on synthetic data; no officer-phone or child-signer data |
| Pairing over real Bluetooth | `PairTest` covers crypto and framing on the JVM; the radio path is manual |
| Nearby flavor | No dedicated tests |
| Python tools | CI runs the retarget and review-record tests only; evaluations run by hand |
| Native assistant (`app/src/main/cpp`) | No unit tests |
| Security tests ST-01 to ST-17 | Manual, per release |
