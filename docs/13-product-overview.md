# 13. Product Overview

A single reference for explaining Sign Setu: what it does, how it works, what it is built with, and what remains before production. Diagrams are in the system design; details are in the numbered documents cited in each section.

## 1. Purpose

Sign Setu is an offline Android application that helps a police officer communicate with a person who cannot communicate in words: deaf, non-verbal, traumatised, or speaking another language; a victim, a witness or an accused person; a child or an adult. Officer and subject share the officer's phone, or the subject uses a paired second screen. The application is a bridge for first contact, never the statement: it is a communication aid, not an interpreter, and never records statements (BNSS s.173 and s.183(6), BSA s.125, POCSO s.26). Every export says "communication note, not a statement", and the app reminds the officer that a qualified interpreter and the statutory video recording are required for statements.

| Property | Value |
| --- | --- |
| Platform | Android 10 (API 29) and later; sign reading on 64-bit phones |
| Network | Optional: only the department gateway, only when the build has one and the phone is online; everything works without it |
| Languages for the subject | 17 in the language finder, including Chhattisgarhi, Gondi and Halbi; every stage word, question, phrase and picture label in all 17 (drafts beyond English and Hindi); typed English captioned in Hindi on the phone; sign languages ISL and ASL |
| Officer interface languages | English, Hindi |
| Release APK | 0.6.0: 78.9 MiB arm64-v8a phone APK, 104.1 MiB universal (arm64-v8a and x86_64); nothing compressed for size |
| Automated tests | 316 unit tests pass for both build flavors; 44 instrumented tests are defined for a device or emulator; 1,221 portal tests pass; 43 gateway tests pass; lint 0 errors |

## 2. How a session works

1. The officer opens the app; it opens directly if the phone was unlocked in the last 30 seconds, otherwise it asks for the phone's fingerprint or PIN. After a crash or low-memory kill while the stage was showing, the full fingerprint or PIN is always asked.
2. On Home the officer chooses who they are helping (young child, signs, other language, not sure), the person's role (left as "Not known yet" until known) and the subject's language, then starts.
3. The stage opens with the officer's introduction, four ground rules and a practice round, or with the language finder for someone who speaks another language.
4. The officer prepares something on the officer page (a prepared question, a phrase, typed text, pictures, a drawing request, a sign window) and shows it: Send, tilting the phone toward the subject, or Volume Up.
5. The subject sees the stage and answers by tapping, drawing, writing or signing.
6. The officer taps the small lock to take the phone back and reads the answer, its grade and its trail in the conversation.
7. At the end the officer exports an encrypted PDF communication note or deletes the session. Unended sessions are deleted after the retention period (24 hours by default).

## 3. Features

### 3.1 Officer side

The officer page has two tabs, built from one card and tile style. **Transcribe** holds the conversation, quick questions and the composer; **More** searches every question group and phrase and also holds They gestured, the picture board, Draw, the language finder, Tell me, the ISL dictionary, the session log with notes, and profile, role or language. With a blank search it shows the applicable question groups as category chips. Picking something on More returns to Transcribe.

