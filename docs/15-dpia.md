# 15. Data protection impact assessment

| Field | Value |
| --- | --- |
| Document | Sign Setu data protection impact assessment |
| Scope | The Android app (both flavors), the optional department gateway (`backend/`) and the reviewer portal (`portal/`) |
| Controller | The police department that deploys the app **[confirm]** |
| Status | Draft for the department's legal officer and Data Protection Officer; not legal advice |
| Related | [01-requirements.md](01-requirements.md) section 3, [04-security.md](04-security.md), [07-decisions.md](07-decisions.md) |

## 1. Processing described

### 1.1 Purpose

The app helps an officer communicate with a person who is Deaf, hard of hearing, mute, a child or otherwise unable to use speech. It shows questions as pictures, captions, speech and signs, and it records the person's answers. The app does not identify anyone, and it does not replace an interpreter, a special educator or a statutory recording (section 3).

### 1.2 Data subjects and categories

| ID | Category | Examples | Held in | Retention |
| --- | --- | --- | --- | --- |
| DC-1 | Answers | Taps, drawings, typed text, confirmed signs, lesson answers (name, address, school) | Encrypted session database | Default 24 h, 4 to 72 h, or until the session ends |
| DC-2 | Landmarks | Hand, pose and face points of signed answers | Memory only | Until the answer is confirmed, lock or session end |
| DC-3 | Camera frames | The live picture inside a sign window | Memory only, one frame at a time | Not stored |
| DC-4 | Skeleton snapshot | A drawing of the pose for one answer, when the officer turns it on (D-46) | Encrypted session database | As DC-1 |
| DC-5 | Officer settings | Language, interpreter contact | DataStore | Until cleared |
| DC-6 | Device enrolment | Device token (hashed on the gateway), audit rows without subject data | Gateway database | Until revoked **[confirm]** |
| DC-7 | Label feedback | A corrected sign label with explicit consent; no landmarks, pictures or statements | Gateway database | **[confirm]** |

Children, victims of sexual offences and persons with disabilities are expected data subjects. DC-2 and DC-3 are biometric in nature, even though they are never stored or used for identification (SEC-71).

### 1.3 Data flow

Camera (DC-3), then MediaPipe Holistic on the phone (DC-2), then the on-device sign reader, then the subject's confirmation, then an answer row (DC-1). Nothing in this chain leaves the phone. The optional paths are:

- The second screen over bonded Bluetooth. It is encrypted (P-256 ECDH, AES-GCM) and carries no landmarks.
- The online assistant through the department gateway. It carries masked text only (D-47).
- The encrypted PDF export, on an officer's action only.

## 2. Lawful basis

| Processing | Basis | Notes |
| --- | --- | --- |
| Core session (DC-1 to DC-4) | DPDP Act 2023 s.7 (legitimate uses: State functions, compliance with law) and the s.17(1)(c) exemption for the prevention, detection, investigation or prosecution of offences | s.17(1)(c) does not lift s.8(1) and s.8(5). Accuracy and reasonable security safeguards still bind the controller. Consent is not the basis, so the app does not ask the subject to "consent" to the interview **[confirm]** |
| Children in the core session | As above | s.9 (verifiable parental consent) falls under Chapter II, which s.17(1)(c) exempts for investigation. JJ Act s.74 and POCSO ss.24, 26 still apply |
| Label feedback (DC-7) | Consent under s.6 | Not an investigation purpose, so the exemption does not apply. A child's feedback needs verifiable parental consent under s.9 and the 2025 Rules. The app sends none by default |
| Any future clip upload | Consent under s.6, given separately and in writing | Not built. Needs its own assessment before it is built |

## 3. Statutory recording and interpretation

The app is an aid to communication. It is not the record the law requires in these cases:

