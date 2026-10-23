# SignSetu — full sweep: avatar quality, backend, text→sign, sign→text

Date: 2026-10-23. Repo: `C:\work\sign-setu` (branch as checked out, no pushes made).
Every finding below was reproduced in this checkout before it was touched, and every fix is backed by a
test that fails without it. Nothing here is inferred from a doc comment alone.

Environment used:

| What | Path |
| --- | --- |
| Repository | `C:\work\sign-setu` |
| MediaPipe / kinematics venv | `C:\Users\Yash\dev\venvs\signsetu-mp\Scripts\python.exe` |
| Content / tools venv | `C:\Users\Yash\dev\venvs\signsetu-tools\Scripts\python.exe` |
| Datasets | `C:\Users\Yash\dev\ml-data\` |
| Android SDK | `C:\Users\Yash\dev\android-sdk` (AVD `pa_api34`) |

Verification commands (all run from the repo root):

```bash
./gradlew :app:testStandardDebugUnitTest            # Kotlin / Android
cd backend && PYTHONPATH=. .venv/Scripts/python.exe -m pytest -q
cd portal  && npx vitest run                        # rig parity + portal
```

| Suite | Before | After (second pass, §9) |
| --- | --- | --- |
| Kotlin unit tests (`testStandardDebugUnitTest`) | 264 tests, 45 classes | **303 tests, 49 classes, 0 failures** |
| Backend `pytest` | 27 passed | **31 passed** |
| Portal `vitest` (incl. `src/rig.test.ts`, 1207 rig parity cases) | 1217 passed | **1217 passed** |
| AVD `pa_api34` | — | cold start, practice flow driven through the UI, a read window closing itself, no crash |

> Section 1 describes the first pass on the elbow (a 4-unit hysteresis band). The second pass (§9.2) measured
> that band still snapping and replaced it with unconditional continuity; read §9.2 as the current behaviour.

---

## 1. Avatar quality and the elbow

### What was wrong (evidence)

1. **The forearm stretched.** `Timeline.resolve()` placed the wrist wherever the key asked, but
   `solveElbow()` internally clamps the wrist distance to `upper + fore - 0.01`. Any key whose hand
   target sat further than arm length from the shoulder therefore drew a bone that could not exist:
   the elbow stopped at `upper` while the hand stayed out at the unreachable target, so the forearm
   looked long and the elbow looked "broken".
   Evidence: `Rig.kt` `solveElbow` clamps `d`, `resolve()` did not.
2. **The elbow flipped across the body.** A two-bone solve has two mirror solutions. As the wrist
   crosses the shoulder line the two scores cross, and the solver switched branches within a frame —
   the elbow jumped from one side of the arm to the other. This is the "sometimes the elbow is wrong"
   report: it is reproducible by sweeping the wrist horizontally through the shoulder.
3. **The authored `elbowOut` was being ignored** by the first hysteresis attempt (it preferred the
   previous elbow unconditionally), which would have flattened every sign's deliberate elbow pose.
4. **Every word started from rest.** `chain()` re-filled each part's first key from `RestKey`, so a
   multi-word sentence dropped both arms to the sides between words and raised them again — reading as
   separate clips rather than one sentence.

### What changed

| File | Change |
| --- | --- |
| `app/src/main/java/app/signsetu/ui/avatar/Rig.kt` | New `clampReach()`: the wrist target is pulled back onto the sphere of radius `upper + fore - 0.01` around the shoulder, along the same line of sight, before any solve. `HandPose.elbowAt()` forwards an optional `prefer` elbow. `solveElbow()` keeps the previous frame's branch **only inside a narrow score band** (`FLIP_BAND = 4f`), so authored `elbowOut` still decides everywhere else. `chain()` carries the previous part's hands, face, head and body into the next part instead of restarting at rest. |
| `app/src/main/java/app/signsetu/ui/avatar/Avatar.kt` | `LastElbows` remembers the elbow drawn last frame per side and threads it through `drawArm()`. |
| `portal/src/rig.ts` | `clampReach()`, banded `prefer` in `solveElbow()`, and the same `resolve()` clamp — kept in step so the web preview matches the app. |
| `portal/src/rig-fixtures.json` | Regenerated fixtures reflecting the clamped reach. |

The easing that already existed (`ease()` = ease-in-out-cubic for all tracks, `easeOutBack()` for a
free move that overshoots and settles, `Timeline`'s 0.08 s pre-landing hold, and the Catmull-Rom
`flow()` for recorded `c:` keys) was **verified, not replaced**: the animation was already eased, so
the mechanical look was coming from the pose data and the elbow, not from linear interpolation.

### Tests — `app/src/test/java/app/signsetu/AvatarElbowTest.kt` (5)

* a wrist inside reach is left exactly where it is;
* a wrist beyond reach lands on the arm-length sphere **and stays on the same line of sight**;
* the elbow is always exactly `upperArm` from the shoulder for four extreme targets;
* sweeping the wrist 120 px through the shoulder line, the hinted solve never jumps more than an
  upper arm and never worse than the unhinted solve (the unhinted solve snaps);
* a following word in `chain()` opens on the previous word's hand, while a one-word gesture still
  starts from rest.

---

## 2. Backend

### What was verified first

`backend/signsetu_gateway/app.py` is a 188-line FastAPI app: `/healthz`, `/v1/enroll`, `/v1/models`,
`/v1/assist`, `/v1/feedback`, and `/v1/admin/feedback` + `/v1/admin/devices` behind reviewer auth.
Auth is device tokens plus an Ed25519-signed reviewer bearer; the rate limiter is per client key and
per route class; audit rows are written per mutation.

The earlier "pytest is broken on a clean checkout" claim was **my environment, not the repo**: CI runs
`pip install -e ".[test]"` first, and reproducing that gives 27 passing tests. The stray `backend/uv.lock`
my probe created was deleted. No CI change is needed for that.

### Real gaps found

1. **`/v1/assist` accepted any length of user text** at the model layer. The route checks
   `settings.assist_max_chars` (default 20 000), but the request model had no bound at all, so a
   multi-megabyte body was parsed and held in memory before the 413 was decided.
2. **`/v1/admin/feedback?limit=` was unbounded input**: `int` with a runtime `min(limit, 1000)`.
   `limit=0`, `limit=-5` and `limit=100000` were all accepted; the clamp silently hid the typo
   instead of rejecting it, and `limit=many` produced a 500-class parse error rather than a 422.
3. **`/v1/admin/devices` had no page size at all**: it returned every device row, so the response grows
   without bound as devices enrol. It now takes the same `limit` (1–1000, default 200).
4. **A `corrected` feedback row could omit the correction.** `outcome="corrected"` with no `confirmed`
   label was accepted and stored, which is exactly the row a reviewer would use to teach the next model
   — an empty one. It is now a 422 at the edge (`outcome="confirmed"` still needs nothing).

### What changed

| File | Change |
| --- | --- |
| `backend/signsetu_gateway/app.py` | `AssistIn.user` gets `Field(max_length=1_000_000)` — a hard parse-time ceiling, with the configurable 20 000-char cap still answering 413. `admin_feedback` uses `Query(200, ge=1, le=1000)`, so an out-of-range page size is a 422 instead of a silent clamp. `admin_devices` takes the same bounded `limit`. `FeedbackIn` refuses `outcome="corrected"` without a non-blank `confirmed`. |
| `backend/README.md` | The two page-size bounds and the corrected-feedback rule are documented where the routes are. |
| `backend/tests/test_gateway.py` | Four new tests (below). |

### Tests (new)

* `test_an_oversized_body_is_refused_before_it_is_read` — 20 001 chars is refused (413 from the route
  cap, or 422 from the model ceiling), i.e. it never reaches the assistant.
* `test_the_feedback_page_size_is_bounded` — `limit=1` works, `limit=0 / -5 / 1001 / many` are 422.
* `test_a_correction_must_carry_the_corrected_label` — `corrected` without `confirmed`, and with a blank
  `confirmed`, are 422; with `confirmed="rain"` it is 201 and a plain `confirmed` row stays 201.
* `test_the_device_list_is_bounded` — `limit=1` works, `limit=0 / -1 / 1001 / many` are 422.

Backend suite now **31 passed**.

---

## 3. Text → sign

`ContentBundle.planText()` (`app/src/main/java/app/signsetu/data/content/TextSigning.kt`) already
tokenised, dropped ISL-unsigned grammar words, reordered to ISL (time first, verb last), fell back to
fingerspelling, and emitted word beats; it is wired live at `Stage.kt` (`Pending.FreeText`) and reused
by `Lesson`, `IslLexicon` and `PromptRecord`. The defects were in the edges:

1. **Accented Latin was silently truncated.** `spelling("café")` produced `c,a,f` — `é` was filtered
   out, so the word signed short and the wrong length. Now NFD-decomposed and stripped of combining
   marks: `café → c,a,f,e`, `ÁNGST → a,n,g,s,t`.
2. **ASCII digits inside a Hindi word vanished.** `कोविड19` signed only the aksharas. `spellingAkshara`
   now keeps `0`–`9` (Devanagari numerals were already mapped) and they route to `digit.n`.
3. **Two sentences signed as one run.** A sentence break (`.` `।` `!` `?`) inside the text inserted no
   beat, so the avatar ran on. A `pause` gesture is now inserted at each boundary.
4. **`؟` (Arabic question mark) set the question face but added no ask sign** — `questionKind()` knew
   it, `planText()` did not. Both accept it now.

### Tests — `app/src/test/java/app/signsetu/TextSigningHardeningTest.kt` (8)

Accent folding; digits inside Hindi words; per-akshara Hindi spelling (`को` → क, ओ); the sentence pause
landing between the right beats; Arabic question mark; empty / filler-only text producing no gesture;
an unknown word spelled only when `SPELL` is on; beat and range invariants; and a 40-word text staying
on one monotonic timeline.

---

## 4. Sign → text (read-back)

`glossToText()` turns a recognised gloss into the English/Hindi/Chhattisgarhi draft the officer reads.
Four defects, all found by testing the reader's real output:

1. **A word that was already English was replaced by the dictionary's first alias.** Gloss `your`
   became **`you`** (`words["your"].w.first()`), so "Where your mother?" read back as "Where you
   mother?" — a different sentence. Tokens are now normalised once, at entry: a token that is already
   an English headword is kept verbatim; only a gesture id (`no.shake`), a Hindi headword (`पानी`) or a
   sign id is resolved through the lexicon.
2. **ASL questions lost their question mark.** `rank()` short-circuited to 1 for ASL, which also
   disabled wh-sign detection, so an ASL "where …" gloss ended with a full stop. ASL now keeps its own
   word order (time words are no longer pulled to the front) but still detects the wh-sign and the
   question mark. ASL also no longer receives an invented English `do` — the gloss is already the
   sentence.
3. **A head shake that arrived as an id was not negation.** `no.shake` matched no dictionary key, so
   "me hungry no.shake" read back as a positive statement. Resolution through the sign id fixes it:
   the draft now contains `not` / `नहीं`.
4. **The Hindi sentence lost its dative doer.** `HINDI_VERB`/`DOER` are keyed by each entry's first
   written form (`i`, not `me`), so `मुझे पानी चाहिए` had regressed to `मैं पानी चाहिए`. Lookups now go
   through the entry key, and the Hindi draft is `मुझे पानी चाहिए।` again.

### Tests — `app/src/test/java/app/signsetu/GlossReadBackHardeningTest.kt` (8)

Empty / punctuation-only gloss is an empty draft, not a bare full stop; ISL reordering to SVO; ASL
keeping its own order *and* its own question mark; a fingerspelled run joined into one word; raised
brows asking without a question sign, and no question mark without them; Hindi verb-last with
Chhattisgarhi swaps; and `no.shake` negating both drafts.

### The ceiling that no code change fixes

The bundled readers are documented at **top-1 0.41 (ISL) / 0.40 (ASL)** on real signers
(`app/src/main/assets/content/…`, model notes). That accuracy is enough to *suggest* a reading and let
the subject confirm it — which is exactly why the stage shows choices rather than a transcript — but it
is not enough for reliable free-flowing transcription. Improving it needs ISLRTC training data
(`C:\Users\Yash\dev\ml-data\islrtc`), not plumbing: the pipeline is already correct end to end.

---

## 5. AVD smoke check

AVD `pa_api34` (API 34, x86_64), `:app:installStandardDebug`, launcher
`in.signsetu/app.signsetu.MainActivity` (the code namespace `app.signsetu` is not the application id; the
application id is `in.signsetu` — launching `in.signsetu/.MainActivity` resolves to nothing).

* Cold start: `LaunchState: COLD`, `TotalTime: 1835 ms`, process alive, no `FATAL EXCEPTION` in logcat.
* First-run screen renders the avatar with both arms bent at the elbow, forearms forward, hands meeting in
  front of the body — no stretched forearm, no elbow on the wrong side.
* The new practice screen was driven end to end through the UI: Start → the new pencil button → type
  `i want water yesterday` → the word chips appear in ISL order (`yesterday`, `i`, `want`, `water`, each
  marked *signed*) → **Sign it** plays water and then where with the elbows bending correctly through the
  moves. Two frames a second apart differ, so the gesture is animating, and logcat has no `FATAL EXCEPTION`.
* The previous install failure (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) was a stale package on the AVD with a
  different signing key; uninstalling it first is required before `installStandardDebug`.

One look worth recording: at large sizes the forearm can read as *detached* from the rolled sleeve, and the
sleeve is a wide bell around the upper arm. To know whether that was a regression from the reach clamp, the
pre-change `Rig.kt`/`Avatar.kt` were checked out temporarily, built, installed, and the same screen captured at
the same moment of the same sentence: **the old build shows the same silhouette**, so this is the art's arm
proportions, not the solver. The files were restored immediately afterwards (working tree clean against the
commit).

## 6. The type-any-text practice surface

There was no standalone place to type a sentence and watch it signed: the planner was only reachable from
inside a live session (`Pending.FreeText`) and from the officer's dictionary sheet. This is the change that
makes "any text → signs" directly usable.

| File | Change |
| --- | --- |
| `app/src/main/java/app/signsetu/ui/practice/PracticeScreen.kt` | New screen: text field, the avatar, the word list with each word marked *signed* or *spelled*, and **Sign it**. It plans with `SIGNS + GESTURES + SPELL + PICTURES`, so a word with no sign is fingerspelled rather than skipped, and it passes `questionKind(text)` to the avatar so `?`/`؟` raise the brows. |
| `app/src/main/java/app/signsetu/ui/AppRoot.kt` | `PracticeKey` entry in the Navigation 3 back stack, reachable from Start. |
| `app/src/main/java/app/signsetu/ui/start/StartScreen.kt` | A pencil button beside Settings, with the practice title as its content description. |
| `res/values/strings.xml`, `res/values-hi/strings.xml` | Seven strings in English and Hindi. |
| `app/src/test/java/app/signsetu/TextSigningHardeningTest.kt` | `anySentenceIsSignableWithThePracticeModes`: `Where is the hospital, Dr Mehta?` signs hospital/doctor, fingerspells the name, keeps the ISL order (question word last), and every beat has a motion. |

Deliberately absent: recording, exporting, or logging anything. It is a practice surface, not a session, so
nothing typed there is stored.

## 7. What was deliberately not done

* **No recognition model retraining or re-export.** Out of scope for a hardening pass and would need
  the datasets plus a training run.
* **No CI edits.** CI already installs the backend package editable before `pytest`; the earlier
  failure did not reproduce.
* **No change to the avatar art itself.** The wide sleeve and the large hands at big sizes are the existing
  drawing style; changing proportions is an art decision, and the A/B above shows it is not a solver bug.
* **No measured handshapes for the alphabet.** Fingerspelled letters are drawn from authored shape codes
  (`FLAT`, `CUP`, `POINT`, `TWO`, `HOOK`, `PINCH`, `FIST`, `OPEN`, `THREE`, `PINKY`) instead of the measured
  `c:` finger curls the retargeted signs carry, which is why letters look more like mannequin hands than
  words do. There is nothing to switch to: `isl_signs.json` holds 415 retargeted gestures and none of them
  is a `letter.*`; `signs.json` carries only the digits, and no alphabet recording exists under
  `C:\Users\Yash\dev\ml-data`. Closing this needs a recorded alphabet run through the retarget pipeline,
  which is a data task, not a code change.

## 8. File inventory

Modified:

* `app/src/main/java/app/signsetu/ui/avatar/Rig.kt`
* `app/src/main/java/app/signsetu/ui/avatar/Avatar.kt`
* `app/src/main/java/app/signsetu/data/content/TextSigning.kt`
* `backend/signsetu_gateway/app.py`
* `backend/tests/test_gateway.py`
* `portal/src/rig.ts`
* `portal/src/rig-fixtures.json`

Added:

* `app/src/main/java/app/signsetu/ui/practice/PracticeScreen.kt`
* `app/src/test/java/app/signsetu/AvatarElbowTest.kt`
* `app/src/test/java/app/signsetu/TextSigningHardeningTest.kt`
* `app/src/test/java/app/signsetu/GlossReadBackHardeningTest.kt`
* `app/src/test/java/app/signsetu/SignSettleTest.kt` (§9.1)
* `INVESTIGATION.md` (this file)

---

# 9. Second pass — a window that ingests itself, elbow continuity, three more read-back defects

Same checkout, same AVD, same three suites. Everything below was reproduced first, then changed, then
re-checked with the command block at the top of this file.

## 9.1 "If no sign happens, ingest what was given" — the window could not end itself

### Evidence

* `SignCapture` closes a **chunk** after `pauseMs` of no hand motion (`ANSWER` 300 ms, `NARRATIVE` 700 ms),
  but the **window** ended in exactly three ways: the subject's Done/Stop rail, the officer's
  `finishReading()`, or the window cap (`ANSWER` 20 s, `NARRATIVE` 180 s). Nothing ended it on quiet.
* On the AVD, officer pane → **Read signs** with nobody in frame left the stage in "Signing…" for the
  whole 20 s cap, and the subject sees that same screen. With a real person who signs three words and
  stops, the app therefore waited for a tap before it read anything.
* `CameraMirror`'s analyser delivers **every** tracked frame to `onSignFrame` while a window is open
  (`ANALYSIS_INTERVAL_MS = 25`), hands present or not, so frame timestamps are a usable clock that cannot
  run ahead of the frames it judges — no wall-clock timer is needed.

### What changed

| File | Change |
| --- | --- |
| `app/src/main/java/app/signsetu/sign/SignCapture.kt` | `SignWindow` gains `firstSignMs = 3_000` and `quietMs` (**2 s** for `ANSWER`, **5 s** for `NARRATIVE`). New `SignQuiet`: `see(frameMs, moved)`, `settled(newestMs)` = quiet ≥ `quietMs` since the last moving frame, or ≥ `firstSignMs` since the first frame if nothing ever moved; `reset()` for a fresh window. `SignCapture.settled()` wraps it and is false once the cap is full, so a capped window is ingested once. |
| `app/src/main/java/app/signsetu/ui/session/SessionViewModel.kt` | `onSignFrame()` ingests on `settled()` exactly as it already did on the cap: the frame that settles the window is the last one it sees, the segments already handed out are read and logged, and the stage leaves signing mode. |
| `app/src/main/java/app/signsetu/ui/pair/StageDevice.kt` | The paired second screen applies the same rule to the frames it collects, then sends `AnswerBody.Signed` as its Done button would. Its Done handler is now one `endSigning` lambda shared by both paths. |
| `app/src/main/java/app/signsetu/ui/session/VideoCallStage.kt` | Hands raised when a window closes must come down once before they auto-open the next one, so an answer that was just ingested is not re-read the next frame. |

Why the narrative waits 5 s: a "Tell me what happened" account pauses between phrases on purpose; 2 s
would cut a three-minute account into pieces. The two numbers live on the enum, not scattered in the UI.

### Tests

`app/src/test/java/app/signsetu/SignSettleTest.kt` (8):

* an empty window settles after exactly 3 s of grace, not before;
* the quiet timer starts at the last *moving* frame, and a twitch restarts it;
* `reset()` does not let the last window's grace carry over;
* a sign is handed out before the window settles, 2.00–2.10 s after its last moving frame;
* the 2 s is counted from the hands stopping, not from the 300 ms chunk close;
* a 3 s silence mid-answer keeps the signs already read and then ends the window;
* a narrative is not ended by a short answer's 2 s, and does settle after 5 s;
* a window that hit its cap reports both `full` and not-`settled`.

`ConversationE2ETest` (+2, through the real view model and the real ISL reader):

* `aQuietAnswerIsIngestedWithoutAnyTap` — three signs, then 2.3 s of hands-down frames: an
  `ANSWER_SIGNED` row with all three words, no `finishReading()` call anywhere;
* `aWindowWithNoSignsClosesItselfAfterThreeSeconds` — 100 empty frames: `reading` becomes null and no
  signed row is written for an answer nobody gave.

### AVD

`pa_api34`, standard debug, camera front = emulated. Officer pane → **Read signs**: the stage showed
**"Signing…"**, and about four seconds later it was back to the idle call screen ("You"), with no
`ANSWER_SIGNED` row and no `FATAL EXCEPTION` in logcat. That is the 3 s grace close, on device, with real
camera frames. The "2 s after the hands stop" path needs a person signing, so it is covered by the unit
and view-model tests above; it is the same `settled()` call in the same frame callback.

*Limit, stated plainly:* every one of these timers is advanced by frames. A camera that delivers no
frames at all (unplugged, permission revoked mid-window) never settles and never hits its cap; the
Done/Stop rail is still the way out, and that is unchanged from before.

## 9.2 The elbow still snapped just outside the band

### Evidence

A sweep of 720 wrist positions on a circle around the shoulder, each solve hinted with the previous
elbow (`Avatar.drawArm` → `LastElbows` → `HandPose.elbowAt(…, prefer)`), measured the worst frame-to-frame elbow
jump:

```
            radius 30      radius 60      radius 90
