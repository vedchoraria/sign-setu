# 07. Decision Log

Each decision records context, the choice, alternatives and consequences. Decisions are changed by adding a new entry that supersedes the old one, not by editing history.

Status: Proposed, Accepted, Superseded.

---

## D-01 Native Android with Kotlin and Jetpack Compose

**Status:** Accepted

**Context.** Production government app, distributed as an APK, oldest phone Android 10 with 3 GB RAM, on-device camera ML, strict security controls. The original plan chose React as a PWA for speed of development.

**Decision.** Build a native Android app in Kotlin with Jetpack Compose.

**Alternatives.**
- React PWA wrapped with Capacitor: fastest for a web team, but camera ML and the avatar run inside a WebView with more memory and lower frame rate; security controls need plugins.
- Flutter: good performance and smaller than React Native; acceptable second choice if the team knows Dart.
- React Native: largest builds of the cross-platform options in published comparisons.

**Consequences.** Smallest build, direct use of CameraX, MediaPipe, Keystore and key attestation. The team needs Kotlin skills. No iOS (out of scope).

---

## D-02 Landmarks on device; only landmarks leave the phone

**Status:** Superseded by D-14, then by D-25: landmarks are computed on the phone and never leave it.

**Context.** Sign recognition needs the subject's hands and body. Video of a child is highly sensitive and heavy on the network.

**Decision.** Run MediaPipe hand and pose landmarkers on the phone; send only landmark arrays, only when hands are visible.

**Consequences.** Lower privacy risk and bandwidth. The ML team must train on MediaPipe Tasks landmarks. Recognition quality is bounded by landmark quality.

---

## D-03 Recorded ISL clips for the triage set; skeleton avatar only for free text

**Status:** Accepted for human ISL clips on the triage set; the skeleton avatar is superseded by D-13. Needs a volunteer signer and reviewer.

**Context.** Studies show sentence comprehension from avatars is substantially lower than from human signers; ISL depends on facial grammar a skeleton may not show.

**Decision.** Record a fluent signer for every triage question; bundle the clips. Use the skeleton avatar only for free text.

**Consequences.** Better comprehension and full offline support for the most important flow. Needs recording and review time; clip size must stay small.

---

## D-04 Bhashini for speech recognition and translation

**Status:** Accepted for release 1.1 (D-15), subject to access and retention terms

**Context.** Need Hindi, English and regional languages; minors' audio should not go to foreign commercial providers.

**Decision.** Use Bhashini through the gateway. Consider on-device recognition later.

**Consequences.** Government-run, Indian-language focused. Dependence on Bhashini availability; retention terms must be confirmed.

---

## D-05 No AI image generation in v1

**Status:** Accepted

**Context.** The original plan included AI-generated images.

**Decision.** Remove it from v1. Use a curated picture set.

**Reasons.** Sends case details to an image service; risk of distressing or wrong images shown to traumatised children; latency and cost; curated cards cover the scenarios.

**Consequences.** Smaller scope; content team must curate a sufficient card set.

---

## D-06 Session data stored only on the phone, encrypted, short-lived

**Status:** Accepted

**Decision.** No server-side storage of sessions. SQLCipher with a Keystore-wrapped key; auto-delete after 24 hours by default; encrypted export to the official record.

**Consequences.** Minimal breach surface; no sync across devices; the official case system remains the record.

---

## D-07 Backend in India, behind a single project gateway

**Status:** Accepted for release 1.1 (D-15)

**Decision.** All partner calls go through a project-owned gateway hosted in an India region of a MeitY-empanelled cloud before production. Partner keys live only in the gateway.

**Consequences.** One pinned host in the app; keys out of the app; one place for logging and rate limiting. The gateway is an extra component to build and operate.

---

## D-08 No officer login in v1

**Status:** Accepted

**Context.** No department identity system integration is available for release 1.

**Decision.** Protect the app with the device credential and biometrics; authenticate the app to the gateway with Android Key Attestation. Revisit with department SSO (for example Parichay) after the pilot.

**Consequences.** No per-officer accountability on the server side; the session log on the phone is the record.

---

## D-09 No third-party analytics or crash reporting SDKs

**Status:** Accepted

**Decision.** No remote crash reporting; crash traces stay on the phone and are shared by officers on request.

**Consequences.** Less product telemetry; pilot feedback is gathered by interviews and forms.

---

## D-10 Zero licence and service cost

**Status:** Accepted

**Context.** The project must run with no paid software, services or accounts.

**Decision.** Use only free and open-source tools and free government services.

| Need | Free choice | Replaces |
| --- | --- | --- |
| Distribution | Mobile Seva AppStore (MeitY, free for departments), department MDM, signed APK on a gov.in page | Google Play (one-time fee) |
| App attestation | Android Key Attestation verified by the gateway | Play Integrity (needs Play) |
| Crash data | Local crash traces shared on request | Play Console vitals |
| Speech, translation | Bhashini | Commercial cloud APIs |
| Hosting (production) | NIC / MeghRaj government cloud through the department [confirm charges] | Commercial cloud |
| Hosting (development) | Team machine or a free-tier VM | Paid VM |
| TLS certificates | Let's Encrypt with a reused key, so the public-key pin survives renewal | Paid certificates |
| CI | GitHub Actions free tier or a self-hosted runner | Paid CI |
| Security tooling | Android Lint, Semgrep CE, MobSF, OSV-Scanner, OWASP ZAP, CycloneDX Gradle plugin, gitleaks | Commercial scanners |
| Libraries and models | Apache-2.0 / MIT / BSD (Compose, CameraX, MediaPipe, Room, SQLCipher Community, OkHttp, Media3) | |
| Fonts, symbols | Noto (OFL), Mulberry and OpenMoji (CC BY-SA 4.0), Jellow (permission needed), ARASAAC (non-commercial) | Paid symbol sets |

**Not free, and outside the team's control:** the CERT-In empanelled audit (paid to the auditor by the department; no free route exists), production hosting if NIC charges the department, and people's time (ISL signer, reviewers). Reduce audit cost by arriving with clean internal MASTG results so only one follow-up round is needed.

**Consequences.** No Play vitals, staged rollout or in-app updates; replaced by MDM, district-wise rollout and a gateway-enforced minimum version.

---

## D-11 National scale

**Status:** Accepted

**Decision.**
- Gateway is stateless and horizontally scalable; capacity planned per state as states join.
- Content (triage clips, captions, cards, audio) is packaged per language. Release 1 bundles every approved language in the APK because it has no network (D-15); downloadable packs are reconsidered in release 1.1 if the size budget is exceeded.
- Rollout state by state, district by district, each with trained trainers.
- Configuration per state limited to: languages, helpline numbers, auto-delete period. No per-state code.
- Bhashini quota and the partners' capacity confirmed before each state joins.

---

## D-12 Subject profiles decide what the subject sees and hears

**Status:** Accepted

**Context.** Most deaf children do not know ISL; hearing children may speak another language and be unable to read. One ISL-first output serves neither.

**Decision.** The officer picks a subject profile at the start with one tap, changeable at any time: *Young child* (gestures, pictures, audio), *ISL user* (human ISL clips, adult character, interpreter call), *Hearing, other language* (language finder, recorded audio, pictures), *Not sure* (pictures, Yes/No, drawing). Pictures and Yes/No are present in every profile.

**Consequences.** Content is organised by profile; the triage set needs captions, audio, gesture entries and ISL clips per question.

---

## D-13 Two original characters with a fixed, reviewed lexicon replace the skeleton avatar

**Status:** Accepted; supersedes the skeleton part of D-03 (see [avatar design](05-avatar-and-gestures.md))

**Context.** A skeleton has no face, so it cannot show ISL facial grammar, and may frighten children. Free text converted word by word into signs is not ISL and cannot be reviewed before a child sees it.

**Decision.** A child character and an adult character share one rig and one motion library. They play only reviewed entries from the gesture lexicon and the ISL lexicon, including a phrase bank of sentences with slots. Free text is shown as caption, pictures and audio, never signed. Delivered in stages: illustrated gesture cards in phase 1, pre-rendered clips in phase 2, real-time rendering only if the pilot shows the need. Characters are original; no commercial cartoon character is copied. The adult character has a simplified but expressive face; a blank "mask" face was rejected because it loses ISL facial grammar. With no budget, the team builds both characters in-house with VRoid Studio and Blender (see avatar design §9).

