# Reader evaluation

Output of `eval_isl.py` and `eval_asl.py` on the shipped models.

    ISL words    top-1 0.9779  top-3 0.9939  (n=816, sign_reader)
    ISL letters  top-1 0.9812  top-3 1.0000  (n=2600, isl_spell)
    ISL digits   top-1 1.0000  top-3 1.0000  (n=1000, isl_spell)
    ASL words    top-1 0.3912  top-3 0.5427  (n=3070, asl_reader)
    ASL spelling CER 0.0257  (400 sequences, asl_spell)

Notes:
- ISL letters and digits are trained and tested on synthetic landmarks posed from the app's keyframes, so the
  scores measure self-consistency, not real signers.
- ASL words: 216 of the 250 PopSign signs (the validation and test shards; the 14.5 GB train split is not
  downloaded). Test signers are not in training.
