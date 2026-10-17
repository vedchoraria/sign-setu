# Machine learning: sign readers, tracking and retargeting

## 1. Components

| Component | Code | Model asset | Runtime |
| --- | --- | --- | --- |
| Landmark tracker | `app/src/main/java/app/signsetu/camera/HolisticTracker.kt` | `assets/content/models/holistic_landmarker.task` | MediaPipe Tasks Vision `HolisticLandmarker`, video mode |
| Debug stand-in tracker | `app/src/debug/java/app/signsetu/camera/LandmarkTrackers.kt` | none | Enabled by `adb shell run-as in.signsetu touch files/demo_hands` |
| Sign reader (ISL words) | `app/src/main/java/app/signsetu/sign/LandmarkSignReader.kt` | `models/sign_reader.{bin,json}` | Plain Kotlin forward pass, no inference library |
| ISL letter and digit reader | `LandmarkSignReader.kt` (same class) | `models/isl_spell.{bin,json}` | Format 1, 36 classes; trained on synthetic landmarks (`"synthetic": true`) |
| ASL word reader | `LandmarkSignReader.kt` | `models/asl_reader.{bin,json}` | Format 1, 250 classes, hidden width 320 |
| ASL fingerspelling reader | `sign/CtcSpellReader.kt` | `models/asl_spell.{bin,json}` | Format 2 (CTC), greedy decoding; trained on synthetic sequences (`"synthetic": true`) |
| Reader selection and gate | `sign/SignReaders.kt` (`SignReaders.load`, `CombinedReader`) | all of the above | One reader per session sign language; a missing or damaged file leaves a word-only reader, or none, and never crashes |
| Score floor | `app/src/main/java/app/signsetu/sign/SignChoices.kt` (`READ_MIN_SCORE = 0.4`, `LOST_SHARE = 0.3`) | | |
| Training | `tools/ml/{prepare,features,train,export}.py`; `synth_isl.py`, `prepare_asl.py`, `spell_ctc.py` for the added readers | | PyTorch (CUDA), NumPy, Python 3.12 |
| Evaluation | `tools/ml/eval_isl.py`, `tools/ml/eval_asl.py`; results in `tools/ml/EVAL.md` | | `runtime.py` runs the exported files in NumPy, so the scores are those of the shipped weights |
| Retarget (recorded ISL signs) | `tools/isl/retarget.py`, `tools/isl/test_retarget.py` | `assets/content/isl_signs.json` | NumPy |
| Retarget (ASL signs) | `tools/isl/retarget_asl.py` | `assets/content/asl_signs.json`, `asl_words.json` | NumPy; 250 words and 26 letters |

## 2. Dataset

| Item | Value |
| --- | --- |
| Source | INCLUDE (AI4Bharat, Sridhar et al., ACM MM 2020), OpenHands pose release, zenodo.org/records/6674324 |
| Licence | CC BY 4.0 (attribution in `docs/09-open-source.md`) |
| Form | MediaPipe Holistic keypoints per video; no video processed |
| Classes | 262 isolated ISL words |
| Split | Official train/test split shipped with the release |
| Location | Outside the repository, default `C:/Users/Yash/dev/ml-data/include_pose` |

## 3. Features

`tools/ml/features.py` is the specification; `LandmarkSignReader.features` mirrors it.

| Step | Detail |
| --- | --- |
| Points per frame | Left hand 21 x (x,y), right hand 21 x (x,y), pose 11..16 (shoulders, elbows, wrists) x (x,y), 2 presence flags: `FEATURES = 98` |
| Aspect | y scaled by height/width (`ky = 1 / aspect`) |
| Centre | Mean shoulder midpoint (pose 11, 12) over the segment |
| Scale | Mean shoulder width over the segment |
| Missing part | Zeros plus presence flag 0; a segment with no shoulders returns null logits |
| Resample | 32 frames, nearest index `(i*(n-1)*2 + frames-1) div (2*(frames-1))` |
| Augmentation (training only) | Time crop up to 15 % per end, 0.8 to 1.2 resample; mirror with hand and arm swap (p = 0.5); rotation +/-0.2 rad; anisotropic scale 0.9 to 1.1; point noise sigma 0.003 |

