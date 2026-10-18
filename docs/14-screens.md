# 14. Screen Catalogue

Every screen of the application, captured from the `standard` debug build on an Android 14 emulator (1080 by 2400 pixels) in a session for a victim who signs, with Hindi as the subject language unless stated. Release builds block screenshots (SEC controls), so the catalogue is taken from the debug build of the same source. The emulator's camera shows a synthetic scene in place of a person.

## 1. Notation

| Prefix | State |
| --- | --- |
| H | Officer, before a session (first run, Home) |
| S | Show state: the stage, phone facing the subject |
| O | Officer state: the session panel |
| C | Settings |

## 2. Before a session

| ID | Screen | File | Notes |
| --- | --- | --- | --- |
| H-1 | First run, step 1 | [01-first-run-1.png](screens/01-first-run-1.png) | Purpose and limits; app language |
| H-2 | First run, step 2 | [02-first-run-2.png](screens/02-first-run-2.png) | Offline and encrypted record |
| H-3 | First run, step 3 | [03-first-run-3.png](screens/03-first-run-3.png) | App pinning |
| H-4 | Home | [04-home.png](screens/04-home.png) | Profile, subject language, role |
| H-5 | Home, Signs profile | [05-home-signs.png](screens/05-home-signs.png) | Indian (ISL) or American (ASL) chosen before start (D-41) |

## 3. The stage

| ID | Screen | File | Notes |
| --- | --- | --- | --- |
| S-1 | Introduction | [06-stage-introduction.png](screens/06-stage-introduction.png) | A statement: only Next and Stop, with the hint "Tap Next when you are ready" |
| S-2 | Ground rule | [07-stage-ground-rule.png](screens/07-stage-ground-rule.png) | Same layout as S-1 |
| S-3 | Practice round | [08-stage-practice.png](screens/08-stage-practice.png) | Open question: no Yes, No or "That's wrong"; Draw, Write and Sign offered |
| S-4 | Question | [09-stage-question.png](screens/09-stage-question.png) | Caption, held picture, answer rails, hint over the camera window |
| S-5 | Writing | [10-stage-writing.png](screens/10-stage-writing.png) | The stage makes room for the keyboard; the character stays in view |
| S-6 | Drawing | [11-stage-drawing.png](screens/11-stage-drawing.png) | Five ink colours, Undo, Start again, Done |
| S-7 | Drawing hand-back | [12-stage-drawing-hand-back.png](screens/12-stage-drawing-hand-back.png) | After a requested drawing: thanks, give the phone back, Draw more |
| S-8 | Drawing in Chhattisgarhi | [13-stage-drawing-chhattisgarhi.png](screens/13-stage-drawing-chhattisgarhi.png) | Every stage word in the chosen language (D-43) |
| S-9 | Phrase board | [14-stage-phrase-board.png](screens/14-stage-phrase-board.png) | Eight everyday phrases the subject can say |
| S-10 | Number pad | [15-stage-number-pad.png](screens/15-stage-number-pad.png) | Lesson-sheet answer input; the Sign button carries a word and a tool colour |
| S-11 | Sign window | [16-stage-signing.png](screens/16-stage-signing.png) | Time bar, body guide, "Sign to the camera. Tap Done when you finish." |
| S-12 | After signing | [17-stage-after-signing.png](screens/17-stage-after-signing.png) | With no sign read, the question returns; with a reading, the picture choices of D-41 appear first |
| S-13 | Pause | [18-stage-pause.png](screens/18-stage-pause.png) | Breathing circle and bubbles; only "I'm ready" ends it |
| S-14 | Language finder | [19-stage-language-finder.png](screens/19-stage-language-finder.png) | Seventeen languages in their own scripts; the tapped one greets back |

## 4. The officer panel

| ID | Screen | File | Notes |
| --- | --- | --- | --- |
| O-1 | Transcribe | [20-officer-transcribe.png](screens/20-officer-transcribe.png) | Conversation with grades, trails and how each answer was given |
| O-2 | Ready to show | [21-officer-ready-to-show.png](screens/21-officer-ready-to-show.png) | A prepared question and its media marks |
| O-3 | Typed text with Hindi | [22-officer-typed-hindi.png](screens/22-officer-typed-hindi.png) | Instant on-phone English to Hindi caption, marked unchecked (D-44) |
| O-4 | More | [23-officer-more.png](screens/23-officer-more.png) | Question groups, other ways to ask, session tools |
| O-5 | Question groups | [24-officer-question-groups.png](screens/24-officer-question-groups.png) | Personal details questions |
| O-6 | They gestured | [25-officer-they-gestured.png](screens/25-officer-they-gestured.png) | Where the hand went and what it did |
| O-7 | Picture board | [26-officer-picture-board.png](screens/26-officer-picture-board.png) | Categories, search and favourites |
| O-8 | ISL dictionary | [27-officer-isl-dictionary.png](screens/27-officer-isl-dictionary.png) | Recorded and drawn signs, searchable in English and Hindi |
| O-9 | ASL dictionary | [28-officer-asl-dictionary.png](screens/28-officer-asl-dictionary.png) | Shown when the session signs in ASL |
| O-10 | Session log | [29-officer-session-log.png](screens/29-officer-session-log.png) | Every row with time and kind; notes |
| O-11 | Session menu | [30-officer-menu.png](screens/30-officer-menu.png) | Sign language, sitting opposite, sun mode, the glance strip, skeleton only, camera |
| O-12 | Change profile | [31-officer-change-profile.png](screens/31-officer-change-profile.png) | Role, profile, language |
| O-13 | Second screen | [32-officer-second-screen.png](screens/32-officer-second-screen.png) | Pairing a department device |
| O-14 | End session | [33-officer-end-session.png](screens/33-officer-end-session.png) | Export, end without export, cancel |
| O-15 | Report ready | [34-officer-export.png](screens/34-officer-export.png) | One-time password, share, finish and delete |

## 5. Settings

| ID | Screen | File | Notes |
| --- | --- | --- | --- |
| C-1 | Settings, interpreter and stage | [35-settings-1.png](screens/35-settings-1.png) | |
| C-2 | Settings, shortcuts and assistant | [36-settings-2.png](screens/36-settings-2.png) | |
| C-3 | Settings, records and about | [37-settings-3.png](screens/37-settings-3.png) | Retention, delete all data, version |

## 6. Not captured

| Screen | Reason |
| --- | --- |
| Unlock prompt | The system credential window is secure and captures blank |
| Picture choices after a signed answer | Needs a person signing to the camera; the emulator camera shows a synthetic scene. The panel is the one used for "Ask them to choose" and is covered by the instrumented stage tests |
| Second-screen stage device | Needs two devices |
