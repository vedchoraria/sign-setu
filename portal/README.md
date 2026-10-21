# Reviewer portal

A static web page for the people who sign off the subject-facing content: the ISL reviewer, the language reviewers
and the child-welfare reviewer. It reads the app's content files from `../app/src/main/assets/content` when it is
built, so it always shows what the app ships. It has no backend, makes no network calls and stores nothing outside
the reviewer's browser.

## Screens

- **ISL review**: every gesture, letter, digit and sign, played on the child and the adult character side by side.
  The characters are drawn by a TypeScript port of the app's rig (`src/rig.ts`, `src/draw.ts`), with play, pause,
  speed (0.25x to 1x) and a scrub bar. A link ending in `#<gesture id>` opens that gesture, and
  `#<gesture id>@<seconds>` holds it at that moment, for example `#letter.b@0.6`.
- **Language review**: every prepared question, phrase-bank sentence, subject-screen word and language-finder
  greeting, with its caption in each language and the recording beside it.
- **Child-welfare review**: the prepared questions by group and the phrase-bank sentences, with their picture cards.
  Wording that matches the research avoid list (stacked, either/or, tag or presupposing questions, body cards in a
  sensitive question, gestures that read as thumb or open-palm signals) carries a hint. The hints only point at
  wording to check; they are not a safety check.

Each item has Approve, Change and Reject, a notes box and the reviewer role chosen at the top. Verdicts are kept in
the browser's local storage, per role, and **Export JSON** downloads them all.

## Run

Needs Node 22.18+, 24.11+ or 26+ (the range `vite-plus` declares). From `portal/`, after `npm install`:

| Command          | Same as           | Does                                                 |
| ---------------- | ----------------- | ---------------------------------------------------- |
| `npx vp dev`     | `npm run dev`     | Local server with live reload                        |
| `npx vp check`   | `npm run check`   | Format, lint and type check                          |
| `npx vp test`    | `npm test`        | Unit tests, including the rig comparison with Rig.kt |
| `npx vp build`   | `npm run build`   | Static site in `portal/dist`                         |
| `npx vp preview` | `npm run preview` | Serves `portal/dist`                                 |

The built `dist/` folder can be copied to any machine and served by any static file server. Content changes in the
app need a rebuild.

## Key files

| Path                             | Contents                                                        |
| -------------------------------- | --------------------------------------------------------------- |
| `src/content.ts`                 | Imports the app's content JSON at build time                    |
| `src/App.tsx`, `src/screens.tsx` | Roles, tabs and the review screens                              |
| `src/rig.ts`, `src/draw.ts`      | TypeScript port of the app's rig and its drawing                |
| `src/flags.ts`                   | Child-welfare wording hints                                     |
| `src/missing.ts`                 | Missing-caption check for language reviewers                    |
| `src/reviews.ts`                 | Verdict storage and the JSON export format                      |
| `src/rig-fixtures.json`          | Poses written by `RigFixtureTest`, checked by `src/rig.test.ts` |

## Keeping the verdicts

The exported JSON file is the review record: keep it with the release it reviewed.

## Keeping the rig in step

`src/rig.ts` is a port of `app/src/main/java/app/signsetu/ui/avatar/Rig.kt`. The Kotlin test `RigFixtureTest`
writes `src/rig-fixtures.json` (poses of every gesture on both characters at three moments) and fails when Rig.kt no
longer produces it; `src/rig.test.ts` checks the TypeScript rig against the same file. After a deliberate change to
Rig.kt, delete the fixture file, run `RigFixtureTest` to write it again, and update `rig.ts` until `vp test` passes.

The portal's place in the architecture is in [docs/02-architecture.md](../docs/02-architecture.md), section 12.
