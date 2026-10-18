# 06. Test Plan

## 1. Approach

| Level | What | Tools | When |
| --- | --- | --- | --- |
| Unit | Session logic, retention, content resolution, lexicon search, lesson inputs, build expiry, segmentation, sign reader, auto sequencer, pairing protocol, handover, record and grades, rig | JUnit 4, kotlinx-coroutines-test, in-process transport fake; run for both flavors (`testStandardDebugUnitTest`, `testNearbyDebugUnitTest`) | Every change |
| Portal | Rig parity with `Rig.kt`, review flags, missing captions | `vp test` (Vitest through Vite+) | Every change to the rig or portal |
| Tools | Retarget maths | `python tools/isl/test_retarget.py` | Every change to the tools |
| Instrumented | Stage accessibility, overlay filtering, database migrations | `connectedStandardDebugAndroidTest` on an emulator | Every change |
| Device | Release build on real phones | Scripts below, Macrobenchmark | Gate 1 |
| Security | MASVS L2 checks, static analysis, dependency and secret scans | Lint, Semgrep, OSV-Scanner, gitleaks, MobSF static | Every change (static); gate 1 (full) |
| Accessibility | IS 17802 / WCAG 2.1 AA on the officer panel | Accessibility Test Framework in instrumented tests (the Accessibility Scanner engine), TalkBack | Every change; TalkBack at gate 1 |
| Content | Translations, motions, audio | Reviewers per the content policy, through the reviewer portal | Gate 1 |
| Acceptance | Department testers | Scripted walk-throughs on department phones | Gate 1 |

Logic with branches (retention, show-back, build expiry, export) is written test-first: the failing test, then the code.

## 2. Functional scenarios

