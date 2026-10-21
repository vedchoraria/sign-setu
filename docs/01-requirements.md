# 01. Requirements

## 1. Purpose

Police officers find lost children and adults, and rescue people from kidnapping or trafficking, who cannot communicate in words: they are deaf, non-verbal, too traumatised to speak, or speak a language the officer does not know. Sign Setu lets the officer ask simple questions and understand the answers on one shared phone while formal support is arranged.

The app is a communication aid. It does not interpret, it does not replace an ISL interpreter, special educator or counsellor, and it never records statements.

## 2. Users

| User | Description |
| --- | --- |
| Officer | Holds the phone and controls the app. Under time pressure, possibly outdoors, may have limited English. Uses a personal phone. |
| Subject | The person being helped, child or adult: a victim, a witness or an accused person. May be frightened, deaf, unable to read, or speak another language. Sees only the subject panel. |

The officer chooses a **subject profile** for each session; it decides what the subject sees and hears.

| Profile | Subject sees and hears |
| --- | --- |
| Young child | Picture cards, child character performing gestures, recorded audio in the subject language |
| ISL user | Picture cards, captions, human ISL clips, adult character phrases, interpreter call |
| Hearing, other language | Language finder, picture cards, recorded audio in the chosen language |
| Not sure | Picture cards, Yes and No, drawing |

## 3. Constraints

| Constraint | Detail |
| --- | --- |
| Devices | Android 10 (API 29) and later, 3 GB RAM or more, screens 5 to 6.9 inches, portrait. Officers' personal phones; no device management. |
| Network | Every feature works offline. INTERNET is declared only to reach the department gateway when the build has one and the phone is online. A second screen links over Bluetooth to an already paired device only. |
| Cost | Free and open-source components and free government services only. |
| Platform | Native Android, Kotlin, Jetpack Compose. |
| Distribution | Signed APK through the Mobile Seva AppStore or a department download page. |
| Law | DPDP Act 2023 and Rules 2025; Juvenile Justice Act 2015 section 74; POCSO Act sections 24 and 26; BNSS sections 38, 47, 48, 53, 58, 173 and 183; BSA section 125; Constitution Article 20(3); RPwD Act with IS 17802. Details in governance. |
| Assurance | CERT-In empanelled security audit before production. |

## 4. Functional requirements

Priority: **MUST** (the app cannot ship without it) or **SHOULD** (wanted, not blocking).

### 4.1 Session and screen

| Requirement | Priority |
| --- | --- |
| The officer can rotate the Show state 180 degrees for a subject sitting opposite. | MUST |
| The app has two states: **Officer** (officer panel full screen) and **Show** (subject panel full screen, facing the subject). One tap switches between them. | MUST |
| Opening the app resumes the unfinished session or starts a new one. | MUST |
| Finishing a session saves it under a name the officer chooses, offers export, and allows deleting it instead. | MUST |
| The officer sets the subject language per session (Hindi and English at minimum). | MUST |
| The officer sets a subject profile per session and can change it at any time. | MUST |
| From the first Show state until the session ends, the app stays pinned (screen pinning) when the device allows it; first run guides the officer to turn on app pinning. Returning from the Show state to the Officer state needs a tap on the officer handle; Back does nothing on the stage; a department setting adds the biometric or device credential. | MUST |

### 4.2 Asking

| Requirement | Priority |
| --- | --- |
| Preset questions are grouped: Safety, Health, Needs, Personal, Family, Address, School, Everyday, Talking, Their account, Sensitive, and the rights groups. | MUST |
| A preset question shows in the Show state with its picture cards and caption, plus by profile the character performing the gesture, a human ISL clip, or recorded audio. No network is used. | MUST |
| Sensitive questions ("what happened", "who hurt you") sit behind a deliberate second tap and never appear on the first screen. | MUST |
| The officer can type free text and show it in one step. The character plays it word by word with ISL signs (recorded first, then authored), everyday gestures, fingerspelling and pictures, each turned on or off by the officer. | MUST |
| Nothing reaches the Show state until the officer confirms it. | MUST |
| When a gesture, clip or audio item is missing, the picture and caption are shown alone and the officer sees "not available". | MUST |
| Clips and audio can be replayed and played at slow speed. | MUST |
| The officer can show any card from the full picture board, with categories, search and favourites. | MUST |
| Phrase bank: reviewed sentences with slots ("Do you want [item]?") play as a sequence of lexicon clips. | SHOULD |
| Language finder: tiles that each play a recorded greeting in one language; the subject's choice sets the subject language. | MUST |
| Call an interpreter opens a video call in the configured messaging app (Sandes or WhatsApp) to the state's contact. The button is hidden when no contact is configured. No session data is passed. | MUST |

