# What the old `police-assist` checkout held

`C:\Users\Yash\OneDrive\Desktop\police-assist` (4.8 GB) has been deleted. This file records
what was in it, why nothing was carried over, and how to get any of it back.

## Provenance

- HEAD was `3210cb1ff5bddf0a53b11847e9de047b2a8f6c9c` — "chore(release): 0.5.1 with a
  search-only question list".
- That commit is in this repo, and the tree hash matches on both sides:
  `8e809fef20ce85cb69612706e8f5e973f9bd277f`. A worktree checked out at `3210cb1` came up
  clean (0 local changes, 3598 files), so the folder was an unmodified 0.5.1 checkout plus
  build output: `app/build` 2.4 GB, `.gradle` 186 MB, `.git` 92 MB.
- This repo's `master` descends from it: 38 commits later, 13 files added, **0 files deleted**
  since 0.5.1.

## Where the history lives now

All 14 local branches that existed only in that folder were fetched here as `refs/pa/*`
before deletion (`.git` grew to 124 MB, local refs, not pushed):

```
pa/main, pa/clean-ui, pa/clean-core, pa/clean-web-py, pa/deps-latest,
pa/en-hi-translate, pa/subject-languages, pa/yash-exp, pa/worktree-agent-* (6)
```

They hang off a rewritten root (`2c17d06`), not off the 0.5.1 line, so they are parallel
experiments, not missing history. `clean-ui` shares 3378/3589 blobs with `master`, `clean-core`
3401/3581; the difference is almost entirely older versions of files that moved on here.

Recover anything from them with, for example:

```bash
git show refs/pa/yash-exp:path/to/file            # read one file
git checkout refs/pa/clean-ui -- docs/some-doc.md # pull one file into the tree
git branch scratch refs/pa/yash-exp               # or work on a whole branch
```

## Files that existed only there, and why none were kept

| file | verdict |
| --- | --- |
| `export/SessionAuditLog.kt` + `SessionAuditLogTest.kt` (14 tests, green against the current session model — it compiled and passed) | Not wanted for now. Recover from `refs/pa/yash-exp` if the case-diary timeline is picked up later. |
| `docs/06-test-plan.md`, `docs/10-test-report.md` | Described the 0.5.1 suite, not this one. Not carried over. |
| `assistant/TokenRouterAiService.kt` (23 KB) | Superseded by `assistant/CloudAi.kt` and the department gateway, and it hardcodes a `sk-…` TokenRouter key in the source. Not carried over; **rotate that key**. |
| `tools/security/generate_sbom.py` | Superseded by `tools/security/sbom.py` (Syft-based, already in CI). |
| `tools/content/generate_manifest.py` | Hashes `app/src/main/assets/content/` for runtime integrity; overlaps `tools/content/build_content.py`'s manifest handling. Available if wanted. |
| `ui/session/DraftsSaverTest.kt` | Tests a `DraftsSaver` that no longer exists here. |
| `res/drawable/ic_pin.xml` | Material "push_pin" vector, only used by that branch's own `FirstRunScreen`. |

No feature was missing here: `officer`, `gateway`, `ASL`, `ISL` and `translate` all appear in
more files on `master` than in those branches.

## Secrets

`C:\Users\Yash\OneDrive\Desktop\ss.txt` (194 bytes) holds the three values from that
checkout's `backend/.env`: `ASSIST_URL`, `ASSIST_MODEL`, `ASSIST_KEY`. Nothing else in the
folder was secret apart from the TokenRouter key noted above.

`refs/pa/*` can be dropped at any time with
`git for-each-ref --format='%(refname)' refs/pa | xargs -n1 git update-ref -d`.