| ID | Scenario | Requirements |
| --- | --- | --- |
| TC-01 | Fresh install: first run, no device lock set; app refuses to store sessions until a lock exists | FR-50, SEC-20 |
| TC-02 | Choose each profile; Show state content matches §3 of the UX document | FR-60, FR-11 |
| TC-03 | Show and Officer states; rotate 180 degrees; Back on the stage stays; a tap on the handle returns; with the setting on, return also asks for the credential | FR-02, FR-03, FR-61 |
| TC-04 | App pinning on: subject cannot leave the app; pinning ends with the session | FR-61, SEC-46 |
| TC-05 | Every triage question shows within 300 ms with its cards, caption and profile content | FR-11, NFR-02 |
| TC-06 | Sensitive questions need a second deliberate tap | FR-12 |
| TC-07 | Typed text from the composer: Send shows it at once; each word plays as a sign, gesture or fingerspelling per the toggles, lit in the caption, with its picture or letter beside the character | FR-13, D-22 |
| TC-08 | Missing clip or audio: picture and caption only; officer sees "not available" | FR-17 |
| TC-09 | Phrase bank sentence with slot plays as one sequence | FR-62 |
| TC-10 | Language finder: each tile plays its greeting; choice sets subject language | FR-63 |
| TC-11 | Gesture lookup returns the expected entries for scripted location and action pairs | FR-65 |
| TC-12 | Show-back: Yes logs the meaning; No and no answer log "unclear" | FR-66 |
| TC-13 | Drawing saved to notes and present in the log | FR-24 |
| TC-14 | Kill the app mid-session; reopen; session restored exactly | FR-34 |
| TC-15 | Export: passphrase shown once; PDF opens in a standard reader only with it | FR-32, SEC-12 |
| TC-16 | Retention: move the clock past the period; session purged at next open and by the worker | FR-33, SEC-08 |
| TC-17 | Interpreter: contact configured opens the messaging app; none configured hides the button | FR-64 |
| TC-18 | Build expiry: clock past 180 days from build; new session refused, export of existing allowed | FR-67, SEC-64 |
| TC-19 | Camera mirror: permission asked of the officer at start; denied or off shows a placeholder; no files appear in app storage after a session | FR-69, SEC-47 |
| TC-20 | "I don't know" and "Stop" are logged; Stop shows the officer's banner until acknowledged or a new item is shown | FR-70 |
| TC-21 | Every gesture plays on both characters; the gesture file passes the rig checks (unit test) and reviewers sign off in the gallery | FR-11, D-21 |
| TC-22 | The subject taps Write on the stage (any profile but young child), types and taps Done; the answer appears in the officer's conversation as a typed answer and in the export | FR-71 |
| TC-23 | Assistant: a known model file imports, any other file is refused and deleted; a typed question returns a caption and its back-translation; Send shows the caption and logs both texts | FR-68, D-24, SEC-65 |
| TC-24 | Every text field reports `IME_FLAG_NO_PERSONALIZED_LEARNING` to the keyboard | SEC-05 |
| TC-25 | Frames reach the tracker only inside a sign window; the first frame is analysed whatever its timestamp and later frames wait for the interval (`AnalysisRateTest`) | FR-84, SEC-71 |
| TC-26 | Segmentation: pauses split signs at exact index ranges; short holds and twitches stay inside or are dropped; incremental capture equals a full scan; hands leaving the frame do not end it; a 3-minute narrative drops faces and splits only at longer pauses (`SegmentTest`, `SignIngestionTest`) | FR-84, FR-87 |
| TC-27 | Sign reader: Kotlin logits match the Python reference within 1e-3 on five fixtures; guesses are ranked probabilities; forward pass timed (`SignReaderTest`) | FR-85 |
| TC-28 | Guesses that fit the item on screen rank first and the others stay; the subject chooses among two or three alternatives with the officer's reading first; the slower cue appears only after a failed read of fast or lost hands; every sign-word maps to the reader and the vocabulary (`SignIngestionTest`) | FR-85 to FR-87 |
| TC-29 | Read signs on the stage: one dot per sign, Done logs the app's reading as MACHINE; the officer's reading is logged beside it; Ask them to choose logs each set before it shows; None of these logs Unclear; ending the session, locking or clearing the view model leaves no landmarks | FR-84 to FR-87, SEC-71 |
| TC-30 | Role gating against the real bank: sensitive only for a victim; never an account question for an accused; a child in conflict with law gets the child's rights, not the adult ones; role survives the database (`RoleTest`) | FR-88 |
| TC-31 | Auto sequencer against the real bank: phase order; manual picks; the ceiling (no sensitive, free-recall, follow-up or rights item ever proposed); R1, R2, R3, R4b, R6 and R8; auto-advance only statement to statement, at most 3; answers pair by shown ID; content checks (`InterviewTest`) | FR-77 to FR-80 |
| TC-32 | Auto logging and control: suggestion, approval and showing in order; skips; Stop pauses and a second Stop ends Auto; a break request pauses; typed or event questions turn Auto off; a role change drops the proposal; a closed database turns Auto off without a crash (`AutoPilotTest`) | FR-79, FR-81, FR-83 |
| TC-33 | Pairing protocol over piped streams: both sides derive the same code, check number and keys; mismatched reveal, version, content hash or off-curve key aborts; frame caps before allocation; handshake timeout; tampered, replayed or skipped frames close the link; resume with the right key; the stage view never carries the draft; late and duplicate answers; drawing bounds (`PairTest`) | FR-72 to FR-76, SEC-66 to SEC-70 |
| TC-34 | Officer page: completions rank signed words first; spelled words are marked; answer trails and pause length; say-back grading; choices shuffled with ways out; the "Shown as" row and picture positions (`MainPageTest`) | FR-89 to FR-94 |
| TC-35 | Stage: glance strip shows the last question and latest answer; vibration codes; signing speed lasts the session; a tapped word is found; pause length from Stop or Break; every opening step and answer has words and pictures (`StageLogicTest`) | FR-95 to FR-98 |
| TC-36 | Sun mode switches on after 3 s above 10,000 lux and off after 10 s below 2,000 lux (`SunModeTest`) | FR-99 |
| TC-37 | Tilt fires once after the dwell and must re-arm; a short dip does not fire; both volume keys held fire once; one key never fires; keys stay with the system unless something is ready or shown (`HandoverTest`) | FR-100 |
| TC-38 | Kill the app while the stage shows; on restart the full biometric or PIN is asked even straight after a phone unlock | FR-101, SEC-75 |
| TC-39 | Database migrations 1 to 2 (role) and 2 to 3 (item, reason, shown and via columns) keep existing rows (`SessionMigrationTest`) | FR-34, FR-81 |
| TC-40 | Rig parity: `RigFixtureTest` regenerates nothing and `vp test` passes on the portal's TypeScript rig | D-31 |
| TC-41 | Two phones and a tablet: the second-screen script UAT-18 | FR-72 to FR-76 |
| TC-42 | Lesson inputs: every lesson question is in the bank in English and Hindi with its pictures; phone numbers need ten digits starting 6 to 9; ages, counts and classes stay in range; names need letters and no digits; answers read back in the subject's language; how an answer was given survives the record; the report lists each detail with how and whether confirmed; a young child is not asked for a mobile number; an accused keeps the factual questions but no welfare-only items; lesson questions are signed in draft ISL order (`LessonTest`) | FR-102 to FR-104, D-36 |
| TC-43 | One lexicon: measured shapes parse and clamp and play on the rig; recorded signs come first, then authored, then gesture, then spelling; a concept prefers the label written as typed; homonyms and multi-word signs are handled; the dictionary finds recorded and draft signs in either language; every INCLUDE word is retargeted with attribution; every sign stays in frame and authored signs keep each hand on its side; every reader label can be signed back (`IslLexiconTest`) | FR-13, FR-106, D-35 |
| TC-44 | Recorded keys closer than 0.35 s pass through without stopping; both hands stay inside the body view; questions get their face (`AvatarTest`) | D-35, D-37 |
| TC-45 | Holistic results map to the subject's own sides in order with the timestamp, and empty lists mean not seen (`HolisticMappingTest`) | FR-84 |
| TC-46 | Stage layout on a phone: the camera window keeps the 9:16 frame uncropped between the rails; Stop is the only pause control on the phone's stage; a second screen still shows its break screen after Stop | FR-107, FR-76 |
| TC-47 | Sign back in both languages: every ISL and ASL reader label resolves through `signBack` to a motion of its own language and is in that language's dictionary; ASL spelling of '9876543210' gives ten `asl.digit.*` motions and the dictionary omits them; ASL gloss 'me want water' gives 'I want water.' (`IslLexiconTest`, `SignLanguageTest`) | FR-86, FR-106, FR-108 |