| Feature | Description |
| --- | --- |
| Profiles | Young child (child character, automatic speech), signs (adult character, no sound), other language (language finder first), not sure |
| Role | Not known yet (default), victim or witness, accused or in custody; sets which question groups appear (§4.8). An accused may be marked "may be under 18" |
| Conversation | A messaging-style view: items shown to the subject on the right, the subject's answers with pictures on the left, notes and steps in between |
| Prepared questions | 80 questions in 15 groups: Safety, Health, Needs, Personal, Family, Address, School, Everyday, Talking (ground rules, breaks, closing), Their account, Sensitive (victim only, locked until the officer confirms), Rights, Arrest rights (adult), Child's rights, Disability rights. A young child is not asked for a mobile number; an accused gets the factual Personal, Family, Address and School questions but not the welfare-only ones (introduce yourself, favourite subject, what they like at school) |
| Lesson-sheet questions | An ISL lesson sheet in full: name, age, mobile, introduction; father's and mother's names, family size, brothers and sisters, who they live with; where they live, village or city, district, state, nearby place; school, class, teacher, favourite subject, what they like at school; and eight everyday phrases. Each has an answer type that opens its own input on the stage (§3.2), and the character signs it through the typed-text path in draft ISL order |
| Quick questions | Hurt, water, hungry, toilet, doctor, mother nearby, age and where you live, each disappearing once asked |
| Question run | Long-press questions to queue them, or tap "Safety basics": up to 5 on one handover, each 1.4 s after the previous answer. Sensitive questions are never queued. Stop, "Something else" or taking the phone back ends the run, logged "Run ended after k of n" |
| Phrase bank | 10 reviewed sentences built from two patterns, "Do you want [water]?" and "Are you [cold]?" |
| Type anything | Typed text is shown at once; the character plays it word by word under four toggles: Signs, Gestures, Spell, Pictures. With Signs on, the order is draft ISL order (§4.4) |
| Word completions | Above the keyboard; each typed word is tinted by what the character will do with it: green for a sign, teal for a gesture, amber for a picture, no tint when it will be spelled. Reviewed phrase templates about the word prepare a phrase in one tap |
| Vocabulary | 226 entries covering 1,326 written forms in English, Hindi, Hindi in Latin letters and local words |
| Answer trail | Under each answer: time taken, every answer in the order given if the person changed it, and the length of a pause after Stop. A changed answer, or one slower than 20 s, gets an amber outline, which means "look again", never deceit |
| Evidence grades | Said (the person tapped, wrote or drew it), Confirmed (the person picked this meaning from two or three choices shown back), Officer's reading, App's reading, Said no, Unclear |
| Say it back | Shows the officer's reading, edited if needed, to the person among the item's other pictures; only their pick makes it Confirmed; No marks the line "Said no" |
| Read signs and Tell me | Open a sign window: a short answer of up to 20 s, or an account of up to 3 minutes (§6) |
| Signed answers | The app's reading per sign, slow replay at ¼× or ½× with sign markers, a word per sign with the reader's top three guesses as chips, and **Ask them to choose** |
| Manual and Auto | Auto, when allowed in Settings, suggests the next welfare item from the reviewed bank and the officer approves each one. It is not an interview and records no statement. The report shows the mode, who chose each shown item, and suggestions not acted on |
| Media marks | Each question card shows whether a gesture and a recording exist and whether the caption is missing in the subject's language; "Ready to show" lists what the subject will get |
| Instant Hindi caption | When the subject reads Hindi, English the officer types gets a Hindi caption as they type, from the reviewed bank, sentence patterns or word by word, marked as unchecked; a tap dismisses it, and nothing is shown if any word is unknown |
| Assistant (optional) | Translates typed text into the subject's language and back into English on the phone; the officer sees both before sending |
| They gestured | The officer marks where the subject's hand went and what it did; matching entries from the 33-entry gesture lexicon are shown back to the subject, who confirms the meaning |
| Picture board | 117 picture cards in 12 categories (including clothes and colours for describing someone), with search and favourites |
| ISL dictionary | Every recorded and draft sign by category (415 signs in 17 categories: 262 retargeted from INCLUDE and 153 from the ISLRTC dictionary, plus the authored word signs), searched in English, Hindi or any written form; a tap plays the sign for the officer, Show prepares it for the stage |
| Draw, language finder | A drawing pad for the subject; a grid of greetings in every language |
| Interpreter call | Opens the department's Sandes or WhatsApp contact; no session data is passed |
| Second screen | Pairs a department phone or tablet as the subject's screen (§3.3) |
| Notes and log | Officer notes and the full session record |
| Export | A PDF communication note encrypted with AES-256 under a one-time 16-character password shown once |
| Settings | Interpreter contact, camera mirror, large captions, slower gestures, screen lock to leave the stage, tilt to show, volume keys, allow automatic conversation, assistant model, retention period (4 to 72 hours), app language, delete all data |

### 3.2 Subject side (the stage)

