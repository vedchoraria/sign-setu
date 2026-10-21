# 14. Screen Catalogue

Every screen of the application, captured from the `standard` debug build on an Android 14 emulator (1080 by 2400 pixels) in a session for a victim who signs, with Hindi as the subject language unless stated. Release builds block screenshots (SEC controls), so the catalogue is taken from the debug build of the same source. The emulator's camera shows a synthetic scene in place of a person; for the sign-reading screens the debug build's stand-in tracker supplies a moving hand.

## 1. Notation

| Prefix | State |
| --- | --- |
| H | Officer, before a session (first run, Home) |
| S | Show state: the stage, phone facing the subject |
| O | Officer state: the session panel |
| C | Settings |

## 2. Before a session

| Screen | File | Notes |
| --- | --- | --- |
| First run, step 1 | [01-first-run-1.png](screens/01-first-run-1.png) | Purpose and limits; app language |
| First run, step 2 | [02-first-run-2.png](screens/02-first-run-2.png) | Offline and encrypted record |
| First run, step 3 | [03-first-run-3.png](screens/03-first-run-3.png) | App pinning |
| Home | [04-home.png](screens/04-home.png) | Profile, subject language, role |
| Home, Signs profile | [05-home-signs.png](screens/05-home-signs.png) | Indian (ISL) or American (ASL) chosen before start |

## 3. The stage

| Screen | File | Notes |
| --- | --- | --- |
| Introduction | [06-stage-introduction.png](screens/06-stage-introduction.png) | A statement: only Next and Stop, with the hint "Tap Next when you are ready" |
| Ground rule | [07-stage-ground-rule.png](screens/07-stage-ground-rule.png) | Same layout as the introduction |
| Practice round | [08-stage-practice.png](screens/08-stage-practice.png) | Open question: no Yes, No or "That's wrong"; Draw, Write and Sign offered |
| Question | [09-stage-question.png](screens/09-stage-question.png) | Caption, held picture, answer rails, hint over the camera window |
| Writing | [10-stage-writing.png](screens/10-stage-writing.png) | The stage makes room for the keyboard; the character stays in view |
| Drawing | [11-stage-drawing.png](screens/11-stage-drawing.png) | Five ink colours, Undo, Start again, Done |
| Drawing hand-back | [12-stage-drawing-hand-back.png](screens/12-stage-drawing-hand-back.png) | After a requested drawing: thanks, give the phone back, Draw more |
| Drawing in Chhattisgarhi | [13-stage-drawing-chhattisgarhi.png](screens/13-stage-drawing-chhattisgarhi.png) | Every stage word in the chosen language |
| Phrase board | [14-stage-phrase-board.png](screens/14-stage-phrase-board.png) | Eight everyday phrases the subject can say |
| Number pad | [15-stage-number-pad.png](screens/15-stage-number-pad.png) | Lesson-sheet answer input; the Sign button carries a word and a tool colour |
| Sign window | [16-stage-signing.png](screens/16-stage-signing.png) | Time bar, body guide, live hand skeleton, one dot per sign seen, "Sign to the camera. Tap Done when you finish." |
| After signing | [17-stage-after-signing.png](screens/17-stage-after-signing.png) | "Is this it?": two or three pictures per sign read and "None of these"; only the person's tap records a meaning |
| Pause | [18-stage-pause.png](screens/18-stage-pause.png) | Breathing circle and bubbles; only "I'm ready" ends it |
| Language finder | [19-stage-language-finder.png](screens/19-stage-language-finder.png) | Seventeen languages in their own scripts; the tapped one greets back |

## 4. The officer panel

| Screen | File | Notes |
| --- | --- | --- |
| Transcribe | [20-officer-transcribe.png](screens/20-officer-transcribe.png) | Conversation with grades, trails and how each answer was given |
| Ready to show | [21-officer-ready-to-show.png](screens/21-officer-ready-to-show.png) | A prepared question and its media marks |
| Typed text with Hindi | [22-officer-typed-hindi.png](screens/22-officer-typed-hindi.png) | Instant on-phone English to Hindi caption, marked unchecked |
| More | [23-officer-more.png](screens/23-officer-more.png) | Question groups, other ways to ask, session tools |
| Question groups | [24-officer-question-groups.png](screens/24-officer-question-groups.png) | Personal details questions |
| They gestured | [25-officer-they-gestured.png](screens/25-officer-they-gestured.png) | Where the hand went and what it did |
| Picture board | [26-officer-picture-board.png](screens/26-officer-picture-board.png) | Categories, search and favourites |
| ISL dictionary | [27-officer-isl-dictionary.png](screens/27-officer-isl-dictionary.png) | Recorded and drawn signs, searchable in English and Hindi |
| ASL dictionary | [28-officer-asl-dictionary.png](screens/28-officer-asl-dictionary.png) | Shown when the session signs in ASL |
| Session log | [29-officer-session-log.png](screens/29-officer-session-log.png) | Every row with time and kind; notes |
| Session menu | [30-officer-menu.png](screens/30-officer-menu.png) | Sign language, sitting opposite, sun mode, the glance strip, skeleton only, camera |
| Change profile | [31-officer-change-profile.png](screens/31-officer-change-profile.png) | Role, profile, language |
| Second screen | [32-officer-second-screen.png](screens/32-officer-second-screen.png) | Pairing a department device |
| End session | [33-officer-end-session.png](screens/33-officer-end-session.png) | Export, end without export, cancel |
| Report ready | [34-officer-export.png](screens/34-officer-export.png) | One-time password, share, done; the session stays saved |
| A signed answer | [38-officer-signed-answer.png](screens/38-officer-signed-answer.png) | The app's reading with replay, the choices shown, and the person's confirmed pick as separate rows |

## 5. Settings

| Screen | File | Notes |
| --- | --- | --- |
| Settings, interpreter and stage | [35-settings-1.png](screens/35-settings-1.png) | |
| Settings, shortcuts and assistant | [36-settings-2.png](screens/36-settings-2.png) | |
| Settings, records and about | [37-settings-3.png](screens/37-settings-3.png) | Retention, delete all data, version |

## 6. Not captured

| Screen | Reason |
| --- | --- |
| Unlock prompt | The system credential window is secure and captures blank |
| Second-screen stage device | Needs two devices |
