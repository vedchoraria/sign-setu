# 16. Response to the client proposal

| Field | Value |
| --- | --- |
| Document | Response to "Sign Setu proposal.md" and the three architecture sketches |
| Purpose | Maps each proposal item to the built system. States where the build departs from the proposal and why, and lists the legal points the proposal does not cover |
| Related | [15-dpia.md](15-dpia.md), [07-decisions.md](07-decisions.md), [02-architecture.md](02-architecture.md) |

## 1. Summary

Every item in the proposal's core goal and data flow (sections 1 and 2) is built and runs offline on the phone. The build departs from the proposal in four places (section 3), each for a technical, security or legal reason. Section 4 lists legal requirements the proposal does not mention. Section 5 lists the work that remains.

## 2. Proposal items against the build

| Proposal item | Status | Where |
| --- | --- | --- |
| Camera frames in device memory only | Built | `camera/CameraMirror.kt`: frames are analysed and discarded, never written (SEC-47) |
| MediaPipe hand, face and body landmarks | Built | MediaPipe Holistic on the phone, GPU with CPU fallback (`camera/HolisticTracker.kt`) |
| Lightweight temporal classifier | Built | Temporal convolution reader, 262 ISL words and 250 ASL signs, plus ISL and ASL fingerspelling (`sign/`) |
| Confidence threshold and clarification | Built | Below the threshold the subject is asked to sign slower. Every reading is confirmed by the subject from two or three pictures; a reading is never recorded as an answer by itself (FR-66) |
| Sign to local animation ID | Built | Recognised words map to sign IDs in `words.json`; the 2D character plays them |
| 2D avatar and caption | Built | Code-drawn character with jointed fingers (D-20, D-21); captions in 17 languages |
| Text or intent to sign | Built | ISL word order (time, negation, question word), sign dictionary, fingerspelling, everyday gestures and pictures (`data/content/TextSigning.kt`) |
| Encrypted local database, retention, deletion | Built | SQLCipher; the key is wrapped in Android Keystore and works only while the phone is unlocked; default retention 24 h (D-48) |
| Hash check before loading a model or content | Built | Every file is checked against the SHA-256 content manifest (SEC-54) |
| Second screen, authenticated and encrypted | Built | Bonded Bluetooth, ECDH key agreement, six-digit comparison code, AES-GCM (D-26) |
| Interpreter workflow | Partial | Interpreter contact and handoff are built. Sharing session data with an interpreter is not, by design (section 3.4) |
| FastAPI backend, model registry, feedback, audit | Built, optional | `backend/`: enrolment, model manifest, consented label feedback, audit, Keycloak admin (D-47) |
| Admin console | Partial | The reviewer portal (`portal/`) covers content sign-off. Device, model and audit pages are later work |
| Testing of accuracy, latency, accessibility, privacy | Built | Unit, device and accessibility tests; accuracy in `tools/ml/EVAL.md`; results in [10-test-report.md](10-test-report.md) |

## 3. Departures from the proposal

### 3.1 Native Kotlin, not React Native, Expo or Flutter

The proposal leaves the choice open. The build uses Kotlin with Jetpack Compose (D-01). The camera, MediaPipe, SQLCipher, Android Keystore, Bluetooth RFCOMM and screen pinning are all native Android APIs. A cross-platform framework would reach each one through a bridge. On the camera path, that bridge copies every frame, which the proposal's own sketch warns against ("No Base64 copies"). A rewrite would also restart the security review and add a JavaScript or Dart runtime to the audit. The trade-off is that an iOS version needs its own build; Android covers officers' phones.

### 3.2 A code-drawn character, not Rive files or ISL video

Rive needs licensed character art, and an ISL video library needs recorded signers. Videos of real signers are identifiable faces that every phone would carry. The code-drawn character signs from keyframes retargeted from public ISL datasets (CC BY 4.0). It needs no recorded person, and a sign can be corrected by editing data.

### 3.3 Keycloak for administrators only

Field phones enrol once with a one-time code from the department and then hold a revocable device token. Officers do not type passwords on personal phones, and the app works with no network. Keycloak protects the admin and reviewer side, where people do log in.

### 3.4 No clip upload and no session sharing in the first release

The proposal makes these optional. They are left out because each one creates a new copy of a vulnerable person's data off the phone. That needs separate consent, a separate assessment and, for children, verifiable parental consent ([15-dpia.md](15-dpia.md) section 2). The gateway accepts a corrected sign label with consent, and nothing else.

## 4. Legal points not in the proposal

Summaries from public sources; not legal advice. The department's legal officer confirms them.

| Law | Point | Effect on the product |
| --- | --- | --- |
| BNSS s.173(1), provisos | When a woman with a disability reports certain offences, the information is recorded with an interpreter or special educator and videographed | "No clip storage" means the app cannot be that recording. It assists the officer; it replaces neither the interpreter nor the videography |
| BNSS s.183 | A statement before a magistrate by a person with a disability needs an interpreter or special educator and is videographed | As above |
| BSA 2023 s.63 | Electronic records offered in evidence need a certificate | The export carries a hash chain and a draft certificate for the department to complete |
| DPDP Act 2023 s.7, s.17(1)(c) | Processing for investigation is a legitimate use and is exempt from most consent duties, but s.8(5) security safeguards still apply | The interview does not ask the subject for "consent". Consent applies only to optional sharing such as feedback |
| DPDP Act 2023 s.9 and 2025 Rules | Children's data needs verifiable parental consent outside the investigation exemption | Optional sharing is off by default and never automatic for a child |
| JJ Act 2015 s.74, POCSO ss.24, 26 | No disclosure of a child's identity; child-friendly recording | No names, photos or faces are stored by design; the export is encrypted |
| RPwD Act 2016 s.12 | Access to justice for persons with disabilities | The product's purpose |

## 5. Remaining work

| Phase | Objective | Exit criterion |
| --- | --- | --- |
| R1 | Raise sign reading accuracy for signers the reader was not trained on | Unseen-signer top-1 rises above the current 0.29 in `EVAL.md`, with no loss on the INCLUDE test split |
| R2 | Calibrate reader confidence so that the clarification threshold means a stated probability | Calibration error is reported; the threshold is set from held-out data |
| R3 | Add the officer notice for the BNSS s.173 and s.183 cases | Wording approved by the department and shown at session start |
| R4 | Signed model updates from the gateway | The manifest is signed and the key is pinned in the app; a tampered download is rejected on the phone |
| R5 | Admin pages for devices, model versions and audit | Behind Keycloak; no subject data shown |