| Feature | Description |
| --- | --- |
| Characters | A child and an adult character framed to the hips, with jointed fingers and facial expressions; motions cover 415 ISL signs retargeted from recorded Deaf signers, 42 everyday gestures, the 26-letter two-handed ISL alphabet, 10 digits and the authored word signs |
| Opening | The officer's introduction (s1), then four pictured and signed ground rules ("I was not there", "you can say I don't know", "tell me if I'm wrong", "you can take a break") and a neutral practice round, "Show me what you did this morning", answered by drawing, writing or signing. The subject moves on with Next; each step is logged as shown. If the officer takes the phone back before the practice round, the log records "Officer skipped the rest of the ground rules" |
| Caption | The question in the subject's language in large type; during typed text the current word is lit, a spelled word shows its current letter, and a word's picture is held up. Tapping a word replays it alone at half speed; tapping the question replays it |
| Speech | Placeholder recordings until re-recorded: 1,350 recordings: the greeting in 15 languages plus 89 clips for each of those languages (Indic Parler-TTS); the rest show captions and pictures; Gondi and Halbi have no voice yet; automatic for a young child or another language, never for a signer |
| Answers | Rails either side of a 9:16 camera window: Yes, "I don't know", "I don't understand", Draw and Write on the left; Next (opening or Auto), No, "That's wrong", the camera button and Stop (an octagonal stop sign, never an open palm, and the only pause control) on the right. Stop also stays on the drawing, writing and language-finder screens. Every tap is logged right after the prompt that was on screen |
| Picture choices | Shuffled every time and capped at four pictures, plus "Something else" (which opens the drawing pad) and "I don't understand". Yes/no, number and single-picture questions are not shuffled |
| Answer inputs | For a lesson-sheet question the panel under the character becomes its input: a number pad with large digits (age 0 to 120, counts, brothers then sisters), a 10-digit mobile pad grouped 5 and 5 with "No phone", a written name or place, a class grid (1 to 12, college, not in school), a state list (Chhattisgarh first, all states and union territories) then its district or a written one, and several pictures with Done. A written or typed answer is read back large and becomes the subject's own only on their Yes; one left unconfirmed is logged as unconfirmed. "I don't know", "I don't understand", "That's wrong" and Stop stay under every input |
| Phrase board | A button by the character opens the eight everyday phrases as pictures with words; a tap logs the phrase as the subject's own |
| Draw and Write | A drawing pad with five ink colours and Undo; a drawing that answers a question returns to it, a requested drawing ends on a thank-you and hand-back screen; Write opens the keyboard for every profile except a young child; written answers are capped at 2,000 characters |
| Sign an answer | The camera button ("Sign") opens a 20-second sign window; the stage shows one dot per sign seen, then at once asks the person to pick what each sign meant among two or three pictures (§6) |
| What to do now | One plain line over the camera window: answer by tapping or signing, or tap Next; statements show only Next and Stop |
| Choosing a meaning | After the officer taps Ask them to choose: two or three picture cards per sign in the subject's language, and "None of these" |
| Camera mirror | The subject sees the whole camera frame below the character, uncropped at 9:16, with a hand-and-arm skeleton during a sign window, never face points; nothing is captured or stored |
| Replay and speed | The subject sets the signing speed (½×, ¾×, 1×), kept for the session in memory only; a young child starts at ¾× at most |
| Pause | After Stop, a calm night-sky layer with a breathing circle and bubbles to pop covers the whole stage, with no score and no sound. Only the subject's "I'm ready" ends it; the officer's lock stays reachable. The log records "Pause ended after N s" |
| Officer glance strip | A band at the edge facing the officer shows the last question and answer upside down, for an officer sitting opposite. Off until the officer turns it on from the session menu. Hidden for sensitive questions and while the subject draws or writes; a long press hides it for the session. Machine sign readings never appear on it |
| Vibration codes | Felt by whoever holds the phone: Yes one long buzz; No two buzzes; Stop, Break, "I don't know", "I don't understand" or "That's wrong" three short pulses; a picture a tick. Never for the young-child profile |
| Sun mode | The stage turns black, white and yellow in glare: on after 3 s above 10,000 lux, off after 10 s below 2,000 lux. The light sensor is read only while the stage shows; the officer can force it on from the menu |
| Rotate | The stage turns 180 degrees for a subject sitting opposite; tilting the phone to show turns it automatically |
| Staying on the stage | The app is pinned; Back does nothing; single volume presses do nothing; one tap on the small lock, or both volume keys held for 1.5 s, returns to the officer |

Subject-screen words, questions, phrases and card labels exist in English, Hindi, Chhattisgarhi, Gondi, Halbi and the other twelve languages of the language finder; everything beyond English and Hindi is marked draft.

### 3.3 Paired stage

A second phone or tablet, paired once in system Bluetooth settings, shows the subject's stage while the officer keeps their phone. The officer phone stays the only store; the stage device keeps nothing about the session, has no local exit while live, and at the end stays pinned on a hand-back screen until its own credential is given. Stop and "I don't know" are always available on it; Stop opens a break that blocks Show until the officer taps "Break given". Paired mode does not change the app's non-evidentiary status.

With the online assistant on, a paired stage can host the **AI counsellor** for a person who is afraid of the police: the counsellor writes one short, non-leading line per turn from the conversation so far, the officer sees each line for six seconds (Send now, Another line, Take over) before it is signed and captioned on the stage, and Stop, a break or a flagged risk hands the conversation back. It is never offered for an accused person, and every line is marked "AI counsellor" in the record.

### 3.4 Safety and privacy

