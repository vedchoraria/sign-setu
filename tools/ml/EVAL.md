# Reader evaluation

Output of `eval_isl.py` and `eval_asl.py` on the shipped models.

    ISL words    top-1 0.9779  top-3 0.9939  (n=816, sign_reader)
    ISL letters  top-1 0.3538  top-3 0.4615  (n=65, isl_spell)
    ISL digits   top-1 0.5000  top-3 0.5227  (n=44, isl_spell)
    ISL combined words   top-1 0.9779  word-only 0.9779  (n=816, gate costs 0.0000)
    ISL combined spelled read as spelling 0.2477, correctly 0.1376  (n=109)
    ISL real letters  top-1 0.1304  top-3 0.3043  (n=23, ISLRTC signers, isl_spell)
    ISL real digits   top-1 0.8000  top-3 1.0000  (n=10, ISLRTC signers, isl_spell)
    ISL real gate odds 1.5: spelled 0.6061, correctly 0.2727  (n=33); INCLUDE words lost 0.0025
    ISL real gate odds 2.0: spelled 0.5152, correctly 0.2424  (n=33); INCLUDE words lost 0.0000
    ISL real gate odds 3.0: spelled 0.1818, correctly 0.0909  (n=33); INCLUDE words lost 0.0000
    ISL real gate odds 4.0: spelled 0.0606, correctly 0.0606  (n=33); INCLUDE words lost 0.0000
    ASL words    top-1 0.4020  top-3 0.5428  (n=5385, asl_reader)
    ASL spelling CER 0.0257  (400 sequences, asl_spell)
    ASL combined words   top-1 0.3935  word-only 0.4020  (n=5385, gate costs 0.0085)
    ASL combined spelled read as spelling 0.7325  (400 sequences)
    ASL gate peak 0.80: real PopSign words lost 0.0156, spelled sequences passed 0.7900
    ASL gate peak 0.90: real PopSign words lost 0.0085, spelled sequences passed 0.7325
    ASL gate peak 0.95: real PopSign words lost 0.0059, spelled sequences passed 0.6925

Notes:
- Combined rows apply the app's sign-or-spell gate (`SignReaders.kt`): an ISL letter or digit wins only at twice
  the word reader's top score; an ASL spelling wins only with at least 3 letters, each at least 0.9 sure. Otherwise the
  word reader answers alone, so a weak read can still ask the subject to sign slower.
- ISL letters and digits are trained on CC BY photographs of four signers (Multimodal ISL alphabet dataset) plus
  synthetic landmarks posed from the app's keyframes, with hand-normalised features. The letters and digits rows are
  ISLAN signers held out of training; the real rows read ISLRTC dictionary signers (`prepare_islrtc.py`, evaluation
  only): the 0 to 9 clips, and the letters of 7 acronyms the dictionary fingerspells, each take split evenly among its
  letters. Synthetic-only training scored 0.04 and 0.10 there; this model scores 0.13 and 0.80.
- `SPELL_ODDS` went from 3 to 2 with the real-trained reader: correct spelled reads of real signers rise from 0.09 to
  0.24 and no INCLUDE word is lost at either value.
- `SPELL_MIN_PEAK` stays 0.9: 0.8 doubles the real PopSign words lost (0.0156, near the 0.02 limit) for 6 points of
  synthetic spelling. ASL spelling has no real-signer error rate: FSboard and Google's ASL Fingerspelling are CC BY
  4.0 but reachable only with Kaggle credentials.
- ASL words: all 250 PopSign signs, trained on the train and validation signers (43,928 clips with the game fill-in);
  test signers are not in training. 0.40 is the release-1 ceiling on these landmarks. Round 4 (`train_asl.py`, 200
  epochs each): width 320 gave 0.3957, with mixup 0.3970, width 1024 0.3987, and the three as an ensemble 0.4046, so
  no distilled student of this format beats the shipped 0.4020. The clips hold about 5 source frames (median 13 at
  15 fps after interpolation) with a hand found in 16% (left) and 41% (right) of frames; 0.6 needs denser landmarks
  (Kaggle `asl-signs` at 30 fps, credentials) or a new reader format; neither is in release 1.