**Consequences.** Needs team time to learn the tools, plus volunteer performers and reviewers. The ISL team's work becomes build-time clip data instead of a runtime API. Human ISL clips stay for the triage set.

---

## D-14 Officer gesture lookup replaces camera sign recognition in v1

**Status:** Accepted (see critical review C-4, C-5); the camera part is superseded by D-20 and D-25. Gesture lookup and show-back stay.

**Context.** Public ISL datasets are adult signers; children's home signs are in no dataset. One phone's front camera cannot watch the subject while the officer reads the screen. A false "yes" is the worst failure the app can produce.

**Decision.** In v1 the officer describes a gesture (where on the body, what the hand did), picks a likely meaning, and confirms it by showing the gesture back to the subject. Camera recognition moves to LATER, for ISL users only, runs on-device with the rear camera, and always ends with the same show-back confirmation.

**Consequences.** No camera, no model and no landmark upload in v1; DPIA risk P-04 and threat T-30 drop. D-02 applies only when recognition returns.

---

## D-15 v1 works fully offline, with no INTERNET permission

**Status:** Accepted (see critical review H-1, H-7)

**Context.** The network features (free-text signing, server recognition, voice, translation) carry most of the security, audit and partner-agreement cost, and the first two are replaced by D-13 and D-14.

**Decision.** v1 ships without the INTERNET permission. Voice input through Bhashini, free-text caption translation, the gateway and attestation arrive in v1.1, after the pilot shows they are needed.

**Consequences.** Threats T-07 to T-13 and DPIA risk P-03 do not apply to v1; the first CERT-In audit covers the app only. Officers type instead of speaking in v1. D-04 and D-07 apply from v1.1.

---

## D-16 Call an interpreter

**Status:** Accepted (see critical review C-6)

**Context.** Bhopal and Indore police already run video-call helplines for deaf people with officers trained in sign language. A human interpreter outperforms any feature of this app for an ISL user.

**Decision.** A **Call an interpreter** action, always one tap away, opens a **video** call (sign language cannot be interpreted over a voice call) to the state's configured interpreter contact, in the app the state configures: Sandes, the NIC government messenger, whose video calls are available to organisation-verified users and the people they invite; otherwise WhatsApp, which most officers' personal phones already have and which the Bhopal and Indore helplines use. No call audio or video passes through Sign Setu. If a state has configured no contact, the button is hidden.

**Consequences.** Each state names who answers, at what hours: officers trained in ISL (the Bhopal model), a Deaf association or ISLRTC volunteers. This costs no money but needs the department's agreement. The child's video passes through the messenger's servers during the call (end-to-end encrypted in both), which the DPIA must record.

---

## D-17 Export as a password-protected PDF

**Status:** Accepted (see critical review H-2)

**Context.** The custom AES-256-GCM export in SEC-12 cannot be opened by a CWC member or a station writer without special software.

**Decision.** Export a PDF encrypted with AES-256 under a strong one-time passphrase shared out of band. Any PDF reader opens it. SEC-12 is amended accordingly.

**Consequences.** Adds one library (see [reuse map](09-open-source.md)). PDF password key derivation is weaker than Argon2id, so the passphrase must be long and random: 16 characters from a 32-symbol alphabet without look-alike characters (80 bits).

---

## D-18 Language finder for hearing children who speak another language

**Status:** Accepted (in scope; see critical review C-7)

**Context.** Rescued and trafficked children often speak Bengali, Nepali, Assamese, Odia or a tribal language and cannot read; captions do not reach them.

**Decision.** The subject sees language tiles; each plays a short recorded greeting in one language, so the child can show which one they understand. The triage set is then played as recorded audio in that language, with pictures.

**Consequences.** Mostly content work: recorded and reviewed audio per language, offline. Language packs follow D-11.

---

## D-19 The app runs on officers' personal phones

**Status:** Accepted

**Context.** Officers will install the app on their own phones. There is no mobile device management, and a personal phone may be old, unpatched, rooted, or shared with family members who know its PIN.

**Decision.**
- No reliance on MDM: no remote wipe, no enforced settings. Encryption, app lock, End session and auto-delete are the only data controls on a lost phone.
- First run guides the officer to turn on the phone's app-pinning setting, used while the phone faces the subject (critical review H-9).
- Every build stops starting new sessions a fixed period after its build date (default 180 days) and asks for an update, because an offline v1 (D-15) has no gateway to enforce a minimum version. Existing sessions can still be exported and cleared.
- Rooted phones get a warning (SEC-63); there is no attestation in an offline v1.
- The SOP requires a phone PIN that is not shared, End session after every conversation, and Clear all data and uninstall on transfer or retirement.

**Consequences.** A family member who knows the phone PIN can open sessions that have not been ended or auto-deleted (R-23). If the pilot shows that phones are commonly shared, add an app-specific PIN.

---

## D-20 The stage: the character above, a camera mirror below

**Status:** Accepted; supersedes the camera part of D-14; the `HandTracker` seam is replaced by `LandmarkTracker` (D-25); the stage layout is amended by D-37

**Context.** In the field the phone faces the subject for most of a conversation, so the subject's screen is the app's home. Children who cannot speak answer with their hands, and seeing their own hands next to the character helps them copy a gesture or show one. A partner team is building an on-device hand-detection model.

**Decision.** While the phone faces the subject, the screen is a stage: the character, the caption and the picture on top; a live front-camera mirror below, with Yes, No, "I don't know" and "Stop" always within reach. The camera is a mirror only: CameraX Preview, plus ImageAnalysis only when a hand model is installed; nothing is captured, recorded or stored. The officer is asked for the CAMERA permission at session start, can turn the mirror off for one person, and a department can turn it off in settings. The partner model plugs in through one interface (`HandTracker`): one upright frame in, 21-point hand landmarks out, on a background thread, with the frame discarded after each call. It is created by `HandTrackers.create()`: the release source set returns none until the model is delivered, and the debug source set offers a stand-in hand (`adb shell run-as in.signsetu touch files/demo_hands`) that checks the camera, analysis and overlay path end to end. Its output only draws an overlay; any meaning it suggests must pass show-back (FR-66).

**Consequences.** CAMERA becomes the app's only dangerous permission (NFR-34, SEC-42 amended; SEC-47 added). Threat T-16 returns with new controls; the DPIA camera row changes from recognition to mirror. The officer side becomes secondary: a panel behind the officer's credential.

---

## D-21 Characters drawn in code for release 1

**Status:** Accepted; changes the delivery stages of D-13, not its characters or lexicons; D-35 adds motions retargeted from recorded signers

**Context.** Pre-rendered clips (05 §5.4 stage 1) need VRoid Studio, Blender and weeks of team time before a child sees any gesture. The app needs a moving character with readable fingers from the first pilot.

**Decision.** Both characters are drawn in code with Compose Canvas on one 2D rig: jointed arms solved from a target position, hands with five fingers of three joints seen from the front, back or side, and a face with brows, eyes and mouth. Gestures are short keyframe files (`gestures.json`) written against named contact points (forehead, mouth, chest), so one file plays on both characters. The gesture lexicon, its review rules and the show-back step do not change. Debug builds include a gallery of every gesture on both characters for reviewers.

**Consequences.** No video files and almost no size cost; gestures can be corrected in the file and re-reviewed in minutes. A 2D figure carries less depth than a rendered clip; reviewers judge each gesture in the gallery, and a gesture that does not read in 2D gets a rendered clip later (05 §5.4).

---

## D-22 Typed text is signed word by word, with the officer choosing how

**Status:** Accepted; amends the free-text rule of D-13 and FR-13; amended by D-29 (word order) and D-35 (recorded signs)

**Context.** Officers in Chhattisgarh, including its Naxal-affected districts, need to say things the triage set and phrase bank do not cover, and reaching a prepared question took several taps. Free text shown only as a caption reaches nobody who cannot read. Grammatical text-to-ISL translation does not exist offline at no cost.

**Decision.** The composer at the bottom of the officer panel takes any text; Send shows it at once. The character plays it word by word, under four toggles the officer sets per person: **Signs** plays the ISL sign for a word (or, if it has none, its everyday gesture, which signers read faster than spelling), **Gestures** the everyday gesture a child who does not sign understands, **Spell** fingerspells any other word with the two-handed ISL alphabet (Hindi is first written in Latin letters, as Deaf signers spell it), and **Pictures** holds up the word's picture. Signers start with Signs, Spell and Pictures; everyone else with Gestures and Pictures. A question mark adds the asking face. The word being played is lit in the caption, and a spelled word shows its current letter beside the character. The vocabulary (`words.json`) lists each word in English, Hindi, Hindi in Latin letters and common Chhattisgarhi and Gondi forms; the alphabet, digits and new signs are generated into `signs.json` by `tools/content/build_signs.py`. Chhattisgarhi, Gondi and Halbi join the language finder as text only.