| Control | Description |
| --- | --- |
| Offline | Every feature works with no connection; no RECORD_AUDIO or location permission, enforced by a build gate for every variant |
| Encrypted record | SQLCipher (AES-256) under a random key wrapped by an Android Keystore key that works only after the phone's own lock |
| Relock | After five minutes away from the app; after a crash mid-stage the full credential is always asked |
| Retention | Finished sessions saved under the officer's name and purged after 24 hours by default, or after a set period of 4 to 72 hours, or kept until deleted if the officer chooses; "Delete all data" destroys the keys |
| Screen | Screenshots blocked in release; taps through another app's overlay ignored; no backup or device transfer |
| Keyboard | Every text field asks the keyboard not to learn from what is typed |
| Integrity | Every content file, the vision and sign-reader models and the assistant model are checked by SHA-256 before use |
| Landmarks | Biometric data: analysed only inside a sign window, kept in memory for the session, never stored or exported |
| Expiry | A build refuses new sessions 180 days after it was made; rooted phones are warned, list no saved sessions and delete a session when it is finished |
| Excluded by design | Identity columns in the database (personal details exist only as the subject's own logged answers, purged with the session), statements, audio or video recording, identity recognition, thumb or open-palm signals for yes, no or stop, body maps for the subject |

## 4. Mechanisms

### 4.1 Unlock and keys

A random 32-byte database key is stored only in wrapped form (`noBackupFilesDir/dek.bin`), encrypted by an AES-256-GCM key in Android Keystore that needs no prompt but works only while the phone is unlocked. A key made by an earlier version is rewrapped on the next unlock, so stored sessions are kept. While the stage shows, a flag holding no subject data (`stage_live`) is written to disk; if the app is locked or restarted while it is set, unlocking always asks for the full credential, and a successful prompt clears it. If the phone's lock is removed or reset, Android destroys the Keystore key; the app then deletes the unreadable data and tells the officer once.

### 4.2 Write before show, and the layered record

Every event (item shown, answer, note) is written to the encrypted database before the screen changes; this also covers items sent over the second-screen link, which the stage confirms with Displayed. The officer page and the stage are two modes of one session screen, driven by a single view-model state; what is on the stage is held apart from the officer's draft.

- Right after each item shown, a "Shown as" row records exactly what the person saw: the caption in their language, the motion IDs the character played, the pictures in the order offered and the content version (the first 12 hex characters of the content manifest's SHA-256). The place of the picture chosen ("choice 2 of 6") is worked out from that row.
- Every answer names the item it answers. A say-back and its answer carry `lexiconId` `ref:<answer id>`.
- The record keeps three layers for signs: the app's reading ("Signed answer", source MACHINE, "read by the app, not checked"), the officer's reading ("checked by the officer") and the subject's answer. Each set of choices is logged as shown before it appears; only the subject's tap records a meaning; "None of these" is logged as Unclear with the alternatives.

### 4.3 Content integrity

`assets/content/manifest.json` holds the SHA-256 of every content file. A failed file is never parsed: a failed text file stops new sessions, because the subject could otherwise be shown wrong words; a failed picture, recording or motion file is left out. The officer sees a notice either way. Model files (holistic tracker, sign reader, assistant) are checked when first used, not on every content load; a failed model is not used and the stage keeps a plain mirror.

### 4.4 Typed text into signs

Typed text is split into words (a possessive keeps its stem; runs of up to three words that form one entry, such as "thank you" or "how many", stay together). Each word becomes, in order of preference: the recorded ISL sign, the authored sign, then the everyday gesture (with Signs on); the everyday gesture (with Gestures on); fingerspelling (with Spell on; grammar words such as "is" and "का" are skipped); or a short pause that holds the word's picture (with Pictures only). With Signs on, words play in draft ISL order: time words first, the other words as typed, then negation, then the question word; the caption keeps the typed order and lights each word as it is signed. Hindi is spelled in Latin letters (पानी to PANI), dropping the final silent vowel. A question mark adds the asking face. The ordering is a draft until Deaf review.

### 4.5 Show-back and say-back

A meaning is never inferred silently. When the subject gestures, the officer describes the place and action; candidates are ranked by both, then place, then action; the subject sees "Is this it?". Where the item has other pictures, the subject picks among two or three of them, and only that pick makes the meaning theirs (Confirmed); a Yes to a single reading is their Yes, and the reading stays the officer's.

### 4.6 Assistant

The model file (Gemma 3 1B, 806 MB) is imported once in Settings. Its SHA-256 is computed while copying, and only allow-listed files are kept. It is offered only on 64-bit phones reporting about 2.4 GB of memory or more, and loads on first use only when 1.2 GB is free; it is freed when the app locks. Each request starts from an empty context and uses greedy decoding, so the same input gives the same output. It translates the officer's text into the subject's language and then back into English; the officer sees both, marked "machine translation, not checked", and decides. The character still signs the officer's own words. It takes no part in Auto.

### 4.7 Pairing a second screen

- Both devices are paired once in system Bluetooth settings. On the stage device the officer cancels the first unlock prompt and taps **Use as stage screen**; the device remembers only that it is a stage device.
- The officer opens **Second screen**, picks the device and types the 6-digit code it shows. Their phone then shows a 4-digit check number; the stage shows the same number once the acceptance arrives, and the stage taps **Match** only if the numbers agree. Three wrong codes close the link.
- The link is secure RFCOMM to the bonded device, with P-256 ECDH (commit then reveal), HKDF-SHA256 and AES-256-GCM frames with strict counters. Both builds must have the same version and content.
- The officer phone sends full snapshots of what is on stage; the stage answers, and each answer is acknowledged after it is committed, de-duplicated and linked to its item. Late answers are marked.
- Both sides ping every 10 s; after 30 s of silence the link is lost. A resume within 120 s needs no code. The officer gets **Reconnect** and **Use this phone**. While the officer is out of the app the stage shows Hold; the 5-minute relock ends the link.
- Sign-reader guesses are not sent over the link in this version.

### 4.8 Roles and rights

The role is "Not known yet" until the officer sets it, and it is exported only when set. With the role not known only shared groups appear. The sensitive group appears for a victim; the rights groups for an accused. The rights items say: grounds of arrest at once (BNSS s.47); bail if bailable, with surety (s.47); a relative or friend told (s.48); a lawyer during questioning (s.38); a government doctor, and a woman doctor for a woman (s.53); a Magistrate within 24 hours, travel time not counted (s.58, and s.187 for longer custody); no forced self-incrimination (Art. 20(3)). A young child, or an accused marked as possibly under 18, gets the child's rights under the Juvenile Justice Act instead of the adult arrest rights. Each rights item carries "rights must also be explained through a qualified interpreter" (an officer card and a subject line on the stage) and is recorded "shown, not proof of understanding". Disability rights (RPwD Act s.7(4)) appear for a victim. All rights wording is a draft for legal review.

### 4.9 Manual and Auto

Manual is the default. When a department allows Auto, a rule-ordered sequencer over the reviewed bank picks the next welfare item (rapport, ground rules, welfare, practical, break, closing) and the officer approves each one; it never proposes sensitive, free-recall, follow-up or rights items. Only a statement may follow a statement by itself, on the subject's Next, at most three in a row. Stop pauses Auto; a second Stop ends it. A typed question, a show-back or an event question sent by hand turns Auto off. Leaving the stage stays one tap.

### 4.10 Other mechanisms

| Mechanism | How |
| --- | --- |
| Leaving the stage | One tap on the lock, or both volume keys held for 1.5 s; a setting adds the fingerprint or PIN; Back and single volume presses do nothing |
| Tilt to show | When something is prepared, tipping the phone over its top edge toward the subject and holding it for about 0.6 s shows it and turns the stage 180 degrees. Turning the phone around sideways is not detected. The gravity sensor runs only while something is prepared; no camera is used |
| Volume keys | Volume Up shows what is prepared. Volume keys behave normally everywhere else and whenever TalkBack is on |
| Screen pinning | Starts at the first Show and ends with the session; Android locks the phone when pinning ends |
| Retention | WorkManager deletes the database and wrapped key after the retention period |
| Export | The PDF is drawn on the phone, encrypted with AES-256 under a 16-character password from 32 symbols without look-alikes (80 bits), and handed to Android's share sheet; temporary files are deleted afterwards |
| Overlay protection | The activity, every dialog, sheet and menu ignore taps that pass through another app's overlay |

### 4.11 Instant Hindi caption

With a Hindi-reading subject, Latin text the officer types is translated on the phone, without a model, 250 ms after typing stops. A reviewed English and Hindi pair in the content wins: it is matched on lower case with punctuation, articles and contractions removed. Next come about 30 sentence patterns (Do you want X? Are you X? Is X hurt? Where is X? Did someone hurt you? Show me X, Can you X? I will X, Do not X, How many X? and others) with nouns from the picture cards, the gesture lexicon and words.json, polite आप forms, honorific agreement for parents and officials, and "हम" for the officer so no gender is assumed. Last, if every word is known, a word-by-word draft in Hindi order. Otherwise there is no caption. The draft is shown marked "machine translation, not checked"; a tap dismisses it for that message, and Send makes it again from the final text. The character signs the officer's own words; an assistant suggestion replaces the caption. The signed-answer draft uses the same idea for common verbs ("मुझे पानी चाहिए").

## 5. The character in detail

The character is not a 3D model, a video or a machine-learning model. It is a parametric 2D puppet drawn in code with Compose Canvas and redrawn every frame from numbers.

| Part | Location | Content |
| --- | --- | --- |
| Drawing | `ui/avatar/Avatar.kt` | Head, hair, torso, arms, five three-joint fingers, brows, eyes and mouth as vector shapes with soft shading; rimmed arms, a lighter palm tone and nails on the back of straight fingers; a view box to the hips |
| Rig | `ui/avatar/Rig.kt`, `MeasuredShape.kt` | A 200 by 200 unit space, named body points, a two-bone arm solver or a recorded elbow, named and measured handshapes, face presets, keyframe interpolation with Catmull-Rom for recorded keys |
| Characters | `ChildBuild`, `AdultBuild`, `ChildLook`, `AdultLook` | One skeleton, two sets of proportions and colours |
| Motions | `assets/content/gestures.json`, `signs.json`, `isl_signs.json` | Keyframes: time, each hand's target point, handshape, palm roll and angle, optional elbow, face, head and body |

Each frame: the clock time selects two keyframes, which are blended with easing (or, for recorded keys less than 0.35 s apart, a Catmull-Rom curve); each hand is placed at a named point (for example `mouth`, `chest`, or `index_opp`, the other hand's index fingertip); the elbow is the recorded one or is solved from the shoulder and wrist; fingers bend by curl values; the face preset sets brows, eyes and mouth; breathing, blinking, sway and occasional glances are added; the result is painted as vectors. Because positions are named body points, one motion file plays on both characters. The reviewer portal draws the same motions through a TypeScript port of the rig, checked against `Rig.kt` by a shared fixture file.

| Strengths | Limits |
| --- | --- |
| Small; offline; a motion is corrected by editing numbers; motions chain smoothly; faces carry ISL facial grammar; 415 signs follow real signers | Flat 2D front view with faked depth, so fingers pointing at the camera read as bent; the alphabet, authored and recorded signs await ISL review; no mouthing; cartoon style |

A 3D rig (VRoid, Blender, glTF or VRM) rendered with Filament is the upgrade path if 2D cannot show what reviewers need ([avatar and gestures](05-avatar-and-gestures.md) §5.4).

## 6. Camera and sign reading

### 6.1 Sign windows

The camera's frames are analysed only inside an explicit sign window:

| Window | Opened by | Length |
| --- | --- | --- |
| Answer | "Read signs" (officer) or the stage hand button | Up to 20 s |
| Account | "Tell me" (More page) | Up to 3 minutes |

Outside a window the mirror is a live preview with no analysis, or off per the camera setting. Inside a window MediaPipe Holistic runs on the GPU (the CPU when the GPU fails) on 16:9 frames of about 640 by 360, at most one frame per 30 ms. The subject sees a hand-and-arm skeleton over the mirror, never face points.

While signing:
- The stage shows one dot per sign seen. It does not show the reader's guesses, which are proposals in English for the officer.
- The subject is never asked up front to sign one sign at a time. Only after a failed read (best guess below 40%) of hands that were lost (more than 30% of the sign untracked, or gaps over 150 ms) or moved too fast (wrist faster than 2.5 frame widths per second) does a gentle "A little slower, please" cue appear, with a picture.
- Hands leaving the frame briefly do not end the capture.
- Segmentation splits at pauses. An answer splits after 0.4 s of still or absent hands, an account after 0.7 s, so its chunks are phrase-sized. Chunks shorter than 0.2 s are dropped.

Tell me:
- The stage says "I'm watching. Take your time." It shows no timer and no cues, only Stop and Done.
- Account landmarks keep hands and pose only. Face points are dropped (data minimisation, and it bounds memory to about 20 MB for 3 minutes).

For the whole session:
- Every signed answer and account stays in view-model memory, so an interpreter can replay it later at ¼× or ½× with sign markers, entering a word per chunk with the reader's top three guesses as chips.
- Landmarks are wiped when the session ends, when the app locks (the session screen is torn down) and when the view model is cleared. They are never stored or exported.

### 6.2 The subject owns meaning

- The app's reading, the officer's reading and the subject's answer are three separate layers (§4.2).
- A reading becomes the subject's answer only when the officer taps "Ask them to choose". The stage then shows, for each sign, two or three picture cards in the subject's language (the officer's reading first, then the reader's guesses, then the item's own answer cards, in shuffled positions) and "None of these".
- After a short sign window on the stage, the choices appear at once; the reader's guesses are never shown as words, and only the subject's pick records a meaning.

### 6.3 Context-limited vocabulary

- `assets/content/sign_words.json` (draft, needs ISL review) maps the reader's INCLUDE labels to the app's vocabulary concepts, for example house to home and bathroom to toilet.
- While a question with two or more answer pictures is on screen, every guess whose concept is one of those pictures ranks first, at any score; with one illustrative picture only the top three are reordered. A label can stand for several cards (`cards` in `sign_words.json`, for example police for a police station). On an unseen signer this puts the right lesson answer first 73% of the time and among the three shown 88% (`tools/ml/EVAL.md`).
- Core answer words the bundled reader cannot read: yes, no, water, pain, help, stop, don't know. Mother, father, police, home (house) and toilet (bathroom) are covered.

### 6.4 Engine and reader

| Component | State |
| --- | --- |
| Camera mirror (CameraX Preview) | Preview only; nothing captured or stored |
| Vision engine (MediaPipe Holistic, `tasks-vision` 1.0.0) | 21 points per hand, 468 face and 33 pose points per frame, GPU delegate with CPU fallback; 64-bit phones only; usage statistics dropped on the device |
| Segmentation, capture and slow replay | Landmark chunks in memory, slow replay at ¼× or ½× |
| Sign reader | A 262-word isolated-sign classifier trained on INCLUDE (CC BY 4.0), 1.4 MB, plain Kotlin; INCLUDE test top-1 97.8%, top-3 99.4% on studio signers; unmeasured with children on phone cameras |
| Partner team's model | Can replace the reader or tracker behind the same interfaces ([hand model integration](11-hand-model-integration.md)) |

No open model reads fast, continuous ISL reliably; the reader handles isolated signs over a known vocabulary, and the subject's pick decides the meaning.

## 7. Exporting and the network

Export works without a network. The PDF is written on the phone and handed to Android's share sheet; only the application the officer chooses (for example WhatsApp or email) uses its own connection. Sign Setu never connects. The PDF says "Communication note, not a statement. A statement needs a qualified interpreter and the statutory video recording." at the top and bottom of every page, followed by a grade legend.

| Item | Exported | Reason |
| --- | --- | --- |
| Answers, confirmed meanings, notes, typed and written answers | Yes, each with its grade in brackets, its choice position and its trail | Part of the session record |
| "Shown as" rows | Yes | What the person actually saw |
| Personal details given | Yes, as a table before the conversation: each field, the value, how it was given (written, number pad, picture, list, sign read back by the officer) and whether the person confirmed it | Lesson-sheet answers in one place |
| Assistant caption with the officer's own words | Yes | Both are logged |
| Readings by the sign reader or the officer | Yes, in a separate section after the conversation ("Readings by the app or the officer. These are not the person's own answers.") | Proposals, not answers; source MACHINE or OFFICER |
| Subject confirmations of a signed meaning | Yes, as ordinary answers after a "Shown: Is this it? A / B / C" row | The subject's own pick |
| Mode, who chose each item, suggestions not acted on | Yes | Auto accountability |
| Role | Only when set | "Not known yet" is not a fact about the person |
| Answers from a second screen | Yes, marked "from second screen" | Provenance |
| Landmarks, replays, stick-figure snapshots | Never | Biometric data |
| Camera video or photographs | Never | The application records nothing |

## 8. Technology stack

| Layer | Technology |
| --- | --- |
| Language | Kotlin 2.4.21; C++ for the assistant bridge |
| Build | Android Gradle Plugin 9.4.1, Gradle 9.8.1, JDK 21 (Java 17 bytecode), KSP 2.3.12, R8 full mode, version catalog, lockfiles, SHA-256 dependency verification, CycloneDX SBOM, per-variant permission build gate; product flavors `standard` and `nearby` |
| User interface | Jetpack Compose (BOM 2026.09.00), Material 3, Material Icons 1.7.8, Activity Compose 1.13.0, Fragment 1.9.1, Navigation 3 (1.2.0), Lifecycle 2.11.0, Core KTX 1.19.1 |
| Graphics | Compose Canvas for the characters, overlays, skeleton replay and drawing pad |
| Media | Media3 ExoPlayer 1.11.1 |
| Camera and vision | CameraX 1.6.2 with `camera-compose`; MediaPipe Tasks Vision 1.1.1 (Holistic landmarker) with `protobuf-javalite` 4.36.2 |
| Sign reader | Plain Kotlin forward pass; trained with Python tools in `tools/ml` |
| Second screen | Bluetooth RFCOMM (standard); Google Play services Nearby Connections 19.5.1 (`nearby` flavor only) |
| Storage | Room 2.8.5 (database version 4), SQLCipher for Android 4.19.1, androidx.sqlite 2.7.1, DataStore 1.2.1 |
| Security | androidx.biometric 1.1.0, Android Keystore, screen pinning, FLAG_SECURE; platform JCA for pairing (P-256, HKDF-SHA256, AES-256-GCM) |
| Background | WorkManager 2.12.0 |
| Export | Android PdfDocument, PdfBox-Android 2.0.27.0, BouncyCastle 1.86 |
| Data | kotlinx.serialization 1.11.0, coroutines 1.11.0 |
| Assistant | llama.cpp v0.5.0 through NDK r30 and CMake 4.1.1; Gemma 3 1B GGUF model file |
| Fonts | Baloo 2 (SIL OFL) |
| Tests | JUnit 4.13.2; kotlinx-coroutines-test; Compose UI tests with the Accessibility Test Framework; Vitest through Vite+; Python unittest; Android Lint |
| Reviewer portal | Vite+ 1.0.0 (`vp`), React 19.3.0, TypeScript 7.0.2 |
| Content tools | Python: content manifest, sign generator, sign retargeting from INCLUDE, review record, placeholder speech (Indic Parler-TTS), sign-reader training |
| Not used | Networking libraries, analytics, crash reporting, Firebase, WebView, microphone, camera recording; Play services in the standard build |

## 9. Frontend architecture

### 9.1 Structure

```
MainActivity  window security, edge-to-edge, biometric prompt, pinning, volume keys, app language
 └─ AppRoot  no-learning keyboard, theme, gate
     ├─ StageDevice           when this device is a second screen (before the lock screen)
     ├─ FirstRunScreen        until first run is done
     ├─ LockScreen            while the database is closed
     └─ NavDisplay
         ├─ StartKey          StartScreen (Home)
         ├─ SettingsKey       SettingsScreen
         └─ SessionKey(id)    SessionRoute
               ├─ Mode.OFFICER  OfficerPane: Transcribe and More, PairedBar, AutoPanel
               └─ Mode.SHOW     Stage under the stage (or sun) theme
```

```
app/src/
  main/java/app/signsetu/
    camera/      CameraMirror, LandmarkTracker, HolisticTracker
    data/        content (Content, IslLexicon, TextSigning, Lesson, Completion), session, settings
    sign/        SignCapture, SignReader, LandmarkSignReader, SignReaders, SignChoices, SkeletonPlayer
    pair/        PairCrypto, StageWire, StageTransport, BluetoothTransport, StageLink, StageBridge, Drawings
    ui/pair/     StageDevice, SecondScreenSheet, PairedBar, LinkText
    ui/session/  OfficerPane, Stage, SessionViewModel, LessonInputs, IslDictionarySheet, AutoPilot, Interview, Handover, GroundRules, PauseLayer, ...
    ui/avatar/   Avatar, Rig, MeasuredShape, MiniFace
  standard/java/app/signsetu/pair/StageTransports.kt   Bluetooth only
  nearby/java/app/signsetu/pair/StageTransports.kt     Nearby Connections, Bluetooth fallback
  nearby/java/app/signsetu/pair/NearbyStageTransport.kt
  debug/, release/                                     LandmarkTrackers per build type
portal/                                                 reviewer portal (not shipped)
```

| Package | Content |
| --- | --- |
| `ui.start` | Home (profile, role, language) and first run |
| `ui.session` | Officer page (conversation, completions, runs, trails and grades, composer, auto panel, ISL dictionary), stage (character, caption, mirror, rails, lesson inputs, phrase board, opening, pause, glance strip, sign panels, drawing, writing), handover, sheets, session view model |
| `ui.pair` | The stage device's screens and the officer's pairing sheet |
| `ui.avatar` | Drawing, rig, measured hand shapes, small faces |
| `ui.settings` | Grouped settings cards |
| `ui.components` | Brand button, picture cards, speech player, overlay filter, guarded dialog |
| `ui.theme` | Colour schemes (officer, stage, sun), shapes, type, ambient light |
| `camera`, `sign`, `pair` | Vision engine, sign capture and reading, second-screen link |

### 9.2 State

The session view model combines the database's event flow with local state into one `SessionUi` value, collected with lifecycle awareness. The officer's draft is a sealed `Pending` value (question, typed text, cards, show-back, phrase, language finder, drawing, ground rules); a lesson-sheet question is a question with an answer type, and a dictionary sign is shown as a question with id `dict.<gesture id>`; what is on the stage is a separate `onStage` value, set only after it is logged. Landmark frames of signed answers live in the same local state, keyed by event, and are never persisted. Auto mode's state lives beside it in `AutoPilot`, memory only. Screens send events up as lambdas; the view model writes the event before the screen changes. Settings come from DataStore; content is an immutable bundle loaded once; screen-local state uses `rememberSaveable`. Wiring is manual through `AppContainer`, which also holds the at-most-one `StageLink`.

### 9.3 Rendering and theming

- The character's clock lives in state read only during drawing, so animation runs without recomposition; typed-text playback derives only the current word and letter, so only those changes recompose.
- Three colour schemes: slate and indigo for the officer, warm peach and teal for the stage, and black, white and yellow for sun mode. Shapes are 8, 12, 14, 20 and 24 dp; spacing tokens run from 4 to 32 dp; one outlined officer card (`OfficerCard`) and one choice tile (`ChoiceTile`) serve every officer screen; headings use Baloo 2 and the stage uses a larger type scale.
- The officer interface uses Android resources in English and Hindi; everything the subject sees comes from content files keyed by language, so a new subject language needs no code change.
- Touch targets are at least 48 dp and answer cards 96 dp; layouts reflow above 1.3 font scale; instrumented accessibility tests cover the stage states and the stage device screens in both themes and two screen sizes.
