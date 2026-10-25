# Tools

Build and research scripts. None ships in the APK. Run them from the repository root; training and evaluation data stay outside the repository, under `$SIGNSETU_DATA` (default `ml-data`). Three Python environments are used: **tools** (resvg-py, Pillow, NumPy), **ml** (PyTorch, NumPy) and **mp** (MediaPipe, OpenCV, NumPy, pyarrow). Scripts that read untrusted downloads run with `python -I`.

## Content (`tools/content`, tools environment)

| Script | What it does | Run |
| --- | --- | --- |
| `build_content.py` | Renders picture cards and writes `manifest.json` (SHA-256 of every asset, JSON normalised to LF) | `build_content.py` (cards and manifest), `--manifest-only` after editing JSON or audio, `--only a,b` for some cards, `--check` exits 1 when the manifest is stale and writes nothing |
| `check_content.py` | Every card, sign, gesture and group a question, word or phrase names exists; ids are unique; every card has its WebP; every subject label has all 17 languages | `check_content.py`; exits 1 with the errors |
| `build_signs.py` | Writes `signs.json`: fingerspelling alphabet, Hindi akshara, digits and draft word signs | `build_signs.py && build_content.py --manifest-only` |
| `build_audio.py` | Placeholder speech with Indic Parler-TTS (Apache-2.0, offline, GPU environment); every file is a placeholder until a fluent speaker approves it | `build_audio.py [--force]`, then `build_content.py --manifest-only` |
| `coverage.py` | How many questions can be signed word for word; words without a sign go to `tools/ml/target_words.txt`. Its word lists mirror `TextSigning.kt` | `coverage.py`, or `--check` to fail above `MAX_GAPPED` questions with a gap |

## Recorded signs (`tools/isl`)

See [isl/README.md](isl/README.md): `retarget.py` (INCLUDE to `isl_signs.json`), `retarget_islrtc.py` (ISLRTC dictionary words, mp environment), `retarget_asl.py` (PopSign to `asl_signs.json` and `asl_words.json`).

## Sign readers (`tools/ml`)

See [ml/README.md](ml/README.md) for data, environments and the training order. Each script, with its arguments:

| Script | What it does | Run |
| --- | --- | --- |
| `prepare.py` | Downloads the INCLUDE pose release and caches normalised landmarks | `prepare.py [data_dir]` |
| `prepare_asl.py` | PopSign ASL v1.0 frames (huggingface `sign/popsign-images`, CC BY 4.0) through the app's Holistic model; train and validation signers train, test signers test; shards are deleted once read; missing signs are fetched from the game split by HTTP range (mp environment) | `prepare_asl.py [data_dir] [workers] [limit]` |
| `prepare_isl_real.py` | Real ISL letter and digit photographs (Hambir et al., doi:10.17632/2tj6c7pzfk.1, for training; ISLAN, doi:10.17632/rc349j45m5.1, test only; CC BY 4.0) through MediaPipe Hand Landmarker (mp environment) | `prepare_isl_real.py <photo dir> <hand_landmarker.task> <out.pkl>` |
| `prepare_islrtc.py` | Evaluation only: ISLRTC dictionary digits and fingerspelled acronyms through the app's Holistic model at 25 fps (mp environment) | `prepare_islrtc.py [data_dir]` |
| `synth_isl.py` | Synthetic ISL letters and digits posed from `signs.json` on a Python copy of the rig, jittered (no redistributable ISL letter set exists) | `synth_isl.py [out.pkl] [per_class]` |
| `mix_isl_spell.py` | Training cache for the ISL letter reader: real photographs plus synthetic poses in train, real held-out signers in test | `mix_isl_spell.py <isl_real.pkl> <synthetic.pkl> <out.pkl>` |
| `train.py` | Trains a format 1 reader and reports test accuracy; `SIGN_HIDDEN` and `SIGN_DROPOUT` set width and dropout (export needs the same width) | `train.py [cache.pkl] [out.pt] [epochs]` |
| `train_asl.py` | ASL reader trainer with GPU augmentation; teacher checkpoints add distillation | `train_asl.py [asl.pkl] [out.pt] [epochs] [teachers...]` |
| `spell_ctc.py` | ASL fingerspelling CTC reader from synthetic sequences of real ASLNow! letters (MIT) | `spell_ctc.py prepare [data_dir] [per_letter]`, `train [data_dir] [epochs]`, `export [data_dir]` |
| `export.py` | Exports a format 1 reader to the content bundle with its Kotlin parity fixture | `export.py [sign_reader or isl_spell or asl_reader] [checkpoint.pt] [cache.pkl]`, then `build_content.py --manifest-only` |
| `eval_isl.py` | Top-1 and top-3 of the shipped ISL readers, the sign-or-spell gate, a sentence demo through `glossToText`'s rules, and ISLRTC real signers | `eval_isl.py [include.pkl] [isl_spell.pkl] [islrtc.pkl]` |
| `eval_asl.py` | ASL word top-1 and top-3, fingerspelling character error rate (synthetic, so flattering) and the gate | `eval_asl.py [asl.pkl] [asl_spell_dir]` |
| `eval_signers.py` | Word-reader checkpoints on a held-out signer split | `eval_signers.py <cache.pkl> <split> <model.pt>...` |
| `eval_video.py` | Video clips through the app's whole chain: Holistic, the app's segmentation rules, the shipped NumPy reader. A clip's label comes from a `labels.json` beside it, else its file name (mp environment) | `eval_video.py <clip.mp4>...` or `--dir <folder>` |

`features.py` holds the feature extraction that `LandmarkSignReader.kt` and `CtcSpellReader.kt` must match exactly; `runtime.py` runs the exported weights in NumPy as the app does.

## Security (`tools/security`)

| Script | What it does | Run |
| --- | --- | --- |
| `sbom.py` | CycloneDX SBOM of the release runtime classpath, with Syft (Apache-2.0); written to `app/build/sbom/sign-setu-release.cdx.json` | `sbom.py path/to/syft` |
| `test_paths.py` | Fails when a tracked text file carries a developer's absolute home path | `python -m unittest discover -s tools/security -p 'test_*.py'` |