### 4.3 Answers

| Requirement | Priority |
| --- | --- |
| Yes and No are always available as large cards during a question. | MUST |
| When the subject taps a card, the officer sees the card and its label in the officer language. | MUST |
| The subject can draw on a canvas with a finger, start again, and finish; the drawing is logged as the answer. | MUST |
| Below the character, the stage shows a live mirror of the subject's hands from the front camera. Nothing is captured, recorded or stored. The officer can turn it off for one person; the department can turn it off in settings. | MUST |
| During any question the subject can also answer "I don't know", "I don't understand" or "That's wrong", or ask to Stop, which opens a pause. Stop is drawn as an octagonal stop sign, never an open palm. "Stop" is shown to the officer prominently, with guidance to pause. | MUST |
| Gesture lookup: the officer records where the hand went (body location) and what it did (hand action); the app lists matching lexicon entries with picture and clip. | MUST |
| Machine translation of the officer's typed text into the subject's language, shown with its translation back into English and marked as not checked, before the officer chooses to show it (on-device). | SHOULD |
| The subject can write their own answer on the stage with the keyboard (every profile except a young child). It is logged word for word as a typed answer and never interpreted. Input is capped at 2,000 characters. | MUST |
| Show-back: the officer sends a candidate meaning to the Show state with "Is this it?". Where the item has other pictures, the subject picks among two or three of them plus "None of these" and only that pick records the meaning; otherwise Yes or No, and a Yes stays the officer's reading confirmed by the subject. Anything else is logged as unclear. | MUST |

### 4.4 Records

| Requirement | Priority |
| --- | --- |
| Every question shown and answer received is logged with a timestamp. | MUST |
| The officer can add free-text notes. | MUST |
| Export produces a PDF encrypted with AES-256 under a one-time passphrase shown once to the officer, shared through the Android share sheet. | MUST |
| Sessions are kept until the officer deletes them (default), or purged after a set retention period of 4 to 72 hours. | MUST |
| Every event is saved before the interface updates, so a crash or restart loses nothing. | MUST |

### 4.5 Settings and first run

| Requirement | Priority |
| --- | --- |
| First run explains the app in three screens (an aid, not an interpreter), sets the officer language, requires a secure device lock, and guides the officer to enable app pinning. | MUST |
| Settings: officer language, text size, clip speed, retention period within department limits, clear all data, about and open-source licences. | MUST |
| The "aid, not an interpreter" notice appears on first run and in About. | MUST |
| A build stops starting new sessions 180 days after its build date and shows "Update required"; existing sessions can still be exported and cleared. | MUST |

### 4.6 Second screen

| Requirement | Priority |
| --- | --- |
| The officer can pair one stage device and use it as the subject's screen. The device must already be paired in system Settings. The stage shows a 6-digit code that the officer types on their phone; both screens then show the same 4-digit check number, and the stage taps Match. Three wrong codes close the link. | SHOULD |
| The stage device stores nothing except one settings flag, `stageDevice`, which stops it offering to unlock by itself. It holds only the current item, stays pinned with no local exit while Live, and at the end shows a pinned hand-back screen until its own credential is given. | MUST |
| Answers from the stage are logged on the officer phone, acknowledged only after the commit, never dropped, de-duplicated on (item, answer number) and linked to the item they answer. Answers that arrive after a resume or for an earlier item are marked `stage_late`. | MUST |
| Link failure: both sides ping every 10 s and the link is lost after 30 s with no frame. A resume within 120 s needs no code. The officer gets Reconnect and "Use this phone". The stage shows Hold while the officer is out of the app; the 5-minute relock ends the link. | MUST |
| Stop and "I don't know" are always available on the stage device (Live, Lost and Hold). Stop opens a break that blocks Show until the officer taps "Break given", logged as `break_given`. | MUST |

### 4.7 Conversation modes

| Requirement | Priority |
| --- | --- |
| The officer can switch between Manual and Auto at any time. Manual is the default each session; Auto is available only when allowed in Settings. | SHOULD |
| Auto proposes only reviewed items from the rapport, ground-rule, welfare, practical, break and closing phases, in protocol order, filtered by role, deterministically. It never proposes sensitive, free-recall, follow-up or rights items. | MUST |
| Every question needs one officer tap to show. Only statements may advance by themselves, on the subject's Next tap, at most 3 in a row. | MUST |
| The Auto rules apply to every proposal. | MUST |
| Suggested, approved, skipped and automatically advanced items are logged with item ID, source and reason; every answer is logged with the ID of the item it answers. | MUST |
| "I don't understand" is always offered on the stage. | MUST |
| Stop pauses Auto until the officer resumes and the subject agrees to carry on; a second Stop ends Auto. | MUST |

