# 11. Hand Model Integration

The built vision engine and sign reader, and the contract by which the partner team's model can replace either of them. Landmarks stay on the phone and in memory only; any meaning a model suggests is a proposal until the subject picks it.

## 1. What is built

| Layer | Interface | Implementation | Replaceable by the partner team |
| --- | --- | --- | --- |
| Landmark tracking | `camera/LandmarkTracker` | `HolisticTracker`: MediaPipe Holistic, `tasks-vision` 1.1.1, video mode, GPU delegate kept only if it finds the person in a bundled probe image, otherwise CPU | Yes, a tracker with the same output |
| Segmentation | `sign/SignCapture`, `segmentSigns` | Pause segmenter, thresholds per `SignWindow` | No; tune the constants |
| Sign reading | `sign/SignReader` | `LandmarkSignReader` (262 ISL words, 250 ASL words, ISL letters and digits) and `CtcSpellReader` (ASL fingerspelling), combined per language by `SignReaders` | Yes, the main replacement path |
| Context and choices | `sign/ReadPolicy`, `sign/SignChoices` | `sign_words.json` map, context ranking (`decideRead`, `inContext`), alternatives (`signChoices`), slower cue (`needsSlower`) | No; the map is content |

## 2. Sign windows

| Window | Opened by | Limit | Pause that ends a sign | Face points |
| --- | --- | --- | --- | --- |
| `ANSWER` | Raised hands on the call stage, the camera button (stage), Read signs (officer) | 20 s, or 2 s of quiet hands, or 3 s with no movement | 0.4 s | Kept in memory |
| `NARRATIVE` | Tell me (More page) | 3 minutes, or 5 s of quiet hands | 0.7 s | Dropped |

Outside a window frames still reach the tracker while the stage camera shows, for the skeleton overlay and the nod, shake, point, palm, shrug and courtesy proposals, but no landmark is kept. A chunk shorter than 0.2 s, or with fewer than 3 moving frames, is dropped. Hands count as still when the mean speed of all their points is below 0.3 frame widths per second, so fingerspelling with steady wrists is not cut.

## 3. Landmark tracker

```kotlin
interface LandmarkTracker : AutoCloseable {
    fun track(frame: Bitmap, timestampMs: Long): LandmarkFrame?
    fun warm() {}
    override fun close() {}
}

data class LandmarkFrame(
    val timestampMs: Long,
    val leftHand: List<Offset>? = null,  // 21 points, the subject's own left hand
    val rightHand: List<Offset>? = null, // 21 points
    val face: List<Offset>? = null,      // 468 points
    val pose: List<Offset>? = null,      // 33 points
    val aspect: Float = 3f / 4f,         // upright frame width / height; CameraMirror sets the real value
    // Marked by FramePipeline after tracking: browsUp, mouthOpen, head (nod or shake), pointsAt,
    // stopPalm, shrug, everyday (namaste, wave, thanks, bow), blurry.
)
```

| Property | Value |
| --- | --- |
| Frames | One at a time, newest only (CameraX `STRATEGY_KEEP_ONLY_LATEST`); at most one per 30 ms inside a sign window, one per 33 ms with a person in view, one per 250 ms after 10 s with nobody |
| Thread | One background analysis thread |
| Pixels | RGBA, 16:9 at about 848 by 480 (480 by 848 upright), rotated upright by CameraX into its reused buffer, not mirrored; the preview uses the same 16:9 shape so the skeleton lines up |
| Lifetime | The buffer is reused after `track` returns; an implementation never keeps or copies it |
| Coordinates | Normalised to the upright frame, origin top-left, not mirrored; the stage mirrors for display |
| Sides | Hands are the subject's own left and right (Holistic assigns them from the pose wrists) |
| Errors | `track` returns null for a frame it cannot read; it never throws for a bad frame |
| Start-up | `warm` loads the model off the main thread when the session screen opens, before the first frame; never during app start |
| Delegate | GPU first; if the GPU fails to open, or the first inference throws, the graph reopens on the CPU on the next frame. `close` frees the graph on lock and on memory pressure, and the next use opens it again |

The holistic model (`models/holistic_landmarker.task`, 13.7 MB, float16, SHA-256 `e2dab61191e2dcd0a15f943d8e3ed1dce13c82dfa597b9dd39f562975a50c3f8`) is mapped from the uncompressed asset, checked against the content manifest and opened only if it matches. Release APKs are 64-bit only (arm64-v8a, and x86_64 in the universal build), so every installed device can run the tracker. The stage draws hands, arms, brows and lips, never the face oval or eyes.

## 4. Sign reader