## 4. Architecture (`SignNet` in `tools/ml/train.py`)

| Layer | Shape |
| --- | --- |
| Input | (B, 32, 98) |
| Per-frame Linear + ReLU | 98 -> 160 |
| Conv1d k=5 same padding + BatchNorm + ReLU | 160 -> 160 |
| Conv1d k=5 same padding + BatchNorm + ReLU | 160 -> 160 |
| Global average pool concatenated with max pool | 320 |
| Dropout 0.4 (training), Linear | 320 -> 262 logits |
| In app | Softmax; guesses above `READ_MIN_SCORE` |

Export (`tools/ml/export.py`): batch norm folded into conv weights; tensors float32 little-endian, row-major, in order embed.w, embed.b, conv1.w, conv1.b, conv2.w, conv2.b, head.w, head.b; conv weight layout `[out][in][kernel]`. `sign_reader.json` holds dimensions, labels, feature spec, dataset and test metrics. The parity fixture is `app/src/test/resources/sign_reader_fixture.json`.

## 5. Metrics (test splits, from each model json and `tools/ml/EVAL.md`)

| Reader | Top-1 | Top-3 | Notes |
| --- | --- | --- | --- |
| `sign_reader` (ISL words, n = 816) | 0.9779 | 0.9939 | Official INCLUDE test split; `SignReaderTest` reproduces the Python logits on the fixture |
| `isl_spell` letters (n = 2600) | 0.9812 | 1.0000 | Synthetic landmarks posed from the app's keyframes: measures self-consistency, not real signers |
| `isl_spell` digits (n = 1000) | 1.0000 | 1.0000 | As above |
| `asl_reader` (ASL words, n = 5385) | 0.4020 | 0.5428 | PopSign test signers, none in training; the acceptance target of 0.6 is not met (see section 9) |
| `asl_spell` | character error rate 0.0257 | | 400 synthetic sequences from ASLNow! static letters |

Combined readers apply the sign-or-spell gate of `SignReaders.kt`: an ISL letter or digit wins only at twice the word reader's top score; an ASL spelling wins only with at least three letters, each at least 0.9 sure. Gated top-1 is 0.9596 for ISL words and 0.2782 for ASL words, against 0.9779 and 0.2836 word-only.

Spelled guesses are marked `spelled` and the gloss text renders them as letters, so a fingerspelled name is not read as a word (`SignLanguageTest`).

## 6. Training commands (run from `tools/ml`; the added readers are in `tools/ml/README.md`)

```
uv venv --python 3.12 signsetu-ml
uv pip install --python signsetu-ml/Scripts/python.exe torch --index-url https://download.pytorch.org/whl/cu128
uv pip install --python signsetu-ml/Scripts/python.exe numpy
python prepare.py        # cache normalised landmarks in include.pkl
python train.py [data]   # 400 epochs by default; prints top-1 and top-3; writes sign_reader.pt
python export.py         # sign_reader.{bin,json} and the parity fixture
python ../content/build_content.py --manifest-only
```

The app loads the model only when both files match `assets/content/manifest.json` (SEC-74).

## 7. MediaPipe Holistic

