# Recorded ISL signs

`retarget.py` turns the INCLUDE signers' MediaPipe Holistic landmarks (AI4Bharat, Sridhar et al. 2020, OpenHands
pose release, CC BY 4.0) into `app/src/main/assets/content/isl_signs.json`, one sign per word, ids `isl.<word>`.
Every sign is a draft until Deaf review. Run from the repository root; data stays outside the repository, and each
script takes its data folder as an argument (the defaults below are one developer's paths).

| Script | Input | Writes |
| --- | --- | --- |
| `retarget.py [data_dir]` | INCLUDE pose release | `isl_signs.json` (keeps ISLRTC entries) |
| `retarget_islrtc.py <targets.json> [data_dir]` | ISLRTC dictionary clips | ISLRTC entries in `isl_signs.json` |
| `retarget_asl.py` | PopSign landmark cache | `asl_signs.json`, `asl_words.json` |
| `test_retarget.py` | Synthetic landmarks | Unit tests of the retarget maths |

After any of them, refresh the content manifest with `python tools/content/build_content.py --manifest-only`.

    python tools/isl/retarget.py   # data in $SIGNSETU_DATA/include_pose
    python tools/isl/test_retarget.py
    python tools/content/build_content.py --manifest-only

`retarget_asl.py` does the same for ASL into `asl_signs.json` (ids `asl.<word>`, PopSign ASL v1.0, CC BY 4.0, from
the cache `tools/ml/prepare_asl.py` builds) and adds the one-handed ASL alphabet (`asl.letter.<c>`) from ASLNow! static
hands (MIT). It also writes `asl_words.json`, the ASL reader's labels that are words the app knows.

## ISLRTC words

`retarget_islrtc.py` adds words the app could otherwise only fingerspell, from the ISLRTC Indian Sign Language
Dictionary (Government of India, Government Open Data License - India; clips from
huggingface.co/datasets/silentone0725/Indian_Sign_Language_Data.gov_Rencoded). It runs the app's own Holistic model
(`models/holistic_landmarker.task`) over each clip, so it needs the MediaPipe venv (`signsetu-mp`: mediapipe, OpenCV,
NumPy). Only keyframes on the character ship, never video.

`targets.json` maps each word (as written in `words.json`) to a clip path inside that dataset, for example
`{"address": "A/Address.mp4"}`. Clips are downloaded into `<data_dir>/words` and their landmarks cached beside them.
Words INCLUDE already signs are skipped; a clip with too little pose, or signing shorter than 0.3 s or longer than
6 s, is skipped and the word stays spelled. Entries are written with `rec: "ISLRTC/<clip>"` and category `ISLRTC`.
Each run replaces every ISLRTC entry, so `targets.json` (kept outside the repository) must list all of them.

    python -I tools/isl/retarget_islrtc.py targets.json   # clips in $SIGNSETU_DATA/islrtc
    python tools/content/build_content.py --manifest-only

`SignPipelineDeviceTest` plays three of these clips (`app/src/androidTest/assets/islrtc/`) through the app's camera
pipeline and compares the frames with a hand against what this script found on a PC.

## Method

- Per word, the recording with the most frames where every signing hand is seen, at a typical length.
- The release stores pixels as if the 1920x1080 landscape videos were portrait; the script undoes that stretch.
- Gaps are filled linearly and the tracks smoothed (Gaussian, sigma 1.2 frames). Frames before the hands rise and
  after they drop are cut, since the rig eases in from its own rest.
- Wrists are placed on the adult rig by the shoulders (body) or the eyes and mouth (near the face), blended, then
  written as the nearest anchor plus dx/dy, kept within reach and far enough from the frame edges that the whole hand stays in the body view.
- Hands: per-finger curl from joint flexion, thumb across the palm, spread, palm roll from the palm normal, angle
  from wrist to middle knuckle, and the elbow as `ex`/`ey` from the shoulder, the longest upper arm scaled to the rig's so shorter ones foreshorten (`elbow: down` is the fallback). Shapes are written as `c:` percentages (`MeasuredShape.kt`, `handShape` in rig.ts).
- Keys at both ends, pauses and turns of the path, at least every 0.5 s.
- `HOMONYMS` (fall, light, ...) stay in the dictionary but are never used for typed text.

## Checks

- Side-by-side renders of the source skeleton and both characters (portal rig and drawing, headless Chromium) for
  thank you, house, you, teacher, family, mother, father, school, hello and how are you: placement, hands used and
  handshapes follow the signer. Fingers pointing at the camera read as bent, a limit of the flat rig.
- `IslLexiconTest` checks every motion stays in the frame on both characters and that authored signs keep each hand on
  its own side, and that every reader label can be signed back.

## Recorded ASL signs

`retarget_asl.py` does the same for ASL: PopSign ASL v1.0 (Georgia Tech and NTID, the 250 signs of Google's Isolated
Sign Language Recognition, CC BY 4.0), frames from huggingface.co/datasets/sign/popsign-images, landmarked with the
app's MediaPipe Holistic model by `tools/ml/prepare_asl.py` and retargeted with `retarget.py`'s maths into
`asl_signs.json`, ids `asl.<gloss>`. Recordings led by the left hand are mirrored so the rig's right hand leads. Most
PopSign signers hold the phone in one hand, so a two-handed gloss (`TWO_HANDED`) takes a clip where both hands sign;
where no clip has one it is drawn with the right hand alone and marked `one_hand_source`. The one-hand ASL alphabet is
authored in the script as keyframes (`asl.letter.a` to `asl.letter.z`). `asl_words.json` pairs reader labels with
words.json.

    python -I tools/ml/prepare_asl.py   # landmarks in $SIGNSETU_DATA/popsign/landmarks
    python tools/isl/retarget_asl.py
    python tools/content/build_content.py --manifest-only

The rig, the lexicons and how signs are validated are described in
[docs/05-avatar-and-gestures.md](../../docs/05-avatar-and-gestures.md).