```kotlin
interface SignReader : AutoCloseable {
    val labels: List<String>                      // the vocabulary, lower-case English
    val words: Map<String, String>                // labels to the app's vocabulary (sign_words.json)
    val cards: Map<String, List<String>>          // labels to further picture cards they answer
    fun read(frames: List<LandmarkFrame>): List<SignGuess> // every label, highest first
}

data class SignGuess(val word: String, val score: Float, val spelled: Boolean = false)
```

The built reader takes the frames of one chunk, resampled to 32, with per frame both hands (21 points each), shoulders, elbows and wrists, normalised to the mean shoulder midpoint and width, and presence flags (98 features). It runs a per-frame linear layer, two 1-D convolutions and pooling to 262 logits. Weights: `models/sign_reader.bin` (4.9 MB, float32) with its spec in `sign_reader.json`; both are checked against the manifest by `SignReaders` before use. On the INCLUDE test split it scores top-1 97.8% and top-3 99.4%; with children on phone cameras it is unmeasured. One read takes about 18 ms on a desktop JVM. Reads run one at a time on a background dispatcher.

Retraining: `tools/ml` (prepare, train, export), then `python tools/content/build_content.py --manifest-only`.

## 5. How readings are used

1. While a question is on screen, guesses whose mapped concept is one of that question's answer cards rank first and the others follow, each group by score. The top three are kept.
2. A best guess below 0.4 with lost tracking (over 30% of the chunk untracked, or gaps over 150 ms) or fast hands (wrist over 2.5 frame widths per second) shows the subject "A little slower, please".
3. On Done the words are logged as the app's reading (source MACHINE, "read by the app, not checked"). The officer may log their own reading beside it.
4. On this phone's stage the subject is asked straight away to pick what each sign meant: two or three picture cards per sign and "None of these", at most three sets. With a second screen the officer taps Ask them to choose. Only the subject's tap records a meaning.
5. The reader's words are never shown to the subject (the stage shows one dot per sign) and never shown on the officer glance strip. A stage device reads signs itself and sends only the words, one per sign, logged on the officer phone as the app's reading.

Core answer words that INCLUDE does not cover (yes, no, water, pain, help, stop, don't know) cannot be read; mother, father, police, home (house) and toilet (bathroom) are covered.

## 6. Replacing the reader or tracker

1. Implement `SignReader` (or `LandmarkTracker`) in plain Kotlin or with LiteRT `CompiledModel` (`com.google.ai.edge.litert`), GPU first with CPU fallback; the legacy `org.tensorflow.lite` artifacts are not used.
2. Ship the model under `app/src/main/assets/content/models/`, list it with `python tools/content/build_content.py --manifest-only`, and load it only when its hash matches.
3. Return it from `SignReaders.create` (or `LandmarkTrackers.create` in the release source set). The app creates it once, off the main thread.
4. Provide the label list and an updated `sign_words.json` so context limiting and picture choices keep working.
5. Native libraries are aligned to 16 KB pages; no new permission (the build gate refuses RECORD_AUDIO, background location, and in `standard` every discovery and location permission); every new artifact is added to `gradle/verification-metadata.xml` and `app/gradle.lockfile`, scans clean with OSV for anything that ships, and is listed in [open source](09-open-source.md) with its licence and attribution.

## 7. Budgets

| Resource | Budget | Current |
| --- | --- | --- |
| Time per tracked frame | 33 ms on the oldest supported 64-bit phone | About 110 ms on the CPU of a mid-range phone (9 frames per second); the GPU rate is to be measured |
| Time per read | Under 100 ms | About 18 ms on a desktop JVM |
| Memory | Session under 300 MB | Narrative landmarks about 20 MB for 3 minutes; model mapped, not copied |
| APK | Distribution target under 100 MB | The arm64-v8a phone APK meets the target (82.7 MB); the multi-architecture universal APK is unconstrained |

## 8. Privacy and safety

- Frames and landmarks are never stored, logged or sent anywhere; landmarks stay in view-model memory for the session and are wiped on end, lock and clear. The one exception is a one-frame skeleton drawing kept with a signed answer when the officer turns evidence mode on for the session.
- MediaPipe's usage statistics are dropped on the device.
- The partner team holds the training data, its consent records and the model card for any replacement, and supplies them for the DPIA and the audit.

## 9. Acceptance

| Check | Pass mark |
| --- | --- |
| Sign window and reader tests with the delivered model | All pass; overlay and dots on the oldest supported 64-bit phone |
| Reference outputs supplied by the partner team | Every landmark within 0.02, or every logit within 1e-3, of the reference |
| Memory | Under 300 MB during a 3-minute narrative |
| Show latency and smoothness | Unchanged |
| Security checks | Manifest gate passes; no debug code in release; OSV clean; tampered model refused |