| Aspect | Behaviour |
| --- | --- |
| Gate | `holisticTracker()` returns null unless the device has a 64-bit ABI and the `.task` asset exists; the stage then keeps a plain mirror |
| Integrity | `verifiedModel()` maps the uncompressed asset (`noCompress += "task"`) and compares its SHA-256 with the manifest |
| Delegate | GPU first; if opening fails, CPU. If the GPU opens but a frame throws, the delegate flips to CPU and the next frame reopens; a CPU failure rethrows |
| Throughput | CPU about 9 fps on a mid-range phone (comment in `HolisticTracker.kt`); frames are skipped while a frame is in analysis; the frame rate is logged in debug builds only |
| Protobuf | `protobuf-javalite` excluded; full `com.google.protobuf:protobuf-java` 4.26.1 added, because MediaPipe classes are compiled against full protobuf (`Any.Builder.build()`) |
| Telemetry | `transport-backend-cct` excluded; stub in `app/src/main/java/com/google/android/datatransport/cct/CCTDestination.kt` (SEC-72) |
| ABI | armeabi-v7a `libmediapipe_tasks_jni.so` excluded from packaging; 32-bit phones get the mirror only (D-25) |
| Data handling | Frames analysed only inside a sign window; landmarks in view-model memory only (SEC-47, SEC-71) |

## 8. Retarget pipeline (`tools/isl/retarget.py`)

| Stage | Functions | Detail |
| --- | --- | --- |
| Load | `load`, `label` | INCLUDE pose files; undoes the portrait stretch of the 1920x1080 landscape source |
| Pick | `used_hands`, `score`, `pick` | Recording with the most frames where every signing hand is seen, at a typical length |
| Clean | `fill_gaps`, `smooth` | Linear gap fill; Gaussian sigma 1.2 frames; frames before the hands rise and after they drop are cut |
| Place | `body_frame`, `face_frame`, `to_rig`, `place`, `clamp_reach` | Wrist by shoulders (body) or eyes and mouth (face), blended; nearest anchor plus dx/dy; kept within reach and frame margin |
| Hand | `finger_curl`, `thumb`, `spread`, `roll`, `angle`, `shape_code` | Per-finger curl, thumb across palm, spread, palm roll, wrist-to-middle-knuckle angle; `c:` percentages (`MeasuredShape.kt`, `handShape` in `portal/src/rig.ts`) |
| Elbow | `elbow_offsets` | `ex`/`ey` from the shoulder, longest upper arm scaled to the rig; fallback `elbow: down` |
| Keys | `key_frames` | Both ends, pauses, turns over 60 degrees; gap at most 0.5 s, at least 0.2 s |
| Output | `main` | `isl_signs.json`, ids `isl.<word>`, 262 signs, drafts until Deaf review; `HOMONYMS` kept for the dictionary only |

Commands: `tools/isl/README.md` and [ops.md](ops.md). Checks: `test_retarget.py`; `IslLexiconTest` (in frame on both characters, hands on their own side, every reader label can be signed back).

## 9. Known limits

| Limit | Effect |
| --- | --- |
| Isolated words, 262 ISL and 250 ASL words | Continuous signing is split at pauses and read per segment (top 3 each, then a gloss sequence the subject confirms word by word); out-of-vocabulary signs map to the nearest class |
| ISL letters and digits are synthetic | `isl_spell` has never seen a real signer; no redistributable ISL fingerspelling landmark set was found |
| ASL words below target | Top-1 0.402 against the 0.6 target; the limit is the 5 fps, 256 px PopSign frames' landmarks across signers, not data volume |
| ASL alphabet is static | `asl_spell` is trained on static ASLNow! hands chained into sequences; no real fingerspelling motion was seen |
| One dataset, few signers | Accuracy on officers' phones, lighting and child signers unmeasured; test metric is in-distribution |
| 2D features (x, y) only | Depth-ambiguous signs; fingers pointing at the camera read as bent on the flat rig |
| No face features | Non-manual markers ignored by design (SEC-71) |
| Shoulders required | No logits when the pose shoulders are absent |
| CPU fallback about 9 fps | Fast signs may be undersampled |
| 64-bit only | 32-bit phones have no tracking or reader |
| Readings are not facts | Shown to the officer as graded guesses; the subject picks among alternatives (T-30, FR-66, FR-86) |
| Retargeted signs are drafts | Deaf review needed before release (T-31, D-39); 14 moving ASL signs are retargeted to static keys |
