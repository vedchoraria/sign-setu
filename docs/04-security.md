# 04. Security

Target: OWASP MASVS v2 with the MAS-L2 testing profile, plus the CERT-In requirements for government mobile applications (Guidelines on Information Security Practices for Government Entities, 6.2.1 to 6.2.7). The app handles data about victims and children on officers' personal phones, so every control below is mandatory for release 1 unless marked otherwise.

## 1. Objectives

1. Subject data cannot be read from a lost, stolen or shared phone without the officer's credential.
2. Subject data does not leak through backups, screenshots, logs, notifications, the clipboard or other apps.
3. The only data that leaves the phone is an encrypted report or a contact link, both on explicit officer action.
4. Every release is built from reviewed code, scanned, and traceable to an audited build.

## 2. Controls

### 2.1 Storage

| ID | Control | Source |
| --- | --- | --- |
| SEC-01 | Session database encrypted with SQLCipher; random 256-bit key wrapped by an AES key in Android Keystore (hardware-backed where available). | CERT-In 6.2.3, 6.2.4; MASVS-STORAGE-1 |
| SEC-02 | Drawings stored only inside the encrypted database; export files only in app-private cache, deleted when the officer finishes the session, on Clear all data, and at every unlock. | MASVS-STORAGE-1 |
| SEC-03 | `android:allowBackup="false"`; `dataExtractionRules` (API 31+) and `fullBackupContent` (API 29-30) exclude everything. | MASVS-STORAGE-2 |
| SEC-04 | No personal data in Logcat; release builds strip `android.util.Log` calls with R8 (`-assumenosideeffects` in `proguard-rules.pro`); no crash SDK. | CERT-In 6.2.5 |
| SEC-05 | Note fields disable autocorrect and request no personalised learning where the keyboard supports it. | MASVS-STORAGE-2 |
| SEC-06 | Copy disabled on log entries; subject data never placed on the clipboard. | MASVS-STORAGE-2 |
| SEC-07 | DataStore (`settings`) and two plain preference files (`locale` for the officer's language, `stage_guard` for the `stage_live` flag) hold settings only, no personal data. | MASVS-STORAGE-1 |
| SEC-08 | Sessions purged on end or retention expiry (WorkManager and at next open, whichever is first); `secure_delete` overwrites purged rows and the rollback journal is truncated, so no write-ahead log keeps old pages. | DPDP section 8(7) |

### 2.2 Cryptography

| ID | Control |
| --- | --- |
| SEC-10 | Platform and SQLCipher cryptography only; no custom algorithms. |
| SEC-11 | Keys never hard-coded or stored in plain text; Keystore keys non-exportable. |
| SEC-12 | Export is a PDF encrypted with AES-256 under a one-time 80-bit random passphrase (16 characters), shown once and shared out of band. |
| SEC-76 | Every report page carries a record digest: the first 64 bits of a SHA-256 over the exported rows (`SessionReport.recordDigest`). Re-exporting the session on the phone gives the same digest, so an edited copy shows. |

### 2.3 Authentication

| ID | Control |
| --- | --- |
| SEC-20 | The app requires a secure device lock and refuses to store sessions without one (`KeyguardManager.isDeviceSecure()`). |
| SEC-21 | Opening the app and viewing past sessions require BiometricPrompt: `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` on API 30+; `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` on API 29, where the strong combination is unsupported. Re-lock after 5 minutes in the background. The app first tries the Keystore key silently: within its 30-second window after the officer unlocks the phone it opens without a second prompt (D-23). |
| SEC-22 | The key-encryption key is bound to user authentication: `setUserAuthenticationParameters` (time-bound) on API 30+; `setUserAuthenticationValidityDurationSeconds` on API 29. |

### 2.4 Network

| ID | Control |
| --- | --- |
| SEC-30 | INTERNET is declared only for the department gateway (D-47): one HTTPS host fixed at build time, cleartext refused by the network security config and by `UrlHttp`, nothing connects when the build has no gateway or the phone is offline, and nothing is sent inside a sign window. The gateway token is wrapped by a Keystore key (`KeystoreVault`). The build fails if the merged manifest of any variant contains RECORD_AUDIO or ACCESS_BACKGROUND_LOCATION. |
| SEC-31 to SEC-34 | Gateway: TLS (platform default, 1.2+), certificate pin set for the gateway host in the network security config once the host is fixed, server-side validation of every request (extra fields refused), partner keys only in the gateway. |

### 2.5 Platform interaction

| ID | Control | Source |
| --- | --- | --- |
| SEC-40 | `FLAG_SECURE` on the activity: blocks screenshots, screen recording and recents thumbnails. | NFR-32 |
| SEC-41 | Only the launcher activity exported, apart from WorkManager's job service, which Android requires and protects with BIND_JOB_SERVICE; unused library receivers are removed from the merged manifest; export through `FileProvider` with a temporary grant. | MASVS-PLATFORM-1 |
| SEC-42 | Runtime permissions: CAMERA for the stage mirror and sign windows (D-20, D-25), asked of the officer at session start; BLUETOOTH_CONNECT ("nearby devices", Android 12 and later) for the second screen, asked only when a second screen is set up on either device (D-26). VIBRATE is a normal permission. No RECORD_AUDIO, location or INTERNET in the standard build (SEC-73). | CERT-In 6.2.6 |
| SEC-43 | No WebView. | MASVS-PLATFORM-2 |
| SEC-44 | No notifications containing subject data. | NFR-37 |
| SEC-45 | `filterTouchesWhenObscured` on every window the app opens (the activity, and each dialog, sheet and menu), so Show, export and clear actions ignore touches that come through another app's overlay. | MASVS-PLATFORM-3 |
| SEC-47 | Camera: only Preview and ImageAnalysis are bound; no ImageCapture or VideoCapture; frames are analysed only inside a sign window (SEC-71), never written anywhere, and the analysis buffer is reused after each frame; the camera runs only while the stage is on screen; CAMERA is requested from the officer at session start, never on the stage; the officer and the department can turn the mirror off. | MASVS-PLATFORM-1, DPDP section 8 |
| SEC-46 | Screen pinning (`startLockTask`) from the first Show state until the session ends; first run guides the officer to turn on app pinning with "ask for PIN before unpinning". Leaving the Show state for the Officer state is one tap on the officer handle, with BiometricPrompt as well when the department turns it on (D-23). | FR-61 |

### 2.6 Code and supply chain

| ID | Control | Source |
| --- | --- | --- |
| SEC-50 | Dependencies pinned with Gradle dependency verification and locking. | CERT-In app guidelines 4.15 |
| SEC-51 | CycloneDX SBOM for every release build. | CERT-In 8.5 |
| SEC-52 | Android Lint security checks, Semgrep and OSV-Scanner on every change; gitleaks on every commit. | CERT-In app guidelines 4.8 |
| SEC-53 | Release build has no test code, debug flags or logs; `debuggable=false`. | CERT-In 6.2.5 |
| SEC-54 | Content bundle verified against the manifest hashes before use. | CERT-In app guidelines 4.16 |
| SEC-55 | minSdk 29; target the latest stable SDK; security fixes released within 30 days of a relevant advisory. | MASVS-CODE-1 |

### 2.7 Resilience

| ID | Control |
| --- | --- |
| SEC-60 | R8 full-mode shrinking and obfuscation. |
| SEC-61 | Release 1.1 only: Android key attestation verified by the gateway (both Google roots). |
| SEC-62 | Release signing key generated offline, held by the release manager with a second custodian and an offline backup in department custody; APK Signature Scheme v3. |
| SEC-63 | Rooted or test-key device: warn the officer and disable session history; do not block the app in an emergency [confirm with department]. |
| SEC-64 | Build expiry: new sessions refused 180 days after the build date (FR-67), because personal phones have no device management. |
| SEC-65 | The assistant model is imported from a file, never downloaded; it is kept only if its SHA-256 is on the app's allow-list, computed while copying, so no other file reaches the native parser. Each request starts from an empty context and nothing typed is stored by the assistant. | D-24 |

### 2.8 Second screen

| ID | Control | Source |
| --- | --- | --- |
| SEC-66 | Secure RFCOMM to bonded devices only. The app never scans, advertises, makes itself discoverable or bonds. The SDP service name is the generic "Display" and the UUID is app-specific. The build gate refuses ACCESS_BACKGROUND_LOCATION in every build, and BLUETOOTH_SCAN, BLUETOOTH_ADMIN, BLUETOOTH_ADVERTISE, NEARBY_WIFI_DEVICES and fine or coarse location in the standard build. | D-26; CERT-In 6.2.6 |
| SEC-67 | P-256 ECDH with commit then reveal, so neither side can search for a key that yields a chosen code. HKDF-SHA256 with the transcript hash as salt derives the two direction keys, the 6-digit code, the 4-digit check number and the resume key. Frames are AES-256-GCM; the IV is a direction byte, zeros and a 64-bit counter, and the additional data is the version and counter; the receiver requires the next counter, so replayed, reordered or skipped frames close the link. Plain handshake frames are capped at 4 KiB and encrypted frames at 1 MiB, checked before allocation. Timeouts: connect 12 s with one retry, handshake 15 s, code 90 s. Peer keys must lie on P-256. Version code and content manifest hash must match. The typed code is compared in constant time; three misses close the link; the stage's Match waits until both screens show the same check number. | MASVS-CRYPTO-1, MASVS-NETWORK-1 |
| SEC-68 | The resume secret is held in memory only and the peer address is pinned; a reconnect within 120 s needs no code. The secret is zeroed on Bye, window expiry, lock and end of link. | MASVS-CRYPTO-2 |
| SEC-69 | Nothing about a session is stored on the stage device. Ended wipes the view, unacknowledged answers and keys; the credential exit then ends the task and the process. Copies of key bytes inside `SecretKeySpec` and the crypto provider are out of reach, so process exit is the real wipe. Drawings received from the stage are re-encoded on the officer phone, with their dimensions bounded, before they reach the database. | MASVS-STORAGE-1 |
| SEC-70 | The pairing code logs no throwable or exception message, only exception class names; the reason shown to the officer is the app's own text, never peer-sent text. LINK rows hold the device model, version code, the first 8 characters of the content hash and the last 4 hex of the SHA-256 of the peer address, never the Bluetooth friendly name. | SEC-04; JJ Act s.74 |

### 2.9 Camera, landmarks and build flavors

| ID | Control | Source |
| --- | --- | --- |
| SEC-71 | Landmarks are biometric data. Frames are analysed for the stage overlay while the camera shows, at most 10 a second outside a sign window, and reach sign capture only inside an explicit sign window (FR-84, D-40). Landmarks live only in view-model memory, for the session, and are wiped when the session ends, the app locks or the view model is cleared; they are never written to disk, logged, exported or sent over the second-screen link, except a one-frame skeleton drawing (no picture of the person) that the officer has switched on for the session with "Keep skeleton snapshot in record" (off at every start, D-46). A narrative keeps hands and pose only. Signs learned in a session (D-40) are feature sequences held under the same rules. The subject's stage draws hands, arms, brows and lips, never the face oval or eyes. | D-25, D-27; DPDP s.8 |
| SEC-72 | MediaPipe's usage-statistics upload backend is excluded from the build and replaced by a stub destination that drops every event on the device (D-33). | SEC-30 |
| SEC-73 | Build flavors: `standard` is the reference build and keeps every rule; `nearby` adds only the Nearby Connections permissions of D-26, uses its own application ID, and is offered only to departments whose phones have Play services. The permission gate runs for every variant before packaging (`check<Variant>Permissions`). | CERT-In 6.2.6 |
| SEC-74 | The holistic model, the sign reader and its word map are verified against the content manifest before they load (`HolisticTracker.verifiedModel`, `SignReaders.create`); a failed model is not used and the stage keeps a plain mirror. | SEC-54 |
| SEC-75 | A flag holding no subject data (`stage_live`) is written while the stage shows. If the app is locked or restarted while it is set (a crash, a low-memory kill or the 5-minute relock), unlocking always asks for the full biometric or PIN instead of reusing the phone's recent unlock; a successful prompt clears it. | FR-101, SEC-21 |

## 3. Key management

| Key | Where | Rotation | Loss impact |
| --- | --- | --- | --- |
| Database key (DEK) | Generated on the phone, wrapped by the KEK | On Clear all data | Sessions unrecoverable (acceptable: short-lived data) |
| Key-encryption key (KEK) | Android Keystore, non-exportable, auth-bound; not invalidated by enrolling a fingerprint | On reinstall, or when Android drops it because the screen lock was removed or reset | Same; the app then deletes the unreadable database, makes new keys and tells the officer once, instead of refusing to unlock |
| Export passphrase | Shown once to the officer | Per export | Report unreadable |
| Link keys (two direction keys) | Derived per link by ECDH and HKDF, memory only on both devices | Every new link and every resume | Link closes; officer pairs again |
| Resume secret | Derived at pairing, memory only | Every new link; zeroed on Bye, expiry, lock and end | Reconnect needs the code again |
| Release signing key | Release manager and second custodian, offline | On compromise (APK Signature Scheme v3 rotation) | Installed apps cannot be updated without the backup |

## 4. Threats in scope for release 1

In the threat model, Release 1 removes the network threats (T-07 to T-13); camera exposure (T-16) and landmark processing (T-37) are controlled by SEC-47 and SEC-71; the second screen adds T-38 to T-44.

| Threat | Main controls |
| --- | --- |
| Lost, stolen or seized phone | SEC-01, SEC-08, SEC-20 to SEC-22 |
| Family member who knows the phone PIN | End session after each conversation, SEC-08 retention, officer SOP; app-specific PIN if the pilot shows shared phones |
| Subject leaves the app on the officer's phone | SEC-46 |
| Backup or device transfer copies data | SEC-03 |
| Screenshot, recording, recents thumbnail | SEC-40 |
| Personal data in logs | SEC-04 |
| Report sent to the wrong person | SEC-12, passphrase out of band, officer SOP |
| Other apps, intents, overlays | SEC-41, SEC-45 |
| Rooted phone | SEC-63 |
| Camera shows or keeps images of the subject or bystanders | SEC-47, FLAG_SECURE (SEC-40), mirror off by officer or department |
| Compromised dependency or signing key | SEC-50 to SEC-52, SEC-62 |
| Gesture misread and acted on | Show-back confirmation (FR-66); "unclear" logged otherwise |
| Distressing content shown to a child | Content policy review; curated cards; no generated images |
| App used to take a statement | No statement feature; in-app notice; terms of use; not-a-statement notice on every export page (FR-94) |
| Landmarks kept or leaked | SEC-71 |
| Rogue, relayed or wrong second screen | SEC-66, SEC-67, SEC-68 |
| Lost or unattended stage device | SEC-69, FR-73, officer SOP |
| Usage statistics sent by a library | SEC-72, SEC-30 |
| Machine reading taken as the subject's answer | FR-86, evidence grades (FR-91) |

## 5. Review points

| When | Review |
| --- | --- |
| Every change | Review by someone other than the author; Lint, Semgrep, OSV-Scanner and gitleaks pass |
| Gate 1, hardening (release path) | MASVS self-check against this document; full internal MAS-L2 review; threat model refreshed |
| Gate 2, security audit | CERT-In empanelled audit (source and binary) |
