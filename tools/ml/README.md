# Sign reader training

Trains the on-device sign readers: the isolated sign classifiers `LandmarkSignReader` runs (format 1) and the ASL
fingerspelling reader `CtcSpellReader` runs (format 2).

## Data

INCLUDE, the Indian Sign Language isolated-word dataset by AI4Bharat (Sridhar et al., "INCLUDE: A Large Scale
Dataset for Indian Sign Language Recognition", ACM Multimedia 2020), licensed CC BY 4.0. The scripts use the
OpenHands pose release (zenodo.org/records/6674324, CC BY 4.0), which already holds MediaPipe Holistic keypoints
for every video, so no video is downloaded or processed. The official train and test split ships with it.
Data stays outside the repository (default `$SIGNSETU_DATA/include_pose`).

## Environment

A Python 3.12 virtual environment with PyTorch (CUDA build) and NumPy:

    uv venv --python 3.12 signsetu-ml
    uv pip install --python signsetu-ml/Scripts/python.exe torch --index-url https://download.pytorch.org/whl/cu128
    uv pip install --python signsetu-ml/Scripts/python.exe numpy

## Steps

Run from `tools/ml`:

    python prepare.py   # download, unzip, cache normalised landmarks in include.pkl
    python train.py     # train on the GPU, print test-split top-1 and top-3, save sign_reader.pt
    python export.py    # write assets/content/models/sign_reader.{bin,json} and the parity fixture
    python ../content/build_content.py --manifest-only

The app loads the model only when both files match the content manifest. `SignReaderTest` checks that the
Kotlin forward pass reproduces the Python logits on the fixture.

## More readers

| Model | Format | Data | Licence |
|---|---|---|---|
| `sign_reader` | 1 | INCLUDE, 262 ISL words | CC BY 4.0 |
| `isl_spell` | 1 | ISL letters and digits: Multimodal ISL alphabet photographs (four signers) plus the app's keyframes (`signs.json`) posed on a copy of the rig, jittered; tested on ISLAN signers | CC BY 4.0, Project |
| `asl_reader` | 1 | PopSign ASL v1.0, 250 signs, frames from `sign/popsign-images`, landmarks by the app's Holistic model | CC BY 4.0 |
| `asl_spell` | 2 (CTC) | Synthetic sequences of real static ASLNow! letters (`synthetic` in its json) | MIT |

No redistributable ISL fingerspelling landmark set was found (public ones are CC BY-NC). Google's Kaggle ASL sets are
CC BY 4.0 but need Kaggle credentials; PopSign is the same isolated-sign corpus. `prepare_asl.py` needs a second venv
(`signsetu-mp`: mediapipe, opencv-python, pyarrow, pillow) and reads the PopSign train and validation signers for training and test signers for
testing (about 19 GB streamed, one 500 MB shard on disk at a time). The non-game split
holds 216 of the 250 signs; the rest, and test clips for signs its test signers lack, come from the game split by HTTP
range reads of only the row groups that hold them (`popsign_labels.json` lists the 250).

    python synth_isl.py                               # synthetic isl_spell.pkl
    signsetu-mp/python -I prepare_isl_real.py <isl_real dir> <hand_landmarker.task> <isl_real.pkl>   # photographs
    python mix_isl_spell.py <isl_real.pkl> <isl_spell.pkl> <out.pkl>   # real + synthetic cache for isl_spell
    python train.py <cache.pkl> <out.pt> [epochs]     # any format 1 reader
    python export.py <name> <out.pt> <cache.pkl>      # models/<name>.{bin,json} + <name>_fixture.json
    signsetu-mp/python -I prepare_asl.py              # asl.pkl
    python train_asl.py <asl.pkl> <out.pt> <epochs> [teacher.pt ...]   # asl_reader, augmentation on the GPU
    signsetu-mp/python -I prepare_islrtc.py           # islrtc.pkl: real ISL signers for eval_isl.py only
    python spell_ctc.py prepare | train | export      # asl_spell, format 2
    python eval_isl.py                                # top-1/top-3: ISL words, letters, digits; sentence demo
    python eval_asl.py                                # top-1/top-3: ASL words; fingerspelling CER
    python eval_signers.py <cache.pkl> <split> <model.pt>...   # checkpoints on held-out signers
    python ../isl/retarget_asl.py                     # asl_signs.json, asl_words.json

`runtime.py` runs the exported files in NumPy, so the evaluations score what ships. The latest scores and what they
mean are in [EVAL.md](EVAL.md); update it when a shipped model changes.

`eval_video.py` measures the whole chain on video files rather than on cached landmarks: it tracks each clip with the
bundled Holistic model, cuts it into signs with the app's own rules, featurizes each sign and reads it with the shipped
readers. A clip's expected label comes from a `labels.json` beside it; `--whole` reads the clip as one sign, which
separates the reader's accuracy from the app's cutting.

    signsetu-mp/python -s tools/ml/eval_video.py --dir <folder of .mp4> [--whole]

`train.py` reads `SIGN_HIDDEN` (width, default 160; `sign_reader` and `asl_reader` use 320), `SIGN_DROPOUT` and
`SIGN_AUG=strong` (moves and resizes each hand against the body). `export.py` needs the same `SIGN_HIDDEN` as training.
`train_asl.py` reads `SIGN_KD` (distillation weight) and `SIGN_SEED`.

Format 2 (`CtcSpellReader.kt`, `spell_features` in `features.py`): frames resampled to 15 a second by timestamp; the
hand seen more often signs, a left hand mirrored; 21 points from the wrist in wrist to middle-knuckle lengths, the
wrist's step since the last frame, a presence flag. Per-frame linear layer, four residual dilated convolutions, per-frame
logits over blank and a to z, greedy CTC decoding. `AslSpellTest` checks logits and letters against the fixture.

## Features

`features.py` is the specification; `LandmarkSignReader.kt` mirrors it. Per frame: both hands (21 points, x and y)
and the arm points of the pose (shoulders, elbows, wrists), with y scaled to the frame width, centred on the mean
shoulder midpoint and divided by the mean shoulder width of the segment. A missing hand is zeros with a presence
flag. Each segment is resampled to 32 frames by nearest index. Training augments with mirroring (which swaps the
hands), small rotation and anisotropic scale, point noise, and time cropping and resampling. `isl_spell` instead
centres on the hand points and scales by wrist to middle-knuckle length, with arm features zero (`norm: hands` in its
json, `featurize_hands`), so close-ups without shoulders read the same.

## In the app

A word reader's labels become answers through `assets/content/sign_words.json`: `words` maps each label to a written
form in `words.json` (and so to that word's picture card), and `cards` lists further cards a label answers (`child`
answers `children`). The reader scores every label; those whose cards answer the question on screen are ranked first,
each group by score, and none are dropped (`inContext` in `SignChoices.kt`). Every pairing is a draft until
ISL review. `SignReaders.kt` decides between a word read and a spelled one (the gate in EVAL.md).

## What to train next

`tools/content/coverage.py` measures how much of what the app asks can actually be signed: every question in
`triage.json` against the recorded signs (`isl_signs.json`) and the vocabulary (`words.json`). It writes
`target_words.txt`, the words without a sign, most used first — the shopping list for the next training run or
recording session. Run it before starting a run so the classes and the questions move together:

    python tools/content/coverage.py

A class can only be added by finding recordings of it. INCLUDE is fixed at 262 words, so a word outside it needs
either a new recorded source in the same landmark format (see `prepare_isl_real.py`) or a sign composed by hand in
`tools/content/build_signs.py`; both are drafts until a Deaf ISL reviewer signs them off. Retraining on the GPU after
either change is the same `prepare.py` → `train.py` → `export.py` sequence, with `SIGN_CLASSES` narrowed or widened to
the words actually shipped.

How the readers fit into the app is in [docs/11-hand-model-integration.md](../../docs/11-hand-model-integration.md).