### 4.8 Signed answers and accounts

| Requirement | Priority |
| --- | --- |
| Camera frames are analysed only inside an explicit sign window: Read signs or the stage hand button (an answer of up to 20 s) and Tell me (an account of up to 3 minutes). Outside a window the mirror is a preview with no analysis. | MUST |
| Signs are split at pauses and read on the phone into ranked proposals; proposals that match the answer cards of the item on screen rank first. Proposals are shown to the officer only, marked "read by the app, not checked", and are never shown to the subject automatically. | SHOULD |
| A reading becomes the subject's answer only when the subject picks it among two or three picture alternatives plus "None of these"; each set is logged as shown before it appears. | MUST |
| Every signed answer and account can be replayed for the rest of the session at ¼× or ½× with sign markers, and the officer or an interpreter can enter a word per sign. The subject is asked to sign more slowly only after a failed read of lost or too-fast hands. | SHOULD |

### 4.9 Roles

| Requirement | Priority |
| --- | --- |
| The officer can set the person's role (victim or witness, accused or in custody); it is "Not known yet" until set and exported only when set. Question groups follow the role: sensitive and free recall never for an accused; rights for an accused, with the child's rights instead of the adult ones for a young child or a person marked as possibly under 18. Each rights item carries "rights must also be explained through a qualified interpreter" and is recorded as "shown, not proof of understanding". | MUST |

### 4.10 Officer page and record

| Requirement | Priority |
| --- | --- |
| Word completions appear above the keyboard, tinted by what the character will do with the word (sign, gesture, picture, or none when spelled); tapping one fills in the word, and reviewed phrase templates about the word prepare a phrase in one tap. | SHOULD |
| Question run: up to 5 queued questions on one handover, each shown 1.4 s after the previous answer. Sensitive questions are never queued. Stop, "Something else" once no unseen pictures remain, or taking the phone back ends the run, logged as "Run ended after k of n". | SHOULD |
| Under each answer the officer sees its answer trail (time taken, every change, pause length after Stop) and its evidence grade; a changed answer or one slower than 20 s is outlined. | MUST |
| Say it back: the officer's reading, edited if needed, is shown to the subject among the item's other pictures; only the subject's pick makes it Confirmed, and "No" marks the line "Said no". | MUST |
| Picture choices are shuffled on every showing and capped at four, plus "Something else" and "I don't understand". "Something else" shows the pictures not yet seen, four at a time, and opens the drawing pad once all have been seen. Yes/no, number and single-picture questions are not shuffled. | MUST |
| A "Shown as" row after each item records the caption, motions, pictures in the order offered and the content version. The report carries the not-a-statement notice at the top and bottom of every page, a grade legend, each answer's grade, choice position and trail, and readings by the app or the officer in a separate section. | MUST |

### 4.11 Stage

| Requirement | Priority |
| --- | --- |
| A session opens with the officer's introduction, four pictured and signed ground rules and a practice round, moved on by the subject's Next; each step is logged, and taking the phone back before the practice round logs "Officer skipped the rest of the ground rules". | MUST |
| After Stop a calm pause layer covers the whole stage, so no answer can be tapped; only the subject's "I'm ready" ends it, and its length is logged. The officer's lock stays reachable. | MUST |
| An officer glance strip shows the last question and answer upside down at the edge facing the officer; it is hidden for sensitive questions, for personal-detail answers and while the subject draws or writes, can be hidden for the session, and never shows machine readings. | SHOULD |
| Vibration codes for whoever holds the phone: Yes one long buzz, No two buzzes, Stop, Break, "I don't know", "I don't understand" or "That's wrong" three short pulses, a picture a tick; never for the young-child profile. | SHOULD |
| Sun mode: in glare the stage turns black, white and yellow, on after 3 s above 10,000 lux and off after 10 s below 2,000 lux; the light sensor is read only while the stage shows; the officer can force it on. | SHOULD |
| Tilt to show (tipping the phone over its top edge for about 0.6 s) and Volume Up show what is prepared; on the stage single volume presses do nothing and holding both keys for 1.5 s returns to the officer. Both can be turned off and stand aside when TalkBack is on. | SHOULD |
| After a crash, low-memory kill or relock while the stage was showing, unlocking always asks for the full biometric or PIN. | MUST |