**Consequences.** Word-by-word signing is not ISL grammar, so the stage presents it as words, not sentences, and prepared questions stay the first choice. Every letter, digit, sign and vocabulary entry is a draft until the ISL and language reviewers sign it off in the content review record; the new languages need native speakers for their greetings and any recordings. The Type sheet is removed; pictures for a typed question are attached from the type box.

---

## D-23 The handle alone, without a credential, leaves the stage

**Status:** Accepted; amends FR-61, SEC-21 and SEC-46

**Context.** Officers met a fingerprint or PIN prompt on opening the app, again every time they took the phone back from the subject, and the phone locked when pinning ended. In the field this is several prompts a minute. The credential on the way back from the stage guards the notes and log against a subject who taps the officer handle, and a deliberate long press does that without a prompt.

**Decision.** Leaving the stage needs the officer handle held for 1.2 seconds; a ring fills while it is held, the panel opens on release, and a tap or Back does nothing. A department setting, off by default, adds the fingerprint or PIN after the hold. Opening the app first tries the Keystore key silently: within its 30-second window after the officer unlocks the phone, the app opens without a second prompt. Screen pinning is unchanged.

**Consequences.** One prompt per session at most: the phone's own unlock when pinning ends. A subject who discovers the held press can open the officer panel of their own session; departments that judge this a risk turn the setting on. Threat T-21's controls and test TC-03 change accordingly.

**Amendment.** At the product owner's direction a single tap on the handle replaces the held press. The handle stays small and out of the way; a subject who taps it reaches the officer panel of their own session, which raises T-21 to Medium unless the department turns the credential setting on.

---

## D-24 An optional on-device model translates typed text, with a back-translation

**Status:** Accepted; brings FR-68 forward from release 1.1 without a network

**Context.** Officers type in English or Hindi; many subjects read another language, and in the Naxal-affected districts there is often no network, so a cloud service such as Bhashini cannot be relied on. Small language models now run on mid-range phones, but they make translation mistakes, and a wrong question in a child interview can mislead.

**Decision.** An optional model file runs on the phone through llama.cpp. The assistant translates the officer's typed text into the subject's language and then back into English; the officer sees both, marked "machine translation, not checked", and chooses whether to show the caption. The character still signs the officer's own words. The model is Gemma 3 1B (Q4_K_M), imported from a file the department hands out and accepted only by its SHA-256; the app never downloads it and gains no network permission. It is offered only on phones with about 3 GB of memory or more. Details: [on-device assistant](12-on-device-assistant.md).

**Consequences.** The release APK grows by 5.9 MB; with the model loaded a session exceeds the 300 MB memory target, so it loads on first use and is freed when the app locks. The Gemma licence is not OSI open source; a fully open model can replace it by hash. Language reviewers spot-check output per pilot language. Asking the model to put sentences in ISL order was tried and dropped: it produced wrong output, and the rule-based word matching of D-22 stays.

---

## D-25 On-device vision engine, sign reader and sign windows

**Status:** Accepted; supersedes D-02 and the hand-model seam of D-20; delivers FR-22 as FR-84 to FR-87; the engine runtime and the context rule are amended by D-38

**Context.** A crying or frightened signer signs fast, and the officer cannot read it. The partner team's hand model was not delivered in time, and no open model reads continuous ISL. Isolated-sign recognition over a known vocabulary is achievable on a phone, and MediaPipe Holistic gives hand, face and pose landmarks on the device. Face and hand landmarks are biometric data, and a camera that analysed every frame would watch the subject without a reason.

**Decision.**
- **Engine.** MediaPipe Holistic (`tasks-vision` 1.0.0, Apache-2.0) runs in video mode behind the `LandmarkTracker` interface (GPU first, CPU fallback, D-38): one upright RGBA frame in; 21 points per hand, 468 face points and 33 pose points out. The model (`models/holistic_landmarker.task`, 13.7 MB, float16) is mapped from the APK, checked against the content manifest before it opens, and loaded off the main thread before the first frame. It ships for 64-bit phones only, and the release APK is arm64-v8a only.
- **Sign windows.** Frames are analysed only inside an explicit window. **Read signs** from the officer, or the hand button on the stage, opens a 20-second answer window; **Tell me** opens a 3-minute narrative window (D-27). Outside a window the mirror is a preview with no analysis. Inside one, analysis runs at up to 30 frames per second.
- **Segmentation.** A pause segmenter splits the stream where the hands are absent or still (mean speed of all hand points below 0.3 frame widths per second): after 0.4 s in an answer, 0.7 s in a narrative. Chunks shorter than 0.2 s, or with fewer than 3 moving frames, are dropped. Hands leaving the frame briefly do not end the capture.
- **Sign reader.** A 262-word isolated-sign classifier (`models/sign_reader.bin` and `.json`, 1.4 MB) trained on the INCLUDE dataset (CC BY 4.0) from the OpenHands pose release, run in plain Kotlin (`LandmarkSignReader`) with no new dependency. Input: hands and arms of one chunk, normalised to the shoulders. Output: every label with its probability. On the INCLUDE test split: top-1 97.8%, top-3 99.4%, measured on studio signers.
- **Context limit.** `sign_words.json` (draft, needs ISL review) maps reader labels to the app's vocabulary, for example house to home and bathroom to toilet. While a question is on screen, guesses whose concept is among that question's answer cards come first (ranked, not filtered, D-38).
- **Proposals only.** Reader guesses are proposals, in English, for the officer. They are never shown to the subject automatically and never sent over the second-screen link (D-26).
- **Slower cue.** The subject is never asked up front to sign one sign at a time. "A little slower, please" appears, with a picture, only after a failed read (best guess below 40%) of hands that were lost (over 30% of the chunk untracked, or gaps over 150 ms) or too fast (wrist faster than 2.5 frame widths per second).

**Consequences.** The release APK grows to about 45 MiB (NFR-05 amended). Face analysis enters the app; the DPIA records it as biometric processing in memory only (SEC-71). The core answer words yes, no, water, pain, help, stop and "don't know" are not in INCLUDE, so the reader cannot read them until more data is collected, and picture answers stay the main path. Accuracy with children on phone cameras is unmeasured. The partner team's model can replace the reader behind `SignReader` ([hand model integration](11-hand-model-integration.md)).

---

## D-26 A second screen over bonded Bluetooth

**Status:** Accepted; adds FR-72 to FR-76 and SEC-66 to SEC-70

**Context.** Turning one phone back and forth is slow, and a tablet is easier for a child to see. A second device must not weaken the offline rules: no INTERNET, no location, no scanning, and the subject's device keeps nothing.

**Decision.**
- A second phone or tablet, paired once in system Bluetooth settings, shows the subject's stage while the officer keeps their phone. The link is secure RFCOMM to a bonded device. The app never scans, advertises, makes itself discoverable or bonds. Rejected: Wi-Fi Direct and Wi-Fi Aware (sockets need INTERNET; discovery needs location or NEARBY_WIFI_DEVICES), BLE GATT (scan and advertise permissions; 23 to 517 byte chunks), USB (accessory mode and a cable) and NFC (Android Beam removed in API 29).
- The officer phone is the only store. It logs every event in its encrypted database before sending it, and sends full snapshots of what is on stage, which can safely be applied twice. The stage confirms each item with Displayed; each answer is acknowledged only after it is committed.
- Pairing uses P-256 ECDH with commit then reveal, HKDF-SHA256 and AES-256-GCM, a design reused from the MIT-licensed Ping project. The stage shows a 6-digit code that the officer types on their phone, which never shows it. Once the officer phone accepts the code it shows a 4-digit check number; the stage shows the same number when the acceptance arrives, and only then can the stage tap Match. Three wrong codes close the link.
- Reconnecting within 120 s uses a resume key held in memory and the pinned peer address, with no code and no tap.
- While Live the stage has no local exit. At the end it stays pinned on a hand-back screen until the stage device's own credential is given, which ends the task and the process.
- A build flavor `nearby` swaps in Google Nearby Connections behind the same `StageTransport` interface for departments whose phones have Play services. It adds scan, advertise, Wi-Fi state, NEARBY_WIFI_DEVICES and fine location (up to API 32) and has its own application ID. The `standard` flavor is the reference build, with no Play services and none of these permissions.
- Paired mode does not change the app's non-evidentiary status.