| Provision | Requirement | Consequence for the app |
| --- | --- | --- |
| BNSS s.173(1), provisos (formerly CrPC s.154(1)) | Information about certain offences from a woman with a temporary or permanent mental or physical disability is recorded at a place of her choice, with an interpreter or special educator, and the recording is videographed | The app stores no video (SEC-47), so it cannot be that recording. The officer arranges the interpreter and the videography. An officer-side notice for these sessions is to be added once the wording is approved (section 7) |
| BNSS s.183 (formerly CrPC s.164(5A)) | A statement before a magistrate by a person with a disability is recorded with an interpreter or special educator and videographed | As above. The report export says it is not a statement (FR-94) |
| BSA 2023 s.63 | An electronic record offered in evidence needs a certificate | The export carries a SHA-256 hash chain over the session timeline, a PDF digest and a draft s.63 certificate (`EvidenceIntegrity.kt`), which the department completes and signs **[confirm with the department]** |
| RPwD Act 2016 s.12 | Access to justice for persons with disabilities, in accessible formats | This is the purpose the app serves. It supports, and does not replace, the interpreter the Act's scheme expects |
| Constitution Art. 20(3) | No compelled self-incrimination | Rights notices are shown as statements. A Yes is never offered under a right |

## 4. Risks

Likelihood (L) and severity (S) are each rated 1 (low) to 3 (high), before and after controls.

| ID | Risk | L×S before | Controls | L×S after |
| --- | --- | --- | --- | --- |
| P-01 | A lost, stolen or seized phone exposes session records | 3×3 | SQLCipher with a Keystore-wrapped key usable only while the phone is unlocked (SEC-01, SEC-22, D-48); short retention; FLAG_SECURE; no backup | 1×3 |
| P-02 | A wrong machine reading is recorded as the person's answer | 3×3 | The subject confirms from pictures (FR-66). A reading is never logged as confirmed. "Sign slower" asks again when a reading is unsure | 1×3 |
| P-03 | Speech or text sent to a cloud service is retained or reused | 2×3 | No voice. Masked text only, through the department gateway, which stores none. Off unless the officer turns it on and the device is enrolled (D-47) | 1×2 |
| P-04 | Camera pictures or landmarks leave the phone | 2×3 | No upload path exists. Landmarks never reach disk or the Bluetooth link (SEC-71). The INTERNET permission is used only by the gateway client | 1×3 |
| P-05 | A child is identified through the record or export | 2×3 | No name, photo or face is stored by design. Export only on an officer's action, encrypted with a passphrase shown once (JJ s.74, POCSO ss.24, 26) | 1×3 |
| P-06 | A tampered model or content file changes what the person is shown | 2×3 | SHA-256 manifest checked before every load (SEC-54). APK signing. An imported assistant model is accepted only with a known hash | 1×3 |
| P-07 | The app is treated as the statutory recording or as the interpreter | 2×3 | Notices in the session and on every exported page (section 3); officer training **[confirm]** | 1×2 |
| P-08 | The second screen is spoofed or overheard | 2×2 | Bonded devices only. Six-digit comparison code, ECDH and AES-GCM. The stage device never opens the database (SEC-69) | 1×2 |
| P-09 | Feedback reveals who the person is | 1×3 | The gateway refuses any field other than the label. Explicit consent; off by default | 1×1 |
| P-10 | Reader accuracy is lower for some signers (regional variants, children, left-handed signers) | 3×2 | Confirmation by the subject, picture choices and fingerspelling; evaluation on unseen signers in `tools/ml/EVAL.md` | 2×2 |
| P-11 | Biometric points are held in memory for the length of a session | 2×2 | Memory only; cleared on confirm, lock and end; face points only for grammar cues, never for identity | 1×2 |
| P-12 | Gateway compromise exposes device tokens and audit rows | 2×2 | Tokens stored only as hashes and revocable; no subject data on the gateway; Keycloak for admin | 1×2 |

## 5. Data principal rights

Under s.17(1)(c), the core session is exempt from Chapter III. For the consent-based optional flows (DC-7), a request to correct or erase data is answered by the department through the gateway admin **[confirm process]**. Records on the phone are erased by the retention period, by ending the session, or by "Clear all data".

## 6. Breach handling

A breach of the gateway or a lost device with records still in retention is notified to the Data Protection Board and to affected persons as the 2025 Rules require. CERT-In is notified within six hours under the CERT-In Directions of 28 April 2022 **[confirm owner]**.

## 7. Open items

| Item | Owner |
| --- | --- |
| Confirm the controller, the DPO and the lawful-basis analysis in section 2 | Department legal officer |
| Approve the wording of the officer notice for section 3 cases; then add it to the session start | Department legal officer, then the team |
| Decide the retention for DC-6 and DC-7 | Department |
| Process for issuing a BSA s.63 certificate from an export | Department |
| Re-run this assessment before any clip upload, cloud inference or new data category | Product owner |
