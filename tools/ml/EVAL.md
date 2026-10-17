# Reader evaluation

Output of `eval_isl.py` and `eval_asl.py` on the shipped models.

    ISL words    top-1 0.9779  top-3 0.9939  (n=816, sign_reader)
    ISL letters  top-1 0.9812  top-3 1.0000  (n=2600, isl_spell)
    ISL digits   top-1 1.0000  top-3 1.0000  (n=1000, isl_spell)
    ISL combined words   top-1 0.9596  word-only 0.9779  (n=816, gate costs 0.0184)
    ISL combined spelled read as spelling 0.9131, correctly 0.9019  (n=3600)
    ASL words    top-1 0.4020  top-3 0.5428  (n=5385, asl_reader)
    ASL spelling CER 0.0257  (400 sequences, asl_spell)
    ASL combined words   top-1 0.2782  word-only 0.2836  (n=5385, gate costs 0.0054)
    ASL combined spelled read as spelling 0.7325  (400 sequences)

Notes:
- Combined rows apply the app's sign-or-spell gate (`SignReaders.kt`): an ISL letter or digit wins only at twice the
  word reader's top score; an ASL spelling wins only with at least 3 letters, each at least 0.9 sure. Otherwise the
  word reader answers alone, so a weak read can still ask the subject to sign slower.
- ISL letters and digits are trained and tested on synthetic landmarks posed from the app's keyframes, so the
  scores measure self-consistency, not real signers.
- ASL words: all 250 PopSign signs, trained on the train and validation signers (43,928 clips with the game fill-in).
  Test signers are not in training. Tripling the data (validation only to the full 14.5 GB train split) and widening
  the net moved top-1 from 0.39 to 0.40, so the ceiling is the 5 fps, 256 px frames' landmarks across signers, not
  data volume; the 0.6 target needs better landmarks (Kaggle's 30 fps set, which needs credentials).