elbowOut 1     72.5            51.0             0.4
elbowOut 0     72.9            51.6             0.4
elbowOut 0.5   72.9            51.6             0.4     (upper 41, fore 38, so arm length 79)
```

A jump of 51–73 units in one frame is most of an upper arm: the two mirror solves are simply far apart.
The first pass held the branch only while `|s1 - s2| < FLIP_BAND (4)`; one degree past the crossover the
band released to the score, which is exactly where the score prefers the *other* branch. So the reported
"sometimes the elbow is wrong" was still reproducible, just a few degrees away from the protected spot.

### What changed

`Rig.kt` and `portal/src/rig.ts` (kept in step): when a previous elbow exists it always wins — the nearest
of the two solves, with no band. The score now only seeds the first frame of a movement (`prefer == null`,
e.g. the first draw, or a pose whose keys carry a recorded elbow). `FLIP_BAND` is gone from both files.

### Test

`AvatarElbowTest.theElbowNeverSnapsRightAroundTheShoulder`: 720 positions × `elbowOut` 1 / 0.5 / 0 × radius
30 / 60 / 90, every step adjacent to the last; asserts the worst jump is **< 5 units**. The old code fails
this by a factor of ten. `RigFixtureTest` and the 1207-case portal parity suite still pass, because
fixture poses are solved with `prefer = null` (unchanged path).

### AVD

Practice screen, `i want water yesterday`: the raised arm of *yesterday* (hand at the temple) keeps its
elbow outside the shoulder with the forearm crossing the face, and the following *i*/*want*/*water* poses
come off the previous elbow without a jump. Captured frames saved under `app/build/seq*.png` (build dir,
not committed).

## 9.3 Read-back: three more English defects, found by printing the draft

Probe of `glossToText()` against the shipped vocabulary before the fix:

```
[what]                    -> "What do?"            [no.shake]           -> "Do not."
[me, hungry, no.shake]    -> "I do not hungry."    [where, your, mother] -> "Where do your mother?"
```

1. **A dangling auxiliary.** "What do?" — the English `do` was added for every wh-question even with
   nothing for it to precede. It is now added only when a verb was signed (`HINDI_VERB` covers the verb
   vocabulary) *and* there is something after it: `What do you want?` still, `What?` now.
2. **A shake on its own read as "Do not."** A gloss that is only a head shake is the answer "No." —
   `No.` / `नहीं।` / `नइ।` now.
3. **A state word took the auxiliary.** "I do not hungry" — states take a copula: `I am not hungry.`,
   `You are not hungry.`, while a real verb keeps `I do not want water.`. No verb is invented for a
   possession either: `Where your mother?`, matching what the ASL path already produced.

Tests: `GlossReadBackHardeningTest` +4 (`aStateWordTakesACopulaNotAnAuxiliary`,
`aHeadShakeOnItsOwnIsNoNotDoNot`, `aQuestionWordCarriesNoDanglingAuxiliary`), 12 in the class.

## 9.4 Measured, not assumed: the cost of planning text on every keystroke

The practice screen plans inside `remember(text)` during composition, i.e. once per keystroke.
`planText()` with `SIGNS + GESTURES + SPELL + PICTURES` on the shipped content:

| Characters | Beats | Gesture keys | Time |
| --- | --- | --- | --- |
| 80 | 11 | 61 | 1.9 ms |
| 297 | 42 | 271 | 3.2 ms |
| 594 | 84 | 544 | 4.1 ms |
| 1188 (≈200 words) | 168 | 1090 | 8.9 ms |

Under a frame budget even for a 200-word paragraph, so no debounce, worker thread or caching was added:
the simplest code is also fast enough here.

## 9.5 Re-checked and deliberately unchanged

* **Backend.** `app.py` re-read end to end after the first pass: `/v1/assist` (model ceiling + configurable
  413), `/v1/feedback` (labels only, `extra="forbid"`, correction required), both admin lists (1–1000),
  `/v1/enroll` (code length 8–128, per-address and global failure limiters, audit rows).
  `/v1/models/{name}` builds `model_dir / name` before the `NAME` regex check, but the download is gated on
  the manifest entry, which is server-owned, and `NAME` cannot contain a separator — no traversal path, so
  it was left alone. No new gap worth a change; **31 tests still pass**.
* **Text → sign.** Re-probed `planText` on accents, Hindi digits, `؟`, sentence breaks, unknown words,
  200-word input: no new defect, and the existing 8 tests still pass.
* **The 20 s cap and the chunk pauses.** Unchanged on purpose: the new settle rule is an *additional* way
  out, not a replacement, so a window that never goes quiet still reads at the cap exactly as before.
* **Avatar art.** Same conclusion as §5/§7: the rolled sleeve's silhouette is the drawing style, not the
  solver.

---

# 10. Sign extraction from video — what was measured, and how far to trust it

The question was how confident we can be that the app's chain turns a video into signs. Rather than argue it,
this section measures it three ways: on a device running the real pipeline over bundled clips, on raw dictionary
videos through a new end-to-end tool, and against the numbers the exported models were trained against.

## 10.1 The tool: `tools/ml/eval_video.py`

Video files through the chain the app runs, not through cached landmarks:

1. decode each clip with OpenCV at its own frame rate;
2. track every frame with the bundled `holistic_landmarker.task` (the same model the app ships);
3. cut it into signs with the app's own rules (`SignCapture`: 300 ms pause, 180 ms minimum, 3 active frames,
   0.3 frame-widths/s motion, 20 s cap) — the Python is a line-for-line port, including §10.2's new rule;
4. featurize each sign as `LandmarkSignReader` does (`features.py`) and read it with the exported
   `sign_reader` / `isl_spell` through `runtime.py`, applying `SignReaders.kt`'s sign-or-spell gate at 2×.

A clip's expected label comes from a `labels.json` beside it (or a `1_One.mp4`-style name); `--whole` reads the
whole clip as one sign, which separates a reader's accuracy from the app's cutting.

    signsetu-mp/python -s tools/ml/eval_video.py --dir <folder> [--whole]

## 10.2 A defect the measurement found: the hands going back to rest read as a sign

Every one of the ten ISLRTC digit clips cut into **two** signs, and the second was junk with a confident score:

```
10_Ten   -> 1, key        2_Two  -> 2, 0          3_Three -> 3, key
5_Five   -> 5, key        7_Seven-> t, key        9_Nine  -> a, key
```

Geometry of the second chunk, straight from the clip (shoulder width = 1 unit):

```
husband.mp4  chunk 1  (the sign)    L wrist y 0.932 -> 0.558   ends  0.59 sw below the shoulders
husband.mp4  chunk 2  (the drop)    L wrist y 0.577 -> 0.922   ends  2.50 sw below, 320 ms
                                    R wrist y 0.534 -> 0.901   ends  2.39 sw below, 320 ms
