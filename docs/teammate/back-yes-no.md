# Back, Yes, No and Stop: acceptance matrix

Scope: every officer screen, sheet and dialog, and every subject stage state, in `app/src/main/java/app/signsetu/`.
Basis: the code at main commit 9989811. Each cell is either the control that
does the job, or the written reason it is not offered. A cell marked **gap** is a known departure from the T1 rule and
is listed again in the last section.

Columns:

- **Back**: system Back and any visible back control.
- **Yes**: the affirmative answer, or the control that stands for it.
- **No**: the negative answer, or the control that stands for it.
- **Stop**: the Stop control. Stop logs `stop` and ends the run.

## 1. Officer screens, sheets and dialogs

Officer surfaces have no subject-facing answer, so Yes, No and Stop are "not applicable" there, with the reason stated.

| Surface (code) | Back | Yes | No | Stop |
|---|---|---|---|---|
| Start screen (`ui/start/StartScreen.kt`, root of `AppRoot.kt`) | System Back leaves the app (root entry). | n/a: start list has no subject answer. | n/a: as Yes. | n/a: no subject stage here. |
| Start: resume card (`ResumeCard`) | Not a screen; its Resume and End actions are the way in and out. | n/a: officer action. | n/a: officer action. | n/a: no stage. |
| Session screen, officer mode (`OfficerPane.kt`, `SessionRoute`) | System Back returns to the main page (tab 0) from any other tab; on tab 0 it opens the End confirm (Export and end, End without export, Cancel). The session is left only through that confirm, so a paired link is not ended by a stray Back. | n/a: officer screen. | n/a: officer screen. | n/a: Stop for the subject is in the stage (section 2). |
| Officer dock, More page, composer, auto panel, ready card (`OfficerPane.kt`, `AutoPanel.kt`) | Inline; the screen's Back applies. | n/a: officer controls. | n/a: officer controls. | n/a: officer controls. |
| Stop banner (`OfficerPane.kt` `StopBanner`) | Inline; dismissed by its OK. | n/a: OK acknowledges the subject's Stop. | n/a: no negative choice. | n/a: the banner reports a Stop, it does not send one. |
| Phrase sheet (`Sheets.kt` `PhraseSheet`) | Modal sheet with a visible Back button (`OfficerSheet`); system Back and drag also dismiss it. | n/a: picking a slot prepares the phrase. | n/a: as Yes. | n/a: nothing sent to the subject until a slot is picked. |
| Board sheets, show and attach (`Sheets.kt` `BoardSheet`) | Modal sheet with a visible Back button and system Back; the card selection is kept as a draft on every dismiss path. | n/a: Use prepares or attaches cards. | n/a: as Yes. | n/a: officer selection only. |
| Gesture sheet (`GestureSheet`) | Modal sheet with a visible Back button and system Back. | n/a: picking a sign prepares the show-back. | n/a: as Yes. | n/a: officer selection only. |
| Log sheet (`LogSheet`) | Modal sheet with a visible Back button and system Back; a note being typed is kept as a draft on every dismiss path. | n/a: read-only record, add note. | n/a: as Yes. | n/a: record only. |
| Change sheet (`ChangeSheet`) | Modal sheet with a visible Back button and system Back; not opened while an item is on the second screen. | n/a: profile, language and role choices are officer settings. | n/a: as Yes. | n/a: no subject answer. |
| Signed sheet (`SignedSheet`) | Modal sheet with a visible Back button and system Back. | n/a: Accept records the officer's reading beside the app's. | n/a: Ask the subject is a separate action. | n/a: reading review only. |
| ISL dictionary sheet (`IslDictionarySheet.kt`, More page) | Modal `OfficerSheet`: visible Back button, system Back and drag dismiss it; the search text and selection survive rotation. | n/a: Show puts the sign on the stage, tapping a word only plays it here. | n/a: as Yes. | n/a: officer reference only; nothing reaches the subject until Show. |
| Say-back dialog (`SayBackDialog`, `MainPageParts.kt`) | Dialog dismissed by system Back (cancels the say-back). | n/a: Send prepares the say-back; the subject's Yes or No follows (stage, section 2). | n/a: cancel dismisses. | n/a: no stage action. |
| Sensitive-question dialog (`OfficerPane.kt` `askSensitive`) | Dismissed by system Back (Cancel). | n/a: confirm unlocks sensitive questions. | n/a: Cancel is the negative. | n/a: officer confirmation only. |
| End-session dialog (`OfficerPane.kt` `askEnd`) | Dismissed by system Back (Cancel). | n/a: Export and end is the affirmative. | n/a: End without export is the alternative; Cancel keeps the session. | n/a: no stage action. |
| Export dialog, working (`ExportDialog`) | Blocked on purpose (`onDismissRequest = {}`) while the report is written; a Back press here does nothing. | n/a: no choice while working. | n/a: no choice while working. | n/a: no stage action. |
| Export dialog, ready (`ExportDialog`) | Blocked on purpose (`onDismissRequest = {}`); Share or Finish leaves it. | n/a: Share hands the file to another app. | n/a: Finish ends the session after the file is deleted. | n/a: no stage action. |
| Export dialog, failed (`ExportDialog`) | Dismissed by system Back. | n/a: no affirmative choice. | n/a: Close only. | n/a: no stage action. |
| Second screen sheet (`pair/SecondScreenSheet.kt`) | Modal sheet with a visible Back button and system Back. | n/a: choosing a peer starts pairing. | n/a: as Yes. | n/a: pairing only; Stop is on the stage device. |
| Paired bar alerts (`pair/PairedBar.kt` `Alert`) | Inline; actions are the choices. | n/a: alert action is officer-side. | n/a: alert action is officer-side. | n/a: link status only. |
| Lock screen (`ui/LockScreen.kt`) | System Back leaves the app (device credential not granted). | n/a: unlock is by device credential. | n/a: as Yes. | n/a: no subject stage. |
| Use-as-stage checklist (`pair/StageDevice.kt` `UseAsStageButton`) | Dismissed by system Back. | n/a: Continue starts the permission steps. | n/a: the dismiss button opens security settings; it is not an answer. | n/a: no stage action. |
| Sign language setting (`OfficerPane.kt` menu row, ISL or ASL) | Inline menu row; the menu closes on Back. | n/a: tapping cycles to the next language, which selects the reader, dictionary and text-to-sign. | n/a: as Yes; tap again to return. | n/a: disabled while an item is on the second screen, so it cannot change under the subject. |
| Settings (`settings/SettingsScreen.kt`) | Visible back arrow and system Back pop the screen. | n/a: settings toggles. | n/a: settings toggles. | n/a: no stage. |
| Settings choice dialogs, interpreter app, retention, language (`ChoiceDialog`) | Dismissed by system Back. | n/a: choosing an option saves it. | n/a: Cancel closes without saving. | n/a: no stage. |
| Settings contact dialog (`ContactDialog`) | Dismissed by system Back. | n/a: Save stores the number. | n/a: Cancel. | n/a: no stage. |
| Settings clear-all dialog (`Dialog.CLEAR`) | Dismissed by system Back (keeps data). | n/a: confirm clears all data. | n/a: Cancel keeps data. | n/a: no stage. |
| Settings licences dialog (`LicencesDialog`) | Dismissed by system Back. | n/a: read-only. | n/a: Close. | n/a: no stage. |
| First-run walkthrough (`start/FirstRunScreen.kt`) | System Back goes to the previous page; on page one it leaves. | n/a: Next and Done move on. | Skip (on every page but the last) leaves the walkthrough. | n/a: no subject answer. |
| Key-reset notice (`AppRoot.kt` `GuardedAlertDialog`) | Dismissed by system Back, which acknowledges the notice. | n/a: acknowledge only. | n/a: as Yes. | n/a: no stage. |

