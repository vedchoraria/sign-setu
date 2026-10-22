# 04. Security

Target: OWASP MASVS v2 with the MAS-L2 testing profile, plus the CERT-In requirements for government mobile applications (Guidelines on Information Security Practices for Government Entities, 6.2.1 to 6.2.7). The app handles data about victims and children on officers' personal phones, so every control below is mandatory unless marked otherwise.

## 1. Objectives

1. Subject data cannot be read from a lost, stolen or shared phone without the officer's credential.
2. Subject data does not leak through backups, screenshots, logs, notifications, the clipboard or other apps.
3. The only data that leaves the phone is an encrypted report or a contact link, both on explicit officer action.
4. Every release is built from reviewed code, scanned, and traceable to an audited build.

## 2. Controls

### 2.1 Storage

| Control | Source |
| --- | --- |
| Session database encrypted with SQLCipher; random 256-bit key wrapped by an AES key in Android Keystore (hardware-backed where available). | CERT-In 6.2.3, 6.2.4; MASVS-STORAGE-1 |
| Drawings stored only inside the encrypted database; export files only in app-private cache, deleted when the officer finishes the session, on Clear all data, and at every unlock. | MASVS-STORAGE-1 |
| `android:allowBackup="false"`; `dataExtractionRules` (API 31+) and `fullBackupContent` (API 29-30) exclude everything. | MASVS-STORAGE-2 |
| No personal data in Logcat; release builds strip `android.util.Log` calls with R8 (`-assumenosideeffects` in `proguard-rules.pro`); no crash SDK. | CERT-In 6.2.5 |
| Note fields disable autocorrect and request no personalised learning where the keyboard supports it. | MASVS-STORAGE-2 |
| Copy disabled on log entries; subject data never placed on the clipboard. | MASVS-STORAGE-2 |
| DataStore (`settings`) and two plain preference files (`locale` for the officer's language, `stage_guard` for the `stage_live` flag) hold settings only, no personal data. | MASVS-STORAGE-1 |
| Sessions purged on end or retention expiry (WorkManager and at next open, whichever is first); `secure_delete` overwrites purged rows and the rollback journal is truncated, so no write-ahead log keeps old pages. | DPDP section 8(7) |

### 2.2 Cryptography

| Control |
| --- |
| Platform and SQLCipher cryptography only; no custom algorithms. |
| Keys never hard-coded or stored in plain text; Keystore keys non-exportable. |
| Export is a PDF encrypted with AES-256 under a one-time 80-bit random passphrase (16 characters), shown once and shared out of band. |
| Every report page carries a record digest: the first 64 bits of a SHA-256 over the exported rows (`SessionReport.recordDigest`). Re-exporting the session on the phone gives the same digest, so an edited copy shows. |

### 2.3 Authentication

| Control |
| --- |
| The app requires a secure device lock and refuses to store sessions without one (`KeyguardManager.isDeviceSecure`). |
| Opening the app and viewing past sessions require BiometricPrompt: `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` on API 30+; `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` on API 29, where the strong combination is unsupported. Re-lock after 5 minutes in the background. The app first tries the Keystore key silently: within its 30-second window after the officer unlocks the phone it opens without a second prompt. |
| The key-encryption key needs no prompt but is created with `setUnlockedDeviceRequired(true)`, so it cannot be used while the phone is locked. A key made without that flag is migrated on the next unlock. |

### 2.4 Network

| Control |
| --- |
| INTERNET is declared only for the department gateway: one HTTPS host fixed at build time, cleartext refused by the network security config and by `UrlHttp`, nothing connects when the build has no gateway or the phone is offline, and nothing is sent inside a sign window. The gateway token is wrapped by a Keystore key (`KeystoreVault`). The build fails if the merged manifest of any variant contains RECORD_AUDIO or ACCESS_BACKGROUND_LOCATION. |
| Gateway: TLS (platform default, 1.2+), certificate pin set for the gateway host in the network security config once the host is fixed, server-side validation of every request (extra fields refused), partner keys only in the gateway. |

### 2.5 Platform interaction

| Control | Source |
| --- | --- |
| `FLAG_SECURE` on the activity: blocks screenshots, screen recording and recents thumbnails. | |
| Only the launcher activity exported, apart from WorkManager's job service, which Android requires and protects with BIND_JOB_SERVICE; unused library receivers are removed from the merged manifest; export through `FileProvider` with a temporary grant. | MASVS-PLATFORM-1 |
| Runtime permissions: CAMERA for the stage mirror and sign windows, asked of the officer at session start; BLUETOOTH_CONNECT ("nearby devices", Android 12 and later) for the second screen, asked only when a second screen is set up on either device. VIBRATE is a normal permission. No RECORD_AUDIO, location or INTERNET in the standard build. | CERT-In 6.2.6 |
| No WebView. | MASVS-PLATFORM-2 |
| No notifications containing subject data. | |
| `filterTouchesWhenObscured` on every window the app opens (the activity, and each dialog, sheet and menu), so Show, export and clear actions ignore touches that come through another app's overlay. | MASVS-PLATFORM-3 |
| Camera: only Preview and ImageAnalysis are bound; no ImageCapture or VideoCapture; frames are analysed only inside a sign window, never written anywhere, and the analysis buffer is reused after each frame; the camera runs only while the stage is on screen; CAMERA is requested from the officer at session start, never on the stage; the officer and the department can turn the mirror off. | MASVS-PLATFORM-1, DPDP section 8 |
| Screen pinning (`startLockTask`) from the first Show state until the session ends; first run guides the officer to turn on app pinning with "ask for PIN before unpinning". Leaving the Show state for the Officer state is one tap on the officer handle, with BiometricPrompt as well when the department turns it on. | |

### 2.6 Code and supply chain

| Control | Source |
| --- | --- |
| Dependencies pinned with Gradle dependency verification and locking. | CERT-In app guidelines 4.15 |
| CycloneDX SBOM for every release build. | CERT-In 8.5 |
| Android Lint security checks, Semgrep and OSV-Scanner on every change; gitleaks on every commit. | CERT-In app guidelines 4.8 |
| Release build has no test code, debug flags or logs; `debuggable=false`. | CERT-In 6.2.5 |
| Content bundle verified against the manifest hashes before use. | CERT-In app guidelines 4.16 |
| minSdk 29; target the latest stable SDK; security fixes released within 30 days of a relevant advisory. | MASVS-CODE-1 |

### 2.7 Resilience

| Control |
| --- |
| R8 full-mode shrinking and obfuscation. |
| Release signing key generated offline, held by the release manager with a second custodian and an offline backup in department custody; APK Signature Scheme v3. |
| Rooted or test-key device: warn the officer, keep no history and do not block the app in an emergency [confirm with department]. See D-53. |
| Build expiry: new sessions refused 180 days after the build date, because personal phones have no device management. |
| The assistant model is imported from a file, never downloaded; it is kept only if its SHA-256 is on the app's allow-list, computed while copying, so no other file reaches the native parser. Each request starts from an empty context and nothing typed is stored by the assistant. |

### 2.8 Second screen

| Control | Source |
| --- | --- |
| Secure RFCOMM to bonded devices only. The app never scans, advertises, makes itself discoverable or bonds. The SDP service name is the generic "Display" and the UUID is app-specific. The build gate refuses ACCESS_BACKGROUND_LOCATION in every build, and BLUETOOTH_SCAN, BLUETOOTH_ADMIN, BLUETOOTH_ADVERTISE, NEARBY_WIFI_DEVICES and fine or coarse location in the standard build. | CERT-In 6.2.6 |
| P-256 ECDH with commit then reveal, so neither side can search for a key that yields a chosen code. HKDF-SHA256 with the transcript hash as salt derives the two direction keys, the 6-digit code, the 4-digit check number and the resume key. Frames are AES-256-GCM; the IV is a direction byte, zeros and a 64-bit counter, and the additional data is the version and counter; the receiver requires the next counter, so replayed, reordered or skipped frames close the link. Plain handshake frames are capped at 4 KiB and encrypted frames at 1 MiB, checked before allocation. Timeouts: connect 12 s with one retry, handshake 15 s, code 90 s. Peer keys must lie on P-256. Version code and content manifest hash must match. The typed code is compared in constant time; three misses close the link; the stage's Match waits until both screens show the same check number. | MASVS-CRYPTO-1, MASVS-NETWORK-1 |
| The resume secret is held in memory only and the peer address is pinned; a reconnect within 120 s needs no code. The secret is zeroed on Bye, window expiry, lock and end of link. | MASVS-CRYPTO-2 |
| Nothing about a session is stored on the stage device. Ended wipes the view, unacknowledged answers and keys; the credential exit then ends the task and the process. Copies of key bytes inside `SecretKeySpec` and the crypto provider are out of reach, so process exit is the real wipe. Drawings received from the stage are re-encoded on the officer phone, with their dimensions bounded, before they reach the database. | MASVS-STORAGE-1 |
| The pairing code logs no throwable or exception message, only exception class names; the reason shown to the officer is the app's own text, never peer-sent text. LINK rows hold the device model, version code, the first 8 characters of the content hash and the last 4 hex of the SHA-256 of the peer address, never the Bluetooth friendly name. | JJ Act s.74 |

### 2.9 Camera, landmarks and build flavors

| Control | Source |
| --- | --- |
| Landmarks are biometric data. Frames are analysed for the stage overlay while the camera shows, at most 10 a second outside a sign window, and reach sign capture only inside an explicit sign window. Landmarks live only in view-model memory, for the session, and are wiped when the session ends, the app locks or the view model is cleared; they are never written to disk, logged, exported or sent over the second-screen link, except a one-frame skeleton drawing (no picture of the person) that the officer has switched on for the session with "Keep skeleton snapshot in record" (off at every start). A narrative keeps hands and pose only. Signs learned in a session are feature sequences held under the same rules. The subject's stage draws hands, arms, brows and lips, never the face oval or eyes. | DPDP s.8 |
| MediaPipe's usage-statistics upload backend is excluded from the build and replaced by a stub destination that drops every event on the device. | |
| Build flavors: `standard` is the reference build and keeps every rule; `nearby` adds only the Nearby Connections permissions of, uses its own application ID, and is offered only to departments whose phones have Play services. The permission gate runs for every variant before packaging (`check<Variant>Permissions`). | CERT-In 6.2.6 |
| The holistic model, the sign reader and its word map are verified against the content manifest before they load (`HolisticTracker.verifiedModel`, `SignReaders.create`); a failed model is not used and the stage keeps a plain mirror. | |
| A flag holding no subject data (`stage_live`) is written while the stage shows. If the app is locked or restarted while it is set (a crash, a low-memory kill or the 5-minute relock), unlocking always asks for the full biometric or PIN instead of reusing the phone's recent unlock; a successful prompt clears it. | |

## 3. Key management

| Key | Where | Rotation | Loss impact |
| --- | --- | --- | --- |
| Database key (DEK) | Generated on the phone, wrapped by the KEK | On Clear all data | Sessions unrecoverable (acceptable: short-lived data) |
| Key-encryption key (KEK) | Android Keystore, non-exportable, usable only while the phone is unlocked; not invalidated by enrolling a fingerprint | On reinstall, or when Android drops it because the screen lock was removed or reset | Same; the app then deletes the unreadable database, makes new keys and tells the officer once, instead of refusing to unlock |
| Export passphrase | Shown once to the officer | Per export | Report unreadable |
| Link keys (two direction keys) | Derived per link by ECDH and HKDF, memory only on both devices | Every new link and every resume | Link closes; officer pairs again |
| Resume secret | Derived at pairing, memory only | Every new link; zeroed on Bye, expiry, lock and end | Reconnect needs the code again |
| Release signing key | Release manager and second custodian, offline | On compromise (APK Signature Scheme v3 rotation) | Installed apps cannot be updated without the backup |

## 4. Threats in scope

The app has no network threats beyond the optional gateway; camera exposure and landmark processing are covered by the camera and landmark controls above, and the second screen by the pairing controls.

| Threat | Main controls |
| --- | --- |
| Lost, stolen or seized phone | Encrypted database, key usable only while the phone is unlocked, app unlock, optional retention period |
| Family member who knows the phone PIN | Finish and delete sessions that are no longer needed, optional retention period, officer SOP; app-specific PIN if the pilot shows shared phones |
| Subject leaves the app on the officer's phone | Screen pinning and the officer handle on the stage |
| Backup or device transfer copies data | Backup and device transfer excluded |
| Screenshot, recording, recents thumbnail | FLAG_SECURE |
| Personal data in logs | Log calls stripped from release; no subject data logged |
| Report sent to the wrong person | AES-256 encrypted PDF, passphrase out of band, officer SOP |
| Other apps, intents, overlays | No exported components beyond the launcher; overlay touches filtered |
| Rooted phone | Root warning on the start screen; no saved-session list; finishing deletes the session (heuristic detection, evadable) |
| Camera shows or keeps images of the subject or bystanders | Frames analysed in memory only, FLAG_SECURE, mirror off by officer or department |
| Compromised dependency or signing key | Locked and checksum-verified dependencies, offline signing key, content hash manifest |
| Gesture misread and acted on | Show-back confirmation; "unclear" logged otherwise |
| Distressing content shown to a child | Content policy review; curated cards; no generated images |
| App used to take a statement | No statement feature; in-app notice; terms of use; not-a-statement notice on every export page |
| Landmarks kept or leaked | Landmarks in memory only, never stored or sent |
| Rogue, relayed or wrong second screen | Bonded devices only, ECDH with a six-digit comparison code, AES-GCM frames |
| Lost or unattended stage device | The stage device keeps nothing and stays pinned on a hand-back screen; officer SOP |
| Usage statistics sent by a library | MediaPipe statistics backend replaced by a stub |
| Machine reading taken as the subject's answer | The subject confirms from pictures; evidence grades |

## 5. Review points

| When | Review |
| --- | --- |
| Every change | Review by someone other than the author; Lint, Semgrep, OSV-Scanner and gitleaks pass |
| Gate 1, hardening (release path) | MASVS self-check against this document; full internal MAS-L2 review; threat model refreshed |
| Gate 2, security audit | CERT-In empanelled audit (source and binary) |
