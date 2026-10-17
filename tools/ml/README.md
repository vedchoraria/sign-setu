# Sign reader training

Trains the on-device sign readers: the isolated sign classifiers `LandmarkSignReader` runs (format 1) and the ASL
fingerspelling reader `CtcSpellReader` runs (format 2).

## Data

INCLUDE, the Indian Sign Language isolated-word dataset by AI4Bharat (Sridhar et al., "INCLUDE: A Large Scale
Dataset for Indian Sign Language Recognition", ACM Multimedia 2020), licensed CC BY 4.0. The scripts use the
OpenHands pose release (zenodo.org/records/6674324, CC BY 4.0), which already holds MediaPipe Holistic keypoints
for every video, so no video is downloaded or processed. The official train and test split ships with it.
Data stays outside the repository (default `C:/Users/Yash/dev/ml-data/include_pose`).

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
| `isl_spell` | 1 | Synthetic: the app's ISL letter and digit keyframes (`signs.json`) posed on a copy of the rig, jittered (`synthetic` in its json) | Project |
| `asl_reader` | 1 | PopSign ASL v1.0, 250 signs, frames from `sign/popsign-images`, landmarks by the app's Holistic model | CC BY 4.0 |
| `asl_spell` | 2 (CTC) | Synthetic sequences of real static ASLNow! letters (`synthetic` in its json) | MIT |

No redistributable ISL fingerspelling landmark set was found (public ones are CC BY-NC). Google's Kaggle ASL sets are
CC BY 4.0 but need Kaggle credentials; PopSign is the same isolated-sign corpus. `prepare_asl.py` needs a second venv
(`signsetu-mp`: mediapipe, pyarrow, pillow) and reads the PopSign train and validation signers for training and test signers for
testing (about 19 GB streamed, one 500 MB shard on disk at a time). The non-game split
holds 216 of the 250 signs; the rest, and test clips for signs its test signers lack, come from the game split by HTTP
range reads of only the row groups that hold them (`popsign_labels.json` lists the 250).

    python synth_isl.py                               # isl_spell.pkl
    python train.py <cache.pkl> <out.pt> [epochs]     # any format 1 reader
    python export.py <name> <out.pt> <cache.pkl>      # models/<name>.{bin,json} + <name>_fixture.json
    signsetu-mp/python -I prepare_asl.py              # asl.pkl
    python train_asl.py <asl.pkl> <out.pt> <epochs> [teacher.pt ...]   # asl_reader, augmentation on the GPU
    signsetu-mp/python -I prepare_islrtc.py           # islrtc.pkl: real ISL signers for eval_isl.py only
    python spell_ctc.py prepare | train | export      # asl_spell, format 2
    python eval_isl.py                                # top-1/top-3: ISL words, letters, digits; sentence demo
    python eval_asl.py                                # top-1/top-3: ASL words; fingerspelling CER
    python ../isl/retarget_asl.py                     # asl_signs.json, asl_words.json

`runtime.py` runs the exported files in NumPy, so the evaluations score what ships.

Format 2 (`CtcSpellReader.kt`, `spell_features` in `features.py`): frames resampled to 15 a second by timestamp; the
hand seen more often signs, a left hand mirrored; 21 points from the wrist in wrist to middle-knuckle lengths, the
wrist's step since the last frame, a presence flag. Per-frame linear layer, four residual dilated convolutions, per-frame
logits over blank and a to z, greedy CTC decoding. `AslSpellTest` checks logits and letters against the fixture.

## Features

`features.py` is the specification; `LandmarkSignReader.kt` mirrors it. Per frame: both hands (21 points, x and y)
and the arm points of the pose (shoulders, elbows, wrists), with y scaled to the frame width, centred on the mean
shoulder midpoint and divided by the mean shoulder width of the segment. A missing hand is zeros with a presence
flag. Each segment is resampled to 32 frames by nearest index. Training augments with mirroring (which swaps the
hands), small rotation and anisotropic scale, point noise, and time cropping and resampling.