```

So the second chunk is the signer lowering their arms: short, every hand downwards, ending where arms hang. Read
as a sign it becomes a phantom word after an answer — in the app's reading, in the transcript, and in the extra
"is this it?" the subject is asked.

`SignCapture.close()` now drops such a chunk (`droppingToRest`): at most 500 ms, every hand that is present at both
ends at least 0.6 shoulder widths lower, and ending at least 1.2 shoulder widths below the shoulder line. It needs
the pose to measure a shoulder, so a frame without one never drops a sign, and the older hand-only tests are
unchanged by construction. The vertical distances carry the same 1/aspect correction as `wristReach`: y is a share of
the frame's height, so without it a wide frame would drop a short real sign and a tall one would keep a drop
(`aLandscapeFrameIsMeasuredInItsOwnUnits` pins it).

| Check | Before | After |
| --- | --- | --- |
| `husband.mp4` on the AVD (real Holistic + bundled reader) | segments `[23, 8]` — the 8-frame drop read "light" | segments `[23]` |
| ISLRTC digits read as a single sign | 1 of 10 clips | **6 of 10 clips** |
| New unit tests | — | drop is not a sign; a short sign that ends at chest height is kept; without a pose nothing is dropped |

## 10.3 The numbers

**On the AVD, real chain over bundled dictionary clips** (one unseen signer, 25 fps, MediaPipe on CPU at 46 ms a
frame):

| Clip | Signs found | Read | True word in top-3 |
| --- | --- | --- | --- |
| student | 1 | `0` (0.28) | no |
| today | 1 | `key` (0.51), `today` second (0.08) | yes |
| husband | 1 | `husband` (0.14) | yes |

**On real dictionary video, shipped readers, through the app's cutting** (ISLRTC digits, one signer not in training):

| How it is read | top-1 | top-3 |
| --- | --- | --- |
| Cut the way the app cuts, gate included | 0.30 (3/10) | 0.60 (6/10) |
| Whole clip as one sign, same gate | **0.60** | **0.80** |
| `isl_spell` alone on the whole clip (EVAL.md) | 0.80 | 0.82 |

**Words, from EVAL.md, same shipped model** (`sign_reader`, 199 dictionary clips of an unseen signer): 0.286 top-1,
0.442 top-3. On INCLUDE's own test signers: 0.979 top-1. The gap between those two rows is the whole story: the
reader has learned the corpus's signers, not yet people in general.

## 10.4 So how confident am I

* **That a video is turned into tracked frames and cut into sign-sized chunks: very high.** Every clip above was
tracked end to end (pose in 100% of frames, hands where a PC run of the same model found them ±1 frame), and a sign
was found in all ten digit clips. This part is deterministic plumbing and is covered by the instrumented test
`SignPipelineDeviceTest` plus the new unit tests.
* **That a sign cut this way is the right chunk: moderate.** The drop-to-rest defect was found and fixed, but a
fluent signer's phrase is cut at every 300 ms pause, so a phrase becomes several signs, and a held sign becomes two
if the hand drifts. There is no sentence-level model, only isolated-sign classification.
* **That the word read back is correct: low for a new signer.** 0.29 top-1 for words and 0.60 for digits (whole
clip, gate included) means roughly one word in three lands first, and the top three are right about half the time.
In a clip of ten signs, a *transcript* would therefore contain several wrong words; anything that treats it as a
record would be wrong to. This is why the app's design is a draft the subject confirms by picking a picture, never
a transcript, and why `decideRead` ranks the item's own answer cards first.
* **For a known, cooperative signer inside a session, confidence is much higher** (0.98 top-1 on the corpus
distribution, 0.73 top-1 on an unseen signer when the answer set is the question's own cards): the constraint does
most of the work.

What would move the last two rows is data, not plumbing: more signers per word (one extra signer per word did not
help, ten might), denser 30 fps landmarks for the fast signs, and a CTC-style sequence model trained on continuous
signing instead of isolated clips. Naming the ceiling is more useful than pretending the pipeline is the problem.

*Sample size, stated plainly:* the digit rows are 10 clips from one signer; the word rows are 199 clips from one
signer (EVAL.md). These are enough to rank the failure modes and to refuse a "it works" claim, not enough to quote
a product-level accuracy.