## 3. Performance

Release build on the API 29 emulator with 3 GB RAM, then on a real Android 10 phone.

| ID | Measure | Target |
| --- | --- | --- |
| PT-01 | Cold start (Macrobenchmark `StartupTimingMetric`) | Under 2 s (NFR-01) |
| PT-02 | Confirm to Show state with clip playing | Under 300 ms (NFR-02) |
| PT-03 | Clip playback frame timing (`FrameTimingMetric`) | No janky frames at source rate (NFR-03) |
| PT-04 | Release APK size | Under 50 MB (NFR-05) |
| PT-05 | Memory over a 30-question session | Under 300 MB (NFR-06) |

## 4. Resilience

| ID | Drill | Expected |
| --- | --- | --- |
| RD-01 | Incoming call during Show state | Session preserved; pinning restored on return |
| RD-02 | Low storage during export | Clear message; session intact |
| RD-03 | Battery saver, dark mode, 200% font | App fully usable |
| RD-04 | Device lock removed while sessions exist | Sessions unreadable; app explains and offers Clear |
| RD-05 | Corrupted content file | Hash check fails; a failed picture, sound or gesture is left out and the rest still works; a failed text file stops new sessions; the officer is told |

## 5. Security

| ID | Test | Controls |
| --- | --- | --- |
| ST-01 | `adb backup` and device-transfer extraction contain no app data | SEC-03 |
| ST-02 | Database file on a rooted test device is unreadable without the key | SEC-01 |
| ST-03 | Screenshot and recording blocked; recents thumbnail blank | SEC-40 |
| ST-04 | Logcat across a full session contains no personal data | SEC-04 |
| ST-05 | Merged manifest: no INTERNET, RECORD_AUDIO or LOCATION permission; CAMERA present only for the mirror | SEC-30, SEC-42 |
| ST-07 | `dumpsys package`: only the launcher activity exported | SEC-41 |
| ST-08 | Decompiled release APK: no keys, test code or debug flags; obfuscated | SEC-11, SEC-53, SEC-60 |
| ST-09 | Lock after 5 minutes in background; key unusable while locked; run on API 29 and API 30+ | SEC-21, SEC-22 |
| ST-10 | Export unreadable without passphrase; tampered file rejected by readers | SEC-12 |
| ST-12 | Tampered content bundle rejected | SEC-54 |
| ST-13 | SBOM complete; no known critical vulnerabilities; gitleaks clean | SEC-50 to SEC-52 |
| ST-14 | Overlay app over Show, export and clear actions: touches filtered | SEC-45 |
| ST-15 | Merged manifest of every variant: the standard build has no discovery or location permission and adding BLUETOOTH_SCAN by hand fails the build; the nearby build adds only the D-26 list; no build has INTERNET, RECORD_AUDIO or ACCESS_BACKGROUND_LOCATION | SEC-30, SEC-66, SEC-73 |
| ST-16 | Dependency tree has no `transport-backend-cct`; the stub destination is present; nothing leaves the phone during a sign window (no network permission) | SEC-72 |
| ST-17 | Stage device after a session: `run-as ls -R` shows no new files other than the settings store; the hand-back screen stays pinned until the stage credential | SEC-69, FR-73 |
| ST-18 | Logcat during a sign window and a pairing session: no landmarks, guesses, exception messages or Bluetooth names | SEC-70, SEC-71 |
| ST-19 | Release APK contains the holistic and sign-reader models listed in the manifest; one changed byte in either disables it and leaves a plain mirror | SEC-74 |