## 2. Subject stage states (`ui/session/Stage.kt`, shown on the phone or the second screen)

The stage is the same composable on the subject's phone and on a paired stage device (`pair/StageDevice.kt` `LiveStage`).
System Back on the stage (`BackHandler` in `AppRoot.kt`) asks for the device credential and returns to the officer when pin-to-leave is on;
otherwise it shows the toast "Back is off here. Hold the handle to hand the phone back." The officer handle (a small corner
control) is the other way back, and it confirms with the device credential when pinning is on. The "Back" cells below
therefore give the subject's own back control where one exists.

Yes and No on the answer bar (`AnswerBar`) are shown for every pending item except a rights question and the drawing
asked for by the officer. Break and Stop (`PauseRow`) are shown under the answer bar and on most other states.

The camera on the stage can propose Yes or No (`LandmarkTracker.kt`, `CameraMirror.kt`): a nod or a held thumbs up pulses the Yes button, and a head shake or a held thumbs down pulses the No button (`RailButton`, `proposed`). The pulse is a highlight only. Nothing is logged until the subject taps the button, so every Yes and No cell below keeps its tap rule, and the proposal is cleared whenever the pending item changes.

| State (code) | Back | Yes | No | Stop |
|---|---|---|---|---|
| S1 Question, plain (`Pending.Ask`) | Reason: the subject has no back on a question; the officer handle returns to the officer. | Yes button, logs `yes` and advances (`answerYesNo`); pulses on a nod or thumbs up, and the tap is still the answer. | No button, logs `no` and advances; pulses on a head shake or thumbs down, and the tap is still the answer. | Stop in `PauseRow`, logs `stop` and ends the run. |
| S2 Rights notice (`Pending.Ask` with `rights_interpreter`) | Reason: as S1. | Reason: no Yes is shown; a Yes under a right would read as a waiver. | Reason: no No is shown; the subject's answer is "Don't understand", which does not waive. | Break and Stop in `PauseRow`. |
| S3 Question with picture choices (`MirrorWindow` tiles) | Reason: as S1. | Yes button on the answer bar (S1 rule). | No button on the answer bar; a picture tile is logged as the subject's pick. | Stop in `PauseRow`. |
| S4 Show-back, reading check (`Pending.ShowBack`) | Reason: as S1. | Yes logs the subject's Yes to the reading. | No logs `ANSWER_UNCLEAR` with the reading. Picking one of two or more pictures logs the choice (`logChecked`). | Stop in `PauseRow`. |
| S5 Say-back read-back (`Pending.FreeText` with `ref`) | Reason: as S1. | Yes logs `ANSWER_CARD` with `yes`, linked to the officer's line (Confirmed in the record). | No logs `ANSWER_UNCLEAR` and reopens the subject's own input (writing pad, or drawing where writing is not offered) through `reopensInput`; the reading is never taken as confirmed. | Stop in `PauseRow`. |
| S6 Officer card set (`Pending.Cards`) | Reason: as S1. | Yes button, or the picture tile the subject taps. | No button on the answer bar. | Stop in `PauseRow`. |
| S7 Phrase slot (`Pending.PhraseAsk`) | Reason: as S1. | Yes button on the answer bar. | No button on the answer bar. | Stop in `PauseRow`. |
| S8 Sign choice, "which sign" (`SignChoicePanel`, `choices != null`) | Reason: the panel's choices are the answer; no back. | Reason: each candidate is a pick; the Yes is the chosen sign tile. | "None of these" button (`onChoose(null)`). | Stop in `PauseRow` under the panel. |
| S9 Sign window, subject signs (`SigningPanel`, `ui.reading != null`) | Reason: the window is ended by Done or Stop, not by back. | Reason: Done ends the window and sends the reading; there is no yes or no on the window. | Reason: as Yes; a reading is judged afterwards by read-back (S5), not here. | Stop button, logs `stop` and ends the window. |
| S10 Language finder (`Pending.LanguageFinder`) | Reason: no back; a wrong language is changed by tapping another one. | Reason: the choice is a language tile, not a yes or no; the tile logs `LANGUAGE_CHOSEN`. | Reason: as Yes. | Break and Stop in `PauseRow`. |
| S11 Drawing, asked by the officer (`Pending.Drawing`) | Reason: no back, because the picture is the answer (code comment in `Stage.kt`). | Reason: Done submits the drawing; there is no yes. | "Something else" routes to this drawing pad from the picture choices; no direct No here. | Break and Stop in `PauseRow`. |
| S12 Drawing, opened by the subject (`drawing` from `PillButton` Draw) | Back button returns to the picture answers. | Reason: Done submits the drawing; not a yes or no. | Reason: as Yes. | Break and Stop in `PauseRow`. |
| S13 Writing (`WritingPanel`) | Back button returns to the picture answers. | Done (green) submits the typed text, logged unread (`ANSWER_TEXT`). | Reason: typed text is the answer; there is no no. | Stop in compact `PauseRow`. |
| S14 Ground rules, steps and practice (`Pending.GroundRules`) | Reason: the rules are read in order; no back. | Yes button on the answer bar; Next advances the step. | No button on the answer bar. | Break and Stop in `PauseRow`. |
| S15 Idle stage, nothing asked (`pending == null`) | Reason: nothing to go back to; the officer handle returns to the officer. | Reason: nothing is asked, so there is no answer bar. | Reason: as Yes. | Break and Stop in `PauseRow`. |
| S16 Pause layer (`PauseLayer`, after Break or Stop) | Reason: the pause has one exit, Ready. | Ready button (`endPause`) resumes; logs the pause length. | Reason: no question is asked during a pause. | After a Break, a Stop button sits on the layer (`stopInPause`) and logs `stop`; after a Stop the run has already ended and the layer offers Ready. |
| S18 Lesson answer inputs (`AnswerPanel`, `LessonInputs.kt`; name, age, phone, place, class, siblings, pictures) | Reason: the ways-out row sits below the input and the officer handle returns to the officer; Change in the read-back (S19) is the way to re-enter. | Reason: the answer is written, typed, picked or drawn; Done opens the read-back (S19) and Yes there confirms. Pictures: Done on the selection confirms. | Reason: as Yes; "Don't know" and, for phone questions, "No phone" are offered among the ways out. | Stop in the ways-out row under the input. |
| S19 Read-back of a lesson answer (`ReadBack`) | Reason: Change is the back; it discards the entry and returns to the input. | Yes logs the answer as Confirmed and, on a run, moves on. | Change is the No: it reopens the input and nothing is logged as confirmed; an entry left open when the item closes is logged unconfirmed. | Stop in the ways-out row. |
| S20 Phrase board (`PhraseBoard`, Say button on the stage) | Back button (round, bottom left) returns to the stage. | Reason: each phrase tile is one thing the subject chose and is logged as Confirmed; there is no yes or no. | Reason: as Yes. | Stop beside Back. |
| S21 Tell me (`SigningPanel` with `SignWindow.NARRATIVE`, started from the More page) | Reason: as S9; Done ends the account and Stop cancels it. | Done ends the account; the timed gloss transcript is then confirmed word by word. | Reason: an account is not a yes or no; a wrong word is rejected in the word-by-word confirm (S8, "None of these"). | Stop button on the rail, logs `stop` and ends the window. |
| S22 Stage device, waiting (`LinkState.Idle`, `Connecting`, `Failed`) | Cancel button leaves the pairing (`endLink`); system Back is not blocked before the first live frame. | Reason: nothing is asked yet. | Reason: as Yes. | Reason: no item is shown; Cancel is the way out. |
| S23 Stage device, code (`LinkState.Code`) | Cancel button (`cancelCode`); system Back is not blocked before the first live frame. | Match button, enabled only once the check value is shown. | Cancel button. | Reason: no item is shown; Cancel is the way out. |
| S24 Stage device, break (`breakFor` in `LiveStage`) | Reason: the break ends only when the officer shows something new; system Back is swallowed once live. | Reason: no question is asked during the break. | Reason: as Yes. | Reason: the break begins only after the subject's own Stop (`onCard`), which is already sent and logged; a second Stop would add nothing. |
| S25 Stage device, ended (`LinkState.Ended`) | System Back is swallowed; the officer's unlock button leaves (`exit`, device credential). | Reason: the session is over. | Reason: as Yes. | Reason: the session is over. |
| S17 Lost or held link, cover (`Cover`, `pair/StageDevice.kt`) | Reason: the link is down; the officer handle is not shown. | Reason: the item is hidden until the link is back. | Reason: as Yes. | Stop and "I don't know" are queued and sent when the link is back. |

## 3. Gaps against the T1 rule

None. Every cell in this file is a control or a written reason; no cell is blank.