**Consequences.** BLUETOOTH_CONNECT is a runtime permission on Android 12 and later (SEC-42 amended). Two-device behaviour cannot be tested on the emulator, which has no Bluetooth; it needs the two-phone and tablet UAT script. Sign capture runs only on the officer phone's own stage in this version.

---

## D-27 Narrative mode, the subject's own meaning, and landmarks kept for the session

**Status:** Accepted; amends FR-66 and SEC-47

**Context.** A traumatised person may need to narrate what happened, not answer one question. Machine and officer readings of signs are interpretations; a reading treated as the person's own words contaminates the account (BNSS s.173 and s.183(6), BSA s.125, POCSO s.26). A later interpreter needs the signing itself, not only the app's words.

**Decision.**
- **Tell me** (More page) opens a 3-minute narrative window. The stage says "I'm watching. Take your time." and shows no timer and no cues, only Stop and Done and one dot per sign seen. Narrative frames keep hands and pose only; face points are dropped, which also bounds memory to about 20 MB.
- Every signed answer and narrative stays in view-model memory for the whole session, keyed by its event, so an interpreter can replay it at ¼× or ½× with sign markers and enter a word per chunk, with the reader's top three guesses as chips.
- Landmarks are wiped when the session ends, when the app locks (the session screen is torn down) and when the view model is cleared. They are never written to disk, logged or exported.
- The record keeps three layers: the app's reading ("Signed answer", source MACHINE, "read by the app, not checked"), the officer's reading ("Signed answer", "checked by the officer") and the subject's answer. A reading becomes the subject's only when the officer taps **Ask them to choose** and the subject picks one of two or three picture cards (the officer's reading, then the reader's guesses, then the item's own answer cards, shown shuffled) or "None of these". Each set is logged as shown before it appears; "None of these" is logged as Unclear with the alternatives.

**Consequences.** Memory holds biometric data for up to the length of a session (SEC-71, DPIA P-11). An app reading never reaches the export as an answer; it appears in a separate readings section.

---

## D-28 Victim and accused roles

**Status:** Accepted; adds FR-88

**Context.** The same first conversation happens with victims, witnesses and accused persons, and the appropriate questions differ: an accused must not be led into an account (Constitution Art. 20(3), BSA s.23) and has rights to be told on arrest (BNSS s.38, 47, 48, 53 and 58). Officers often do not know the role at first contact.

**Decision.**
- A session's role is **Not known yet** until the officer sets **Victim or witness** or **Accused or in custody**. While it is not known only shared groups are offered, and the export omits the role.
- The sensitive group is offered for a victim only, and the free-recall group "Their account" for a victim or an unknown role, never an accused. For an accused the rights groups are offered: shared rights (grounds of arrest, a lawyer during questioning, a government doctor, no forced self-incrimination), adult arrest rights (bail and surety, a relative or friend told, a Magistrate within 24 hours) and a child's rights (no lock-up or handcuffs, parent or guardian told, a child welfare police officer, the Juvenile Justice Board within 24 hours). Disability rights under the RPwD Act s.7(4) are offered for a victim.
- A group may carry a `child` flag: true for a person who may be under 18, false for adults only. A young-child profile, or an accused the officer marks as possibly under 18, gets the child's rights and never the adult ones. Setting the accused role shows the officer the Juvenile Justice notice (logged `jj_notice`); the mark is logged as `juvenile` or `juvenile_off`.
- Rights are shown in pictures and signs, each with "rights must also be explained through a qualified interpreter" (an officer card and a subject line on the stage). The record marks each "shown, not proof of understanding".

**Consequences.** Every rights text, in English and Hindi, is draft content that needs a legal translator's and the legal adviser's sign-off before field use. Database version 2 adds the session role (migration 1 to 2; older rows read as Victim).

---

## D-29 Typed text in ISL order, as a draft

**Status:** Accepted; amends D-22

**Context.** Word-by-word signing in English order is hard for Deaf readers. Published descriptions of ISL place time signs early, negation after the verb and the question sign last, but the rules vary between signers and have not been checked by Deaf reviewers for this app.

**Decision.** With **Signs** on, typed text plays in ISL order: time words first, the other words as typed (grammar words dropped), then negation, then the question word. Gestures-only and spelling-only playback keep the typed order. The caption keeps the typed text and lights each word as it is signed. Hindi "kal" plays the general time sign, because it means both yesterday and tomorrow.

**Consequences.** The ordering and every new word sign stay drafts until ISL review on the content review record. Non-manual grammar beyond the asking face is not generated. A setting that turns the reordering off independently of the Signs toggle is not yet built and is required before Deaf review is complete.

---

## D-30 Manual and Auto conversation modes

**Status:** Accepted; adds FR-77 to FR-83; D-23 unchanged

**Context.** Officers asked for help choosing the next question. A free-running assistant would lead a child, and an on-device language model cannot be trusted to choose or word interview questions.

**Decision.**
- **Manual** is the default every session. **Auto** is available only when "Allow automatic conversation on this phone" is on in Settings.
- Auto is a rule-ordered sequencer over the reviewed bank: a pure function of the session log, the bank and the role. It proposes only from the RAPPORT, GROUND_RULES, WELFARE, PRACTICAL, BREAK and CLOSING phases, and never sensitive, free-recall, follow-up or rights items, for any role. A question with no phase is never proposable.
- Rules: R1, an item last answered unsure or Stop is not proposed again; R2, after an unsure answer to an option item, other option items of that group wait; R3, two unsure answers in a row offer a break first; R4, Stop pauses Auto; R4b, after a resume "Can we carry on?" comes first; R6, a follow-up is eligible only when its condition holds on an unchanged answer, and then goes first; R7, groups are gated by role; R8, when nothing else is left only closing items are proposed. R5 is not used.
- Every question needs one officer tap. Only a statement may follow a statement by itself, on the subject's Next tap, at most 3 in a row.
- The on-device model ranks nothing and writes nothing; it only translates for officer approval (D-24).
- A manual send of typed text, a show-back, a sensitive item, or a free-recall or follow-up item turns Auto off (`mode_manual_substantive`). Stop pauses Auto; a second Stop in the same Auto run ends it.
- Leaving the stage stays one tap in Auto too (D-23).

**Consequences.** Every suggestion, approval, skip and automatic advance is logged with item ID, source and reason (database version 3); the report shows the mode, who chose each item and suggestions not acted on. Auto state is memory only: after a lock or restart the session reopens in Manual.

---

## D-31 A static reviewer portal for content sign-off

**Status:** Accepted

**Context.** ISL, language and child-welfare reviewers sign off content without an Android phone or developer tools, and the record of their verdicts must be reproducible.

**Decision.** `portal/` is a static, offline web page built with Vite+ (the `vp` command line), React 19.3 and TypeScript 7.0, with no backend. It reads the app's content files at build time. Screens: ISL review (every motion on both characters through a TypeScript port of the rig, with speed and scrub), language review (captions and recordings per language) and child-welfare review (question wording and cards, with hints from the research avoid list; the hints are prompts, not a safety check). Each item takes Approve, Change or Reject, notes and the reviewer's role. Verdicts stay in the reviewer's browser and are exported as JSON, kept as the review record.

**Consequences.** The TypeScript rig must match `Rig.kt`: `RigFixtureTest` writes `portal/src/rig-fixtures.json` and fails when the Kotlin rig changes. After a deliberate rig change the fixture is regenerated and `rig.ts` updated until `vp test` passes. The portal is a tool and is not shipped in the app.

---

## D-32 Pinned stack and upgrade rule

**Status:** Accepted

**Context.** Dependencies are pinned and verified (SEC-50). An upgrade changes the lockfile, the verification metadata and possibly the merged manifest.

**Decision.** Libraries are taken only as stable releases at least two weeks old. The pinned set includes MediaPipe `tasks-vision` 1.0.0, NDK r30 (`30.0.16248370`, 16 KB page alignment by default), CMake 4.1.1 with llama.cpp v0.5.0, JDK 21 for the build (Java 17 bytecode), and for the portal vite-plus 1.0.0, React 19.3.0 and TypeScript 7.0.2. Every other version is in `gradle/libs.versions.toml`.

**Consequences.** Each upgrade regenerates `app/gradle.lockfile` and `gradle/verification-metadata.xml` and reruns the permission gate. PdfBox-Android 2.0.27.0 has no newer release and remains a maintenance risk.

---

## D-33 MediaPipe usage statistics are blocked at build time

**Status:** Accepted; implements SEC-72

**Context.** MediaPipe's task runner reports usage statistics through Google's data transport library, whose upload backend (`transport-backend-cct`) declares INTERNET. The permission gate (SEC-30) refuses a build that contains it.

**Decision.** The upload backend is excluded from the dependency graph. A stub destination named `none` (`com.google.android.datatransport.cct.CCTDestination`) satisfies the task runner; no backend is registered for it, so the transport runtime drops every event on the device.

**Consequences.** Nothing is sent and the merged manifest has no INTERNET. The transport runtime may keep a small local statistics database (task name, latency counts, device model) that never holds images or landmarks; it is excluded from backup with the rest of the app's data. Every MediaPipe upgrade is checked for new transport dependencies.

---

## D-34 A layered, graded record and a fairer stage

**Status:** Accepted; adds FR-89 to FR-101; the stage's pause answers are amended by D-37

**Context.** Research on child interviewing and AAC shows that what the person saw, the order of the choices and how long they took matter as much as the answer, and that someone else's reading must never pass as the person's own words.

**Decision.**
- After each item shown, a "Shown as" row records exactly what the person saw: the caption in their language, the motions played, the pictures in the order offered, and the content version (the first 12 hex characters of the content manifest's SHA-256).
- Every answer line carries a grade (Said; Confirmed, when the person picked this meaning from two or three choices shown back; Officer's reading; App's reading; Said no; Unclear) and an answer trail (time taken, every change, pause length).
- Exports say "Communication note, not a statement. A statement needs a qualified interpreter and the statutory video recording." at the top and bottom of every page, with a grade legend, and print readings by the app or the officer in a separate section.
- The stage opens with the officer's introduction, four ground rules and a practice round. Picture choices are shuffled and capped at four, plus "Something else" and "I don't understand". Stop and Break open a calm pause that only the person's "I'm ready" ends.
- Thumb-up, thumb-down and open palm are not used for yes, no or stop; Stop is an octagonal sign. No body map is shown to the subject in the default flow; the body outline stays an officer tool for gesture lookup.

**Consequences.** EventType gains PROMPT, with no schema change beyond D-30. The subject-screen words added for these features exist in English and Hindi only, so other subject languages show the pictures alone until they are translated.

---

## D-35 Recorded ISL signs and one sign lexicon

**Status:** Accepted; amends D-22 and D-29

**Context.** Authored word signs are slow to make and read as drawn approximations. The INCLUDE pose release (CC BY 4.0) holds MediaPipe Holistic landmarks of Deaf signers for 262 isolated ISL words. Typed text, questions without a gesture of their own, the sign-back of a reader label and an officer reference each needed a word lookup.

**Decision.**
- `tools/isl/retarget.py` retargets one recording per INCLUDE word onto the adult rig and writes `isl_signs.json` (ids `isl.<word>`, 16 categories, Hindi glosses from `tools/isl/hindi.json`). Each key carries an anchor with an offset, a measured hand shape written as `c:` percentages, palm roll, hand angle and the signer's elbow as `ex`/`ey` from the shoulder.
- Words the lesson sheet needs and INCLUDE lacks (for example what, where, how many, age, understand, again, district, state, class, number, near, like, please, live, village, water, favourite, subject) are authored drafts in `signs.json`.
- One lookup (`ContentBundle.motionFor`, `IslLexicon.kt`) serves typed text, unsigned questions, sign-back and the dictionary: with Signs on, the recorded sign, then the authored sign, then the everyday gesture; with Gestures on, the gesture; otherwise fingerspelling or a picture beat. Homonyms (23 words such as fall, light and key) stay in the dictionary and are never used for typed text.
- Recorded keys less than 0.35 s apart are joined by a Catmull-Rom curve, so the hands pass through them; authored signs keep their holds and overshoot. A recorded elbow is used when both keys of a segment carry it, and eases from or to the two-bone solve when only one does.
- The More page opens an ISL dictionary: every recorded and authored sign by category, searched in English, Hindi or any written form; Show prepares the sign like a question.

**Consequences.** Every recorded and authored sign is a draft until Deaf review. The INCLUDE attribution travels with the app (open-source licences and the dictionary). Fingers pointing at the camera read as bent, a limit of the 2D rig.

---

## D-36 Lesson-sheet questions with stage answer inputs

**Status:** Accepted; adds FR-102 to FR-106

**Context.** The first conversation with a found child is mostly about who they are and where they live. Yes, No and pictures cannot carry a name, an age or a phone number, and an officer's reading of such an answer is not the child's own.

**Decision.**
- The bank holds the groups Personal, Family, Address, School and Everyday: name, age, mobile, introduction; father's and mother's names, family size, brothers and sisters, who they live with; where they live, village or city, district, state, a place nearby; school, class, teacher, favourite subject, what they like at school; and eight everyday phrases. The new items have phase MANUAL, so Auto never proposes them.
- A question may carry an answer type (NAME, TEXT, AGE, COUNT, PHONE, SIBLINGS, CLASS, STATE, DISTRICT, PLACE, PICTURES). The panel under the character becomes its input: a number pad, a 10-digit phone pad grouped 5 and 5 with "No phone", a written name or place, a class grid, a state then district picker (Chhattisgarh first, with its districts), or several pictures with Done.
- A written or typed answer is read back large and becomes the subject's own only on their Yes; one left unconfirmed when the item closes is logged as unconfirmed. The method and confirmation are stored in the answer row's `reason` as `given:<METHOD>:<0|1>`.
- `hiddenFor` and `roles` keep items from people they do not suit: a young child is not asked for a mobile number; an accused is not asked the welfare-only items (introduce yourself, favourite subject, what they like at school).
- Items marked `signed` get one motion from the text-to-sign path in draft ISL order.
- The report lists these answers in a "Personal details given" table: field, value, how it was given and whether the person confirmed it.
- A phrase board on the stage lets the subject say the eight everyday phrases; a tap is logged as the subject's own.

**Consequences.** The app collects identifying answers (names, phone number, address, school). They exist only as answer rows in the encrypted session store under the same retention; there are still no identity columns. The DPIA records the data category. Lesson inputs are not offered on a second screen, which keeps its usual answers.

---

## D-37 The stage in two halves, with Stop as the only pause

**Status:** Accepted; amends D-20 and D-34; adds FR-107

**Context.** On a phone the camera window was cropped and the answers competed with it for space; Break and Stop side by side read as one control to a child.

**Decision.**
- The stage splits in half: the character, caption and held picture on top; below, a camera window at the camera's own 9:16 shape, fitted so nothing is cropped, between two narrow rails of answers. Panels that need room (drawing, writing, choices, the language finder, lesson inputs, the phrase board) take 64% of the height.
- Left rail: Yes, "I don't know", "I don't understand", Draw, Write. Right rail: Next (in the opening or Auto), No, "That's wrong", the camera button that opens a sign window, Stop. Tool buttons use neutral colours so Stop stands alone.
- Stop is the only pause control on the phone's stage; it opens the pause layer. Break remains as Auto's item "Do you want a break?" and as the break screen of a second screen (FR-76).
- The character is framed to the hips (a view box of 220 by 258 units), so both hands stay in view at rest and at waist level.

**Consequences.** The camera window is narrower than the screen; rail buttons stay 56 dp tall. The stage's sign button is a camera icon, never an open palm.

---

## D-38 Vision engine runtime and ranked sign guesses

**Status:** Accepted; amends D-25

**Context.** On a mid-range phone the CPU ran Holistic at about 9 frames per second. MediaPipe 1.0.0's classes are compiled against the full protobuf runtime and fail at run time with the lite one. Limiting guesses to the item on screen left the officer with none when the reader lacked the answer word.

**Decision.**
- `HolisticTracker` opens the graph on the GPU delegate and falls back to the CPU when the GPU fails to open or the first inference throws; the next frame then reopens on the CPU.
- The build uses `protobuf-java` 4.26.1 and excludes `protobuf-javalite` from MediaPipe.
- Preview and analysis share a 16:9 portrait frame; analysis requests 640 by 360 and receives each frame rotated upright.
- Of the reader's top three guesses, those whose concept answers the item on screen are ranked first; the others stay, each group by score (`inContext`). A less likely guess is never lifted above them because it fits the item.

**Consequences.** Frame rate depends on the phone's GPU. Context ranking no longer guarantees that every guess fits the question, so the subject's pick (FR-86) remains the only way a meaning becomes theirs.

---

## D-39 ASL as a second sign language, from PopSign and ASLNow!

**Status:** Draft; every ASL sign, pairing and model needs review by Deaf ASL signers before use with a person

**Context.** The officer may meet a signer who uses ASL, which is used in some Indian schools. ISL and ASL differ in vocabulary, grammar and fingerspelling. Google's Kaggle sets for ASL may not be redistributed; PopSign ASL v1.0 (CC BY 4.0, the same 250 signs) and ASLNow! (MIT, static letters) may.

**Decision.**
- The sign language is a per-session setting, ISL or ASL (FR-108). It selects the reader, word list, fingerspelling reader and sign set (`SignLanguage`); the bundle is rebuilt for it, so ASL never plays an ISL motion.
- `asl_signs.json` holds 250 signs retargeted from PopSign signers by `tools/isl/retarget_asl.py`, plus the one-hand alphabet and the digits 0 to 9, authored as keyframes. Letters and digits are fingerspelling keys and are not dictionary entries.
- ASL text is signed and glossed in subject-verb-object order; the ISL time-first, verb-last ordering applies to ISL only.
- Missing ASL files degrade one by one (architecture §5); the ISL reader and signs are unaffected.

**Alternatives.** Kaggle `asl-signs` and `asl-fingerspelling` (redistribution barred, credentials needed); one merged lexicon (ASL and ISL words would collide).

**Consequences.** A reading, in either language, still becomes the subject's answer only through their pick (FR-86). Sources and licences are in the [open-source register](09-open-source.md).

---

## D-40 Live landmarks on the stage, everyday gestures as proposals, and signs learned in the session

**Status:** Accepted; adds FR-109 to FR-112; amends D-25 and SEC-71

**Context.** Officers saw a plain mirror outside sign windows and read it as hand detection not working. Many subjects do not sign: children, people with intellectual disability, and frightened adults answer with a nod, a thumbs up, a raised palm or a pointing finger. Some Deaf subjects use home signs that no dataset holds. ISL marks yes/no questions with raised brows, not a sign.

**Decision.**
- While the stage camera shows, frames are analysed for the overlay at up to 10 frames a second, and at up to 30 inside a sign window. Only inside a sign window do landmarks go to sign capture. The overlay draws hands, arms, brows and lips; the face oval and eyes stay off.
- Brows raised above the subject's own running baseline, over most signs of an answer, make its written draft a question. Mouth opening is marked per frame for later use.
- A nod or head shake, a thumbs up or down held 0.5 s, a raised open palm held 0.8 s, a shrug (both hands open, low and wide) held 0.6 s, and a finger held 0.6 s on the head, mouth, stomach, arm or leg only make the matching Yes, No, Stop, "I don't know" or picture pulse. Nothing is logged until the subject taps; inside a sign window these proposals are muted, because open hands and pointing are signs there. When the tap matches the proposal, the answer's record line says so, for example "(after a nod or thumbs up)".
- A wrist at the frame edge lights that edge of the camera window during a sign window.
- When the officer reads a sign as a word the reader did not offer, the sign's feature sequence is kept for the session and offered first when signed again (dynamic time warping). It lives in view-model memory only.
- A paired stage phone runs the same overlay and proposals on its own camera; only the cue name travels with the tapped answer, never landmarks.
- With no one in view for 10 s outside a sign window, the overlay slows to about 3 frames a second.
- The tuning values (brow raise, mouth opening, head swing, home-sign distance, pointing regions) are calibration settings to be set on devices.

**Alternatives.** Reading words INCLUDE lacks from one ISLRTC signer each by dynamic time warping (measured: 0.07 top-1 across 60 words on INCLUDE signers, too often wrong to offer); analysis only inside sign windows (the overlay looked broken); treating a nod or point as an answer (would put words in the subject's mouth); a per-subject trained model for home signs (needs storage and training on the phone).

**Consequences.** More frames are analysed, bounded at 10 a second outside sign windows. Landmarks and home-sign templates remain memory-only biometric data under SEC-71. Every proposal still needs the subject's tap, so FR-66 holds.

## D-41 A plainer stage for the person answering, and signed answers checked at once

**Status:** Accepted; amends D-20, D-25, D-37 and FR-97

**Context.** Field review found the stage confusing for a frightened person: Yes and No sat under statements that ask nothing, nothing said what to do next, the camera button had no word, the officer's upside-down strip looked like an error, a finished drawing dropped the person back to a greeting with the question gone, and a signed answer reached the person for checking only after the officer took the phone, read it and handed it back.

**Decision.**
- The introduction and ground rules show only Next and Stop; the practice round shows no Yes, No or "That's wrong", because it has no yes-or-no answer.
- One plain line over the camera window says what to do now: answer by tapping or signing, tap Next, or, inside a sign window, sign and tap Done. The camera button carries the word "Sign".
- The glance strip starts hidden; the officer turns it on from the session menu when sitting opposite.
- The drawing pad has five ink colours (dark, red, blue, green, brown) and Undo. A drawing given as an answer to a question returns to that question with the usual acknowledgement. A drawing the officer asked for ends on a hand-back screen: a tick, "Please give the phone back to the officer" and "Draw more". The officer's conversation shows the drawing itself; a tap opens it full size.
- When a short sign window ends on the stage, the app's reading is put to the person straight away: two or three picture choices per sign and "None of these", asked once per distinct reading and for three signs at most, logged as shown before they appear, exactly as "Ask them to choose". Only their pick records a meaning; "None of these" on the last sign opens a new sign window. The question stays on the stage afterwards. Tell me accounts and paired second screens keep the officer-led path.
- On Home, the Signs profile offers Indian (ISL) or American (ASL) before the session starts.

**Alternatives.** Keeping the officer-led check only (two extra handovers per signed answer); showing the reader's top guess as text (would put words in the person's mouth); a guided tour before the first question (one more step for the person, and it would be skipped in practice).

**Consequences.** Machine readings now reach the subject without an officer action, but only as two or three choices among which their own tap decides; FR-66 holds and the record keeps the app's reading, the choices shown and the pick as separate rows. The new stage words are content strings and need review in every subject language.

## D-42 Full-precision models and an uncompressed APK

**Status:** Accepted; amends NFR-05 and the release packaging in D-25

**Context.** The 0.2.0 release halved the sign-reader weights to float16 and compressed the native libraries to stay under 50 MB. The owner asked that nothing be compressed when the app is built.

**Decision.** The ISL and ASL word readers and both fingerspelling readers ship as float32. Native libraries, the holistic model and the reader weights are stored uncompressed in the APK and load straight from it. The release stays arm64-v8a only. R8 shrinking stays on, because it also strips logging and debugging aids (SEC controls), not to save size. NFR-05 becomes a 100 MB ceiling.

**Alternatives.** Keeping float16 (readers differ from the trained weights by rounding); per-ABI splits (one APK per phone type complicates sideloading).

**Consequences.** The APK grows by about 17 MB; readers give exactly the trained model's scores; the installed size and load time drop because nothing is unpacked at install.

## D-43 Draft captions in every language of the language finder

**Status:** Accepted; amends section 3.2 of the product overview

**Context.** Subject-facing text existed only in English and Hindi, so a subject who reads Chhattisgarhi, Gondi, Halbi or another language picked in the language finder saw pictures with no caption. Most subjects in the focus region, Chhattisgarh including Bastar, speak one of the first three at home.

**Decision.**
- Every subject-screen word, question, phrase and card label carries Chhattisgarhi (hne), Gondi (gon) and Halbi (hlb), Gondi and Halbi as spoken in Bastar and written in Devanagari; lexicon labels carry these three as well.
- The same texts, except lexicon labels, carry Bengali, Nepali, Assamese, Odia, Tamil, Telugu, Kannada, Malayalam, Marathi, Gujarati, Punjabi and Urdu, each in its own script.
- Each content file holding these texts says in its status that everything beyond English and Hindi is a draft until a native speaker reviews it; rights items need that review and the legal review together before field use.

**Alternatives.** Pictures alone until reviewed translations exist (leaves most local subjects without words); Hindi as the fallback caption (many Gondi and Halbi speakers read little Hindi, and a half-understood rights text is worse than none).

**Consequences.** A wrong draft caption can mislead; pictures stay beside every caption and every answer still needs the subject's tap (FR-66). New subject texts need all seventeen languages or show pictures alone in the missing ones.

## D-44 Instant English to Hindi captions without a model

**Status:** Accepted; amends D-24

**Context.** Text an officer types reached a Hindi-speaking subject in English unless the optional 806 MB assistant was imported and asked, which is slow and takes a button press. The written draft of a signed answer gave Hindi verbs in their dictionary form ("मैं पानी चाहना").

**Decision.**
- When the subject's language is Hindi and the officer types in Latin letters, a Hindi caption is made on the phone as they type (after a 250 ms pause) and shown under the composer, marked "machine translation, not checked". A tap dismisses it for that message; Send makes it again from the final text. The character still signs the officer's own words; an assistant suggestion, when asked for, replaces it.
- The caption comes, in order, from: a reviewed English and Hindi pair in the content (questions, phrases, subject screen text, gesture lexicon, picture cards), matched on lower case without punctuation, articles or contractions; one of about 30 sentence patterns for welfare and police questions, using polite आप forms, honorific agreement for parents and officials, अपना where the subject owns the thing, and gender-free "हम" for the officer; or word by word in Hindi order. Any unknown word gives no caption rather than part of one.
- The signed-answer draft gives common verbs their Hindi present form with the doer in को ("मुझे पानी चाहिए", "मुझे घर जाना है").

**Alternatives.** Making the assistant model mandatory (size and speed); a bundled phrase-based translation model (new dependency, still unreviewed output); word-by-word output with gaps (puts half-sentences in front of a frightened subject).

**Consequences.** Every caption is still an unchecked draft under D-24 and is logged with the officer's words. Hindi reviewers should check the sentence patterns in `Translate.kt` with the content. Other subject languages still need the assistant.

## D-45 Picture quality gate and one read policy, from the team's architecture sketches

**Status:** Accepted; amends D-25

**Context.** The team's sketches show a Flutter or Expo client with a FastAPI, Keycloak and PostgreSQL backend for optional clip upload, plus an on-device pipeline with a frame rate and blur check ahead of tracking and a policy step that abstains or asks for clarity. The app is native Android and offline by requirement (no INTERNET permission, D-20), so the backend, upload, accounts and cross-platform client are not adopted: clips are never uploaded, and the existing controls already match the rest (landmarks only in memory, SHA-256 verified model files, encrypted sessions, signed evidence export, abstaining reads that the subject confirms).

**Decision.**
- While a sign window is open, each analysed picture is scored for blur (variance of a Laplacian over the centre, against the best recent picture of the session). The score is a flag on the landmark frame; the picture is not kept.
- A sign whose pictures are mostly blurred, or analysed at under about 8 frames a second, and whose best guess is below `READ_MIN_SCORE`, shows the existing "sign slower" cue, like fast or lost hands.
- The step from reader output to the segment shown (home sign first, item context, three candidates, slower cue) moves out of the view model into `decideRead`, and the per-frame stages into `FramePipeline`, so each can be tested without the camera or a view model.

**Alternatives.** A new "poor light" cue (new subject text needing review in every language); a cloud review queue (breaks the offline rule); measuring blur on every frame outside sign windows (cost with no use).

**Consequences.** The blur ratio, peak decay and minimum peak in `FramePipeline.kt` are calibration knobs to tune on device. A sure read is never blamed on the picture.

## D-46 Skeleton snapshots in the record are opt-in

**Status:** Accepted; amends D-25 and SEC-71

**Context.** Landmarks are biometric data (SEC-71) and are never to reach disk or the second-screen link. The skeleton evidence snapshot was switched on by default with no control, so every signed answer wrote a drawing of the subject's pose to the record, and the second screen sent one to the officer's phone. The team's sketches ask for the same rule in other words: nothing leaves memory without explicit authorisation.

**Decision.** The snapshot is off at every start. The officer turns it on from the session menu ("Keep skeleton snapshot in record"). The second screen draws and sends one only when it is on, and the officer's phone drops a received one unless it is on.

**Alternatives.** Keep it on and disclose it (conflicts with SEC-71); remove it (loses a record of what was read for officers who want one); a stored consent per subject (more state to protect, and the officer already decides what the session keeps).

**Consequences.** Switching it on is an officer's decision per run of the app. The second screen has its own switch state, so a paired stage sends a snapshot only if its own switch is on.

## D-47 Hybrid connectivity: offline always works, the department gateway adds to it when online

**Status:** Accepted; supersedes D-15 for the network rule, implements D-07 and SEC-31 to SEC-34

**Context.** The product must work with no connection and use a proper backend when there is one. The code already declared INTERNET for an optional online assistant that sent masked session text to a third-party service with a key typed on the phone, which broke D-07 (partner keys only in a project gateway) while the documents still said "no INTERNET". The team's sketches add a FastAPI, Keycloak and PostgreSQL backend.

**Stacks checked before choosing (October 2026).** FastAPI 0.146.0 (0.x, so pinned exactly), SQLAlchemy 2.1.5, Pydantic 2.14, PostgreSQL 18, Keycloak 26.8.1 (13 October 2026), OkHttp 5.5.0 stable, AppAuth-Android 0.11.1 (last release December 2021). No beta or release-candidate component is used.

**Decision.**
- The app is offline first. The `CloudAi` client talks only to the gateway at `BuildConfig.GATEWAY_URL` (set with `-PgatewayUrl=https://...`). With no URL, no switch is shown and nothing connects. With one, a call is made only if the officer switched the assistant on, the device is enrolled, and the network is validated; any other case, any error and any 401 return null and the on-device path runs. A 401 clears the token.
- Device identity is a one-time enrolment code issued by the department, traded for a random token the gateway stores only as a SHA-256 hash. The token is wrapped by a Keystore key. Accounts and passwords are not used on the phone, so AppAuth-Android (stale) is not needed.
- The gateway (`backend/`) offers: enrolment, a model manifest with verified download, an assistant proxy that holds the AI key and stores no text, consented label feedback that refuses any extra field (no landmarks, pictures or statements), an audit table without subject data, rate limiting, and an admin read of feedback behind a Keycloak access token with the reviewer role.
- Platform HTTP and the network security config are used instead of OkHttp: a handful of JSON calls do not justify a dependency, and the platform supports certificate pin sets. Revisit if streaming or HTTP/3 is needed.
- Still offline by rule: video and landmarks never leave the phone; models are never replaced by downloaded ones until a signed manifest is verified on the phone (SEC-54, SEC-74).

**Alternatives.** Two flavors, one with INTERNET (a second APK to review and ship); OkHttp 5.5.0 (a new dependency, no gain here); AppAuth with Keycloak for officers (stale library, passwords on personal phones); keeping the third-party service (keys and unreviewed processor).

**Consequences.** The third-party key and its screen are gone. Certificate pins go into the network security config when the gateway host is fixed. Key Attestation at enrolment (D-07, SEC-61) is not built; the one-time code is the only enrolment check until then. Documents that still say "no INTERNET" are amended here: the build gate now refuses RECORD_AUDIO and background location only.
- A build with no gateway URL but an assistant key (env `OPENROUTER_API_KEY` or `openrouterKey` in the untracked `local.properties`, model `nvidia/nemotron-3-ultra-550b-a55b:free`) calls that OpenRouter service directly, in its own format, under the same switch, masking, fallback and toasts. The settings text then says "third party" and names OpenRouter. The key is compiled into the APK and can be extracted from it; use a revocable, rate-limited key, and prefer a gateway build for fielded phones. The gateway reads the same service from `backend/.env` (`ASSIST_URL`, `ASSIST_MODEL`, `ASSIST_KEY`).
- The officer sees a toast once when the online assistant falls back (no internet, gateway error, device no longer enrolled) and once when it returns, and after enrolling; an unenrolled or switched-off assistant stays silent.

## D-48 The database key works only while the phone is unlocked

**Status:** Accepted; amends SEC-22

**Context.** The key-encryption key had been changed to need no authentication, so that unlocking the app does not ask twice. That left the session database readable whenever any code ran as the app, including while the phone was locked. The database holds statements and children's data, and DPDP Act s.8(5) requires reasonable security safeguards.

**Decision.** The key is created with `setUnlockedDeviceRequired(true)`. Officers see no extra prompt. On the first unlock after the update, a database key wrapped by the earlier unbound key is rewrapped under the new one, so stored sessions are kept. A device that refuses the flag gets an unbound key, as before.

**Alternatives.** A fingerprint prompt every five minutes (strongest, but a second prompt during interviews); no binding (a UI gate only).

**Consequences.** Retention and other background work that needs the database must run while the phone is unlocked. The current retention worker deletes files without the key, so it is unaffected.

## D-49 MediaPipe 1.1.1 runs on the lite protobuf runtime

**Status:** Accepted; amends D-38

**Context.** The dependency update to MediaPipe 1.1.1 and `protobuf-java` 4.36.2 stopped the Holistic landmarker from opening: 1.1.1's classes are compiled against `protobuf-javalite` (`NoSuchMethodError` on `Any$Builder.build()`), and the full runtime from 4.36 also drops `DescriptorMessageInfoFactory`. The camera mirror showed no landmarks, and the on-device pipeline test found no hands or pose. A split of separate hand, pose and face landmarkers was measured as an alternative: it found as many hands but took 80 ms a frame on the emulator CPU against Holistic's 68 ms, because the hand landmarker searches the whole picture for palms while Holistic crops the hands from the pose.

**Decision.** The build uses `protobuf-javalite` 4.36.2, the runtime MediaPipe 1.1.1 declares, pinned above its own 4.26.1 for CVE-2024-7254. The full runtime and the test-classpath exclusion are removed. Holistic stays.

**Consequences.** `SignPipelineDeviceTest` must pass after any MediaPipe or protobuf update; a unit test cannot catch this.

## D-50 AI counsellor on a paired second screen, officer monitoring

**Status:** Accepted; amends D-30 and D-24 for this mode only

**Context.** Some victims are afraid of the police themselves and will not answer an officer. The product owner asked for an AI helper that talks with the person while the officer watches and can take over at any time.

**Decision.**
- The counsellor runs only when a second screen is paired: the person talks with it there, and the officer watches on their own phone. It is never offered for an accused person or anyone in custody (Constitution Art. 20(3)), and it needs the online assistant (D-47), switched on and enrolled.
- Each line is written by the online model from the session so far, with names masked, and is held on the officer's phone for 6 seconds: Send now, Another line, or Take over. Unless the officer acts, it is then signed and captioned on the second screen.
- The prompt asks for one short line per turn: open questions only, feelings acknowledged first, no suggested names, places, acts or details, no promises and no blame. A reply that flags risk or a wish to stop hands back to the officer, and so do Stop and break from the person. Only the first question of a reply is kept, at most 160 characters.
- Anything the officer sends or reads takes the conversation back. The run stops after 30 turns.
- Every line is logged as shown by the machine with reason `ai_counsellor`; the transcript and the report mark it "AI counsellor".

**Alternatives.** Officer approval of every line (Auto mode, D-30), which is slower than a frightened person will wait; a free-running model with no officer view (no control over leading questions); an on-device model (too weak for this, D-24).

**Consequences.** The masked session text goes to the gateway's model for each turn (DPIA P-03). Answers drawn out by the counsellor carry its mark, so their weight as evidence is for the investigating officer and the court to judge. Field use needs two phones; tested here by unit tests and against the gateway's model, not yet on two devices.

## D-51 Finished sessions are saved under the officer's name until deleted

**Status:** Accepted; the default retention is superseded by D-52; amends the retention default of SEC-08 and FR-06

**Context.** Ending a session deleted its record, and an export deleted it too. Officers found that strange: a report could not be made again, and a second look at what was said meant keeping the session open. The product owner chose to keep sessions until the officer deletes them.

**Decision.**
- Finish asks for a name (prefilled with the start time) and saves the session, encrypted, on the phone. Export report saves it as well; the exported file itself is still removed after sharing.
- The start screen lists saved sessions. Open brings one back to read, export or finish again; Delete removes it after a confirmation. "Delete without saving" at finish needs two taps.
- The retention setting gains "Keep until deleted", now the default. Any hour value still purges as before.
- The name is free text and may hold a person's name; it lives in the encrypted database like every answer.

**Consequences.** A lost or seized phone can hold many past sessions, protected by SQLCipher and the unlocked-device key (D-48); DPIA risk P-01 rises accordingly. Departments that want the old behaviour set a retention period.

## D-52 Sessions are purged after 24 hours by default

**Status:** Accepted; supersedes the default in D-51 [confirm with the product owner]

**Context.** D-51 made "keep until deleted" the default, so a lost or seized phone could hold every past session, children's included. The data protection assessment (DPIA P-01) relies on short retention as a mitigation, and DPDP Act s.8(5) requires reasonable safeguards.

**Decision.** The default retention is 24 hours from the session's start. "Keep until deleted" and 4 to 72 hours stay as the officer's or department's choice. Saved sessions are purged by the same rule; the purge runs at every unlock, and a background job destroys the store once even the newest session is past the period.

**Alternatives.** Keeping D-51's default and relying on the lock (more data on a lost phone); purging by end time (a session left open would outlive the period).

**Consequences.** A saved session disappears a day after it started unless the setting was changed; officers who want longer say so in Settings. Phones that already stored a retention value keep it. A phone that never stored one, which includes every 0.5.0 phone whose officer did not open the setting, was keeping sessions forever and will purge anything older than 24 hours at its next unlock; there is no way to tell that phone from one that chose the default, so officers who rely on saved sessions must export or change the setting before updating. Until this change, choosing "Keep until deleted" was stored as 4 hours by a clamp in `SettingsStore`; such phones show 4 hours and need the choice made again. The crash-recovery flag (`stage_live`) only makes the next unlock ask for the full credential; it restores no data, and the purge runs at unlock before the session list or the resume offer is built, so an expired session cannot come back through recovery.

## D-53 A rooted phone keeps no history

**Status:** Accepted [confirm with the department]

**Context.** SEC-63 only warned about a rooted phone, while the security document promised that session history is disabled. A rooted phone can read an app's files, so stored sessions are not safe there.

**Decision.** When the phone has test-keys or an su binary in a usual path, the start screen warns, lists no saved sessions, and finishing a session deletes it whatever name was typed; an export can be made first. The app still runs.

**Alternatives.** Warn only (stored sessions stay readable by root); block the app (fails an officer in an emergency); hardware attestation (needs a server, D-47).

**Consequences.** The check is a heuristic and a rooted phone can evade it; it reduces risk, not removes it. Unfinished sessions can still be resumed on a rooted phone until they end or expire.

## D-54 The gateway accepts only the app's own prompts and caps wrong enrolment codes

**Status:** Accepted; amends D-47

**Context.** `/v1/assist` forwarded any system prompt, so an enrolled device could use the department's model key for anything. `/v1/enroll` had no limit, and a per-address limit is defeated by a forged forwarding header.

**Decision.** A system prompt must match, in full, one of the app's seven prompt templates in `app_prompts.json`; slots for a language name accept one line of up to 80 characters. The Android unit test renders the templates from the prompt code and fails if the file is out of date, and the gateway tests send every template, and altered or extended ones, to the real route. Enrolment is limited per address and to 20 wrong codes a minute across all callers. Model files are hashed once per size and modification time, not on every download.

**Alternatives.** A prompt identifier with the text held on the server (the app's prompts would live in two repositories); a per-address limit alone (spoofable).

**Consequences.** Changing any word of a prompt means regenerating `app_prompts.json` and deploying the gateway before the new app version is used. An attacker who keeps sending wrong codes can keep enrolment refused (valid codes included, with a Retry-After) for as long as they keep sending them, but cannot guess codes faster than the cap, cannot stop phones that are already enrolled, and leaves an `enroll.locked` audit entry. Codes from `make_codes.py` carry 72 bits, so the cap is defence in depth, not the only barrier; the limit is tunable per department. User text is still forwarded as given, so the gateway remains a translation and drafting proxy.

**Amendment (enrolment limiter).** uvicorn with `--forwarded-allow-ips='*'` took the leftmost `X-Forwarded-For` entry, which the caller writes (checked against uvicorn 0.54), so the per-address limit could be evaded. The image now ignores proxy headers; the gateway reads the entry a configured number of trusted proxies appended on the right (`TRUSTED_PROXY_HOPS`, default 0). The window counters are guarded by a lock, so the wrong-code cap holds exactly under concurrent requests, and an already-used code gets the same answer as a wrong one. The limiter is in memory: it resets on restart and is per process, so run one worker, or move it to shared storage before running several.