ST-06 and ST-11 (attestation, gateway fuzzing) apply from release 1.1.

## 6. Accessibility

| ID | Test | Requirement |
| --- | --- | --- |
| AT-01 | Accessibility checks (Accessibility Test Framework or Scanner) on every screen: no label, target or contrast failures | NFR-20, NFR-21 |
| AT-02 | 200% font scale: officer panel usable | NFR-22 |
| AT-03 | TalkBack: complete triage flow as officer | NFR-23 |
| AT-04 | Every supported script renders without clipping | NFR-25 |
| AT-05 | Greyscale: Yes and No still distinguishable | NFR-21 |
| AT-06 | Accessibility Test Framework over every lesson input (age pad, phone pad with No phone, sibling pads, class grid, state picker, name entry, several pictures, inputs without words in the subject's language) and the phrase board, in both themes | NFR-20, NFR-21 |
| AT-07 | Accessibility Test Framework over the stage device screens (waiting, code, reconnecting, hold, break, ended) | NFR-20, NFR-21 |

## 7. Content validation and testing with people

- Content review and comprehension thresholds are in [avatar and gestures](05-avatar-and-gestures.md) §8.
- At least five people new to the app run scripted scenarios after a short briefing; record hesitation points.
- Any test with children needs approval from an institutional ethics committee, written consent from a parent or guardian, the child's own assent, a child-welfare professional present, and the right to stop at any time. Sessions take place in a familiar setting such as the child's school. Rescued or victimised children are never test participants. No video of child participants is kept after scoring.

## 8. Devices

| Device | Purpose |
| --- | --- |
| Emulator, API 29, 3 GB RAM | Performance floor and automated tests |
| Real Android 10 phone, 3 GB RAM | Final performance and pinning behaviour |
| Recent phone | Current platform behaviour |
| Second phone (API 29, fingerprint enrolled) and a tablet | Second screen (UAT-18); the emulator has no Bluetooth |
| Emulator, latest API | Automated tests |

## 9. Exit criteria per release

- All MUST requirements pass on the device list.
- No open critical or high defects; ST-01 to ST-19 (release 1 set) pass.
- Performance targets met on the oldest device.
- Test report stored with the release record.
