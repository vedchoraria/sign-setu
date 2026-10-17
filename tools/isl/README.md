# Recorded ISL signs

`retarget.py` turns the INCLUDE signers' MediaPipe Holistic landmarks (AI4Bharat, Sridhar et al. 2020, OpenHands
pose release, CC BY 4.0) into `app/src/main/assets/content/isl_signs.json`, one sign per word, ids `isl.<word>`.
Every sign is a draft until Deaf review.

    C:/Users/Yash/dev/venvs/signsetu-ml/Scripts/python tools/isl/retarget.py   # data in C:/Users/Yash/dev/ml-data/include_pose
    C:/Users/Yash/dev/venvs/signsetu-ml/Scripts/python tools/isl/test_retarget.py
    python tools/content/build_content.py --manifest-only

`retarget_asl.py` does the same for ASL into `asl_signs.json` (ids `asl.<word>`, PopSign ASL v1.0, CC BY 4.0, from
the cache `tools/ml/prepare_asl.py` builds) and adds the one-handed ASL alphabet (`asl.letter.<c>`) from ASLNow! static
hands (MIT). It also writes `asl_words.json`, the ASL reader's labels that are words the app knows.

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

    C:/Users/Yash/dev/venvs/signsetu-mp/Scripts/python -I tools/ml/prepare_asl.py   # landmarks in C:/Users/Yash/dev/ml-data/popsign/landmarks
    C:/Users/Yash/dev/venvs/signsetu-mp/Scripts/python tools/isl/retarget_asl.py
    python tools/content/build_content.py --manifest-only