### 4.12 Personal details, phrases and the ISL dictionary

| Requirement | Priority |
| --- | --- |
| A question with an answer type opens its own input under the character: number pad (age 0 to 120, counts 0 to 50, brothers then sisters), a 10-digit mobile pad starting 6 to 9 with "No phone", a written name (letters, no digits, up to 60 characters) or text (up to 80), a class grid (1 to 12, college, not in school), a state then district picker, or several pictures with Done. "I don't know", "I don't understand", "That's wrong", the sign button (where a tracker exists) and Stop stay under every input. | MUST |
| A written or typed answer is read back large; it becomes the subject's own only on their Yes. One left unconfirmed when the item closes is logged as unconfirmed, with how it was given. | MUST |
| The report carries a "Personal details given" table: field, value, how it was given and whether the person confirmed it. | MUST |
| A phrase board on the stage offers the everyday phrases as pictures with words; a tap is logged as the subject's own. | SHOULD |
| The officer can search an ISL dictionary of every recorded and authored sign in English or Hindi, play a sign, and prepare it for the stage. | SHOULD |
| The stage shows the camera at its own 9:16 shape without cropping, between two rails of answers; Stop is the only pause control on the phone's stage. | MUST |
| While the stage camera shows, the subject sees their hands, arms, brows and lips traced live; a wrist leaving the frame lights that edge during a sign window. | SHOULD |
| A nod, head shake, thumbs up or down, raised open palm, shrug, or a finger held on a body part makes the matching Yes, No, Stop, "I don't know" or picture pulse as a proposal; only the subject's tap answers. | SHOULD |
| A sign the officer reads as a word the reader did not offer is recognised for the rest of the session and offered first. | SHOULD |
| Raised brows over most signs of an answer make its written draft a question. | SHOULD |
| A victim or witness can describe someone with pictures: clothes, colours and what was in their hands, offered only as follow-up after free recall. | SHOULD |
| When a sign reads as a letter, the word reader's top guess is still offered among the three guesses. | SHOULD |
| The officer sets the sign language of a session, ISL or ASL, and can change it during the session; each change is logged. The setting selects the sign reader, the dictionary, the fingerspelling alphabet and numbers, and the character's signs for typed text. ASL text keeps the order typed; the ISL reordering of time, negation and question words applies to ISL only. Every ASL sign and pairing is a draft until reviewed by Deaf ASL signers. | SHOULD |

## 5. Non-functional requirements

| Requirement | Priority |
| --- | --- |
| Cold start to usable Officer state under 2 seconds on the oldest test device. | MUST |
| A preset question appears in the Show state within 300 ms of confirmation. | MUST |
| Clips play at their source frame rate without visible stutter on the oldest test device. | MUST |
| APK under 100 MB for the base content set, including the vision and sign-reader models at full precision; no part of the APK is compressed or quantised to save size. | MUST |
| Memory under 300 MB during a session. | SHOULD |
| Android 10 (API 29) and later, 3 GB RAM or more. | MUST |
| Portrait, screens 5 to 6.9 inches. | MUST |
| Touch targets at least 48 dp; subject cards at least 96 dp. | MUST |
| Text contrast at least 4.5:1; meaning never carried by colour alone. | MUST |
| Officer panel usable at 200% system font scale. | MUST |
| Officer panel works with TalkBack. | SHOULD |
| Subject panel: no timers, flashing or pop-ups; sound only on officer action or by profile. The only time bar is the 20-second answer window the subject opened by signing; an account (Tell me) shows none. | MUST |
| All text from resources; Devanagari and the supported regional scripts render correctly. | MUST |
| Session data encrypted at rest under a key protected by Android Keystore. | MUST |
| Opening the app and past sessions require device credential or strong biometric. | MUST |
| Screenshots, screen recording and recents thumbnails blocked. | MUST |
| No session data in backups or device transfer. | MUST |
| No build declares RECORD_AUDIO or location; INTERNET is used only for the department gateway. CAMERA is declared for the stage mirror and sign windows, BLUETOOTH_CONNECT for the second screen, and VIBRATE (a normal permission) for answer codes. The optional `nearby` flavor adds the discovery permissions and nothing else. | MUST |
| No third-party analytics, advertising or crash-reporting SDKs. | MUST |
| No personal data in logs, crash traces or notifications. | MUST |
| Under 1 crash per 100 sessions in the pilot. | MUST |

