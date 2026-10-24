# 04. Security

Target: OWASP MASVS v2 with the MAS-L2 testing profile, and the CERT-In requirements for government mobile applications (Guidelines on Information Security Practices for Government Entities, 6.2.1 to 6.2.7). The app handles data about victims and children on officers' personal phones: NowSecure tier 3, government. Every control is mandatory unless marked otherwise. Architecture references are to [02-architecture.md](02-architecture.md).

## 1. Objectives

1. Subject data cannot be read from a lost, stolen or shared phone without the officer's credential.
2. Subject data does not leak through backups, screenshots, logs, notifications, the clipboard or other apps.
3. Data leaves the phone only as an encrypted report or a contact link on the officer's action, as encrypted frames to a bonded stage device, or as masked text to the assistant service after the officer switched it on and allowed it for the session.
4. Every release is built from reviewed code, scanned and traceable to an audited build.

## 2. Controls

### 2.1 Storage

| Control | Source |
| --- | --- |
| Session store encrypted with SQLCipher; a random 256-bit DEK wrapped by a non-exportable AES-256-GCM Keystore key usable only while the phone is unlocked (`DatabaseKey`). Without a secure lock screen the DEK is wrapped by a key not tied to unlocking, the officer is told once, and the DEK moves under the unlocked-only key at the first unlock after a lock is set. | CERT-In 6.2.3, 6.2.4; MASVS-STORAGE-1 |
| Drawings and skeleton snapshots only inside the encrypted store. Export files only in `cacheDir/exports`, deleted when the officer finishes, after 15 minutes, at every unlock and on Clear all data. | MASVS-STORAGE-1 |
| `allowBackup="false"`; `dataExtractionRules` and `fullBackupContent` exclude every domain. | MASVS-STORAGE-2 |
| No personal data in Logcat; release builds strip `android.util.Log` calls (`proguard-rules.pro`); the four remaining log lines carry exception classes or timings only; no crash SDK. | CERT-In 6.2.5 |
| Every text field asks the keyboard for no personalised learning (`AppRoot.NoPersonalisedLearning`); the enrolment code field is a password field. | MASVS-STORAGE-2 |
| Only the interpreter's contact ever reaches the clipboard, on the officer's tap. | MASVS-STORAGE-2 |
| DataStore `settings` and the plain preference files `locale` and `clock` hold settings only. The gateway token is wrapped by its own Keystore key (`KeystoreVault`) in `noBackupFilesDir`. | MASVS-STORAGE-1 |
| Retention purge with `PRAGMA secure_delete` on and a truncated journal; while the app is locked the whole store is destroyed once even the newest session is past retention (`RetentionWorker`). | DPDP s.8(7) |

### 2.2 Cryptography

| Control | Source |
| --- | --- |
| Platform JCA, Android Keystore and SQLCipher only; no custom algorithms. AES-256-GCM, P-256 ECDH, HKDF-SHA256, SHA-256; `SecureRandom` for every key, nonce and passphrase (`kotlin.random` is used only to shuffle pictures). | MASVS-CRYPTO-1 |
| No key in the source or the repository; the direct assistant key lives in untracked `local.properties`, and a release build refuses to compile it in unless `-PallowEmbeddedKey` is passed for a private build. gitleaks runs on every push. | MASVS-CRYPTO-2 |
| Export: PDF encrypted with AES-256 under a one-time 80-bit passphrase shown once, and a random owner password. Every page carries the record digest and the hash-chain head; the certificate page carries the full chain head and its status (`EvidenceIntegrity`). | MASVS-CRYPTO-1 |
| Event log sealed as a SHA-256 chain as rows are written (`EventSeal`); export re-walks it and reports the first broken row. | BSA s.63 |

### 2.3 Authentication

| Control | Source |
| --- | --- |
| `OfficerAuth`: platform `BiometricPrompt` with `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` (API 30+), device credential on API 29, before the store opens, before a session is left, ended, exported or deleted, and before Clear all data. One confirmation covers the next 60 s; a lock forgets it. A phone with no secure lock skips the prompt. | MASVS-AUTH-2, AUTH-3 |
| Relock after 5 minutes in the background, 30 inside a session; a relock, crash or restart always asks again. | MASVS-AUTH-2 |
| The KEK needs no prompt but is created with `setUnlockedDeviceRequired(true)`; Android invalidating it (lock removed or reset) deletes the unreadable store and tells the officer once. | MASVS-AUTH-2 |
| Gateway: one-time enrolment codes (stored as SHA-256 on the server), per-address and global wrong-code limits, a random 256-bit bearer token (stored as SHA-256 on the server), optional token life, admin revocation; admin and reviewer routes need an OIDC token with the right realm role. | MASVS-AUTH-1 |

### 2.4 Network

| Control | Source |
| --- | --- |
| INTERNET is used only by the online assistant client (`UrlHttp`): HTTPS only, cleartext refused by the network security config and by `UrlHttp`, system trust anchors only, validated networks only. Nothing connects unless the build has a gateway or a direct key and the officer has switched the assistant on. | MASVS-NETWORK-1 |
| Session text goes only after the officer allows it once per session; names, details, emails, links, PAN-like ids and long numbers are masked first (`Pseudonymizer`). The gateway accepts only the app's own system prompts. | MASVS-NETWORK-1, PRIVACY-1 |
| Certificate pinning is designed and waits on the production host and CA ([17](17-attestation-and-pinning.md)). | MASVS-NETWORK-2 (gap) |

### 2.5 Platform interaction

| Control | Source |
| --- | --- |
| `FLAG_SECURE` on the activity in release builds; dialogs and sheets inherit it. | MASVS-PLATFORM-3 |
| Exported components in the merged release manifest: the launcher activity and WorkManager's `SystemJobService` (guarded by `BIND_JOB_SERVICE`). FileProvider not exported, one temporary read grant per share. Unused library receivers removed. No deep links, no PendingIntents, no WebView. | MASVS-PLATFORM-1, PLATFORM-2 |
| `filterTouchesWhenObscured` on the activity and every dialog window (`GuardedAlertDialog`). | MASVS-PLATFORM-3 |
| Runtime permissions: CAMERA asked of the officer at session start, never on the stage; BLUETOOTH_CONNECT only when a second screen is set up. Build gates fail any variant with RECORD_AUDIO or background location, and `standard` with any discovery or location permission. | CERT-In 6.2.6 |
| Camera: Preview and ImageAnalysis only; frames tracked in memory while the stage camera shows, at most one per 30 ms and down to one per 250 ms when idle or when the phone reports severe heat; landmarks kept only inside a sign window; the officer and the department can turn the mirror off. | MASVS-PRIVACY-1, DPDP s.8 |
| Screen pinning (`startLockTask`) while the stage shows; first run guides the officer to turn on "ask for PIN before unpinning". | |

### 2.6 Code and supply chain

| Control | Source |
| --- | --- |
| Dependencies pinned, locked (`gradle.lockfile`) and checksum-verified (`verification-metadata.xml`); BouncyCastle forced to 1.86; llama.cpp vendored at a fixed commit. | CERT-In app guidelines 4.15; MASVS-CODE-3 |
| CycloneDX SBOM and OSV-Scanner record for every release build on `master`. | CERT-In 8.5 |
| Release lint with security checks as errors, semgrep, OSV-Scanner on the shipped runtime lockfile, gitleaks. | CERT-In app guidelines 4.8 |
| Input from outside the app is bounded before use: second-screen frames (4 KiB handshake, 1 MiB frames, strict counters, `ignoreUnknownKeys=false`), drawings re-encoded with bounded size, written answers capped at 2,000 characters, the gateway's body and field limits. Room queries are parameterised; the only raw SQL is fixed migration DDL. | MASVS-CODE-4 |
| Content and models verified against SHA-256 before use; the assistant model accepted only from an allow-list. | CERT-In app guidelines 4.16 |
| minSdk 29; compileSdk and targetSdk 37; a build refuses new sessions 180 days after its build day. | MASVS-CODE-1, CODE-2 |

### 2.7 Resilience

| Control | Source |
| --- | --- |
| R8 shrinking and obfuscation in release; not debuggable; logs stripped. | MASVS-RESILIENCE-3 |
| Release key generated offline, held by the release manager and a second custodian; APK Signature Scheme v3. | MASVS-RESILIENCE-2 |
| Rooted or test-key device: heuristic check (`Build.TAGS`, `su` paths), warn, keep no history, still usable in an emergency. | MASVS-RESILIENCE-1 (partial) |
| No debugger, hook or repackaging detection; Play Integrity is unavailable in `standard` (NOTE E-5). | MASVS-RESILIENCE-2, -4 (gap) |

### 2.8 Second screen

| Control | Source |
| --- | --- |
| Secure RFCOMM to bonded devices only; the app never scans, advertises or bonds; SDP name "Display" and an app-specific UUID. | CERT-In 6.2.6 |
| P-256 ECDH with commit then reveal; HKDF-SHA256 over the transcript gives two direction keys, the 6-digit code, the 4-digit check number and the resume key. AES-256-GCM frames with a direction byte and counter as IV; the receiver requires the next counter. Peer keys must lie on P-256; version code and content hash must match; the code is compared in constant time; three misses close the link; timeouts connect 12 s with one retry, handshake 15 s, code 90 s. | MASVS-CRYPTO-1, NETWORK-1 |
| The resume secret is memory only and zeroed on Bye, window expiry, lock and end; the peer address is pinned for resume. | MASVS-CRYPTO-2 |
| The stage device stores nothing about a session, never opens the store, reads signs itself and sends words only; the officer phone re-encodes drawings. LINK rows hold the peer model, version, content hash prefix and 4 hex of the hashed address, never the Bluetooth name. | MASVS-STORAGE-1; JJ Act s.74 |

## 3. MASVS v2 checklist

Status from code and configuration; dynamic testing by the empanelled auditor confirms it (§7).

| Control | Status | Evidence | MASTG |
| --- | --- | --- | --- |
| STORAGE-1 Secure storage | Implemented | `DatabaseKey`, `SessionDatabase`, `KeystoreVault` | 0001, 0200 |
| STORAGE-2 No leakage | Implemented | Backup rules, FLAG_SECURE, Log stripping, no-learning keyboard | 0003, 0011, 0201, 0202 |
| CRYPTO-1 Strong cryptography | Implemented | §2.2 inventory | 0013, 0208, 0221 |
| CRYPTO-2 Key management | Implemented | §4 | 0015, 0212 |
| AUTH-1 Remote authentication | Implemented on the gateway; pinning and attestation open | `backend/signsetu_gateway/security.py` | 0017 |
| AUTH-2 Local authentication | Partial: the prompt gates actions; the DEK is not bound to the prompt by a CryptoObject (accepted: officers share no device credential, and the KEK still needs an unlocked phone) | `OfficerAuth`, `DatabaseKey` | 0018, 0326 |
| AUTH-3 Step-up for sensitive actions | Implemented for leave, end, export, delete, clear; switching the online assistant on is gated by a notice, not the prompt | `OfficerAuth` call sites | 0327 |
| NETWORK-1 Secure traffic | Implemented | `UrlHttp`, `network_security_config.xml` | 0019, 0217, 0233 |
| NETWORK-2 Identity pinning | Gap until the production host is fixed | [17](17-attestation-and-pinning.md) | 0020, 0021 |
| PLATFORM-1 IPC | Implemented | Merged manifest (§2.5) | 0007, 0250, 0251 |
| PLATFORM-2 WebViews | Not applicable: no WebView | | 0028 |
| PLATFORM-3 UI | Implemented | FLAG_SECURE, overlay filter, pinning | 0035, 0289 |
| CODE-1 Platform version | Implemented | minSdk 29, targetSdk 37 | 0272 |
| CODE-2 Updates | Implemented by build expiry | `Policy.kt` | 0274 |
| CODE-3 Vulnerable components | Implemented | OSV-Scanner, lockfile, BouncyCastle pin | 0222 |
| CODE-4 Input validation | Implemented | §2.6 | 0025, 0245 |
| RESILIENCE-1 Platform integrity | Partial: heuristic root check | `AppContainer.deviceLooksRooted` | 0038 |
| RESILIENCE-2 Anti-tampering | Partial: signing only | | 0040, 0224 |
| RESILIENCE-3 Anti-static analysis | Implemented: R8 | `app/build.gradle.kts` | 0045 |
| RESILIENCE-4 Anti-dynamic analysis | Gap | NOTE E-5 | 0039, 0263 |
| PRIVACY-1 Minimisation | Implemented | Landmarks memory only; no identity columns; masked online text | 0254, 0255 |
| PRIVACY-2 No identification | Implemented | No identifiers; no analytics; MediaPipe statistics stubbed | 0318 |
| PRIVACY-3 Transparency | Implemented | First run, online notice, per-session consent, not-a-statement notice | 0256 |
| PRIVACY-4 Control | Implemented | Retention, delete, Clear all data | 0319 |

## 4. Key management

| Key | Where | Rotation | Loss impact |
| --- | --- | --- | --- |
| Database key (DEK) | Random, wrapped in `noBackupFilesDir/dek.bin` | On Clear all data, or when the KEK is lost | Sessions unrecoverable (short-lived data) |
| KEK `signsetu.kek.unlocked` | Android Keystore, non-exportable, unlocked-device only | On reinstall, or when Android drops it | Store deleted, new keys made, officer told once |
| KEK `signsetu.kek.open` | Keystore, only while the phone has no secure lock | Moved under the unlocked-only key at the first unlock with a lock | Same |
| Gateway token key `signsetu.gateway.token` | Keystore, wraps `gw.bin` | On Clear of the token (401) | Device enrols again with a new code |
| Export passphrase | Shown once | Per export | Report unreadable; export again |
| Link keys | ECDH and HKDF per link, memory only | Every link and resume | Link closes; pair again |
| Resume secret | Memory only | Every new link; zeroed on Bye, expiry, lock, end | Reconnect needs the code |
| Release signing key | Release manager and second custodian, offline | On compromise (v3 rotation) | Updates need the backup |

## 5. Threat model (STRIDE)

Trust boundaries are TB0 to TB3 of the [data flow](README.md#33-trust-boundaries).

| Boundary | Threat | STRIDE | Controls | Residual |
| --- | --- | --- | --- | --- |
| Phone at rest | Lost, stolen or seized phone read | I | SQLCipher, unlocked-only KEK, relock, retention, wipe while locked | A phone with no lock screen; officer SOP |
| Phone at rest | Family member who knows the phone PIN | S, I | Retention, finish and delete, OfficerAuth | Shared PINs; department PIN if the pilot shows it |
| Stage | Subject leaves the app or reads officer data | E, I | Pinning, officer handle, no drafts or notes on the stage | Unpinning gesture if the device allows it |
| Stage | Machine reading taken as the subject's answer | R, T | Subject confirms from pictures; grades; readings in their own section | None beyond reader accuracy |
| Record | Row edited after the fact | T, R | Hash chain, record digest, certificate | A full rewrite of the store with the DEK |
| OS and apps | Overlay tapjacking, screenshots, backups | T, I | Touch filter, FLAG_SECURE, backup exclusion | |
| OS and apps | Malicious intent or provider access | E | Nothing exported beyond the launcher and the job service | |
| Bluetooth | Rogue, relayed or wrong stage device | S, T | Bonded only, commit-then-reveal, code and check number, AEAD, strict counters | Officer misreads the check number |
| Network | Traffic read or altered | I, T | TLS, validated networks, masking | User-installed CA until pinning |
| Network | Stolen enrolment code or token used elsewhere | S | Single-use codes, rate limits, revocation, optional token life | Attestation not built |
| Gateway | Prompt injection through the assist proxy | E | System prompts allow-listed; size limits | User text is still free text |
| Build | Compromised dependency or signing key | T | Lockfile, verification, scans, SBOM, offline key | |
| Library | Usage statistics sent by MediaPipe | I | Upload backend excluded and replaced by a stub | |
| Device | Rooted phone, hooking, repackaging | T, E | Root heuristic, R8, signing | No anti-debug or integrity attestation |
| Process | Denial of service by a hostile peer | D | Frame caps checked before allocation, timeouts | Bluetooth jamming |

## 6. Findings and accepted risks

| ID | Finding | Severity | Decision |
| --- | --- | --- | --- |
| F-1 | No certificate pinning | Medium | Waits on the production host (NOTE E-1) |
| F-2 | DEK not bound to the biometric prompt | Low | Accepted; documented in AUTH-2 |
| F-3 | No debugger or hook detection | Medium | Waits on the department's MASVS-R decision (NOTE E-5) |
| F-4 | Root check is a heuristic | Low | Warn-only by design, so an emergency is never blocked |
| F-5 | A private build may compile in a direct assistant key | High if distributed | Release gate refuses it without `-PallowEmbeddedKey`; such a build must never leave the department |
| F-6 | The record digest joins fields without a separator | Low | The hash chain (length-prefixed fields) is the integrity control; the digest only matches re-exports |
| F-7 | Bundled BouncyCastle ships a trust-all `X509TrustManager` class (lint) | Info | Unused by the app; R8 keeps it only if referenced |

## 7. Testing plan

| Area | Tests | How |
| --- | --- | --- |
| Storage | MASTG-TEST-0001, 0200, 0201 | Pull app data on a rooted test phone; confirm only ciphertext, no backup, no logs |
| Crypto | 0013, 0015, 0212 | Static review of §2.2; key aliases and flags with `adb shell dumpsys keystore` |
| Auth | 0018, 0326, 0327 | Relock timings; each gated action; behaviour with no lock screen |
| Network | 0019, 0020, 0233 | mitmproxy with a user CA against a gateway build; confirm masking on the wire |
| Platform | 0007, 0035, 0250 | Drozer for exported components; overlay test app; screenshot attempts |
| Second screen | Pairing protocol | [08-two-device-bluetooth-test.md](08-two-device-bluetooth-test.md); a modified peer for wrong version, bad curve point, replayed and oversized frames |
| Resilience | 0038, 0039 | Frida against root and relock behaviour |
| Privacy | 0254, 0256 | Permission review of each variant's merged manifest; first-run and consent texts |

## 8. Review points

| When | Review |
| --- | --- |
| Every change | Review by someone other than the author; unit tests, release lint, semgrep, OSV-Scanner and gitleaks pass |
| Gate 1, hardening | This checklist re-checked; internal MAS-L2 review; threat model refreshed |
| Gate 2, security audit | CERT-In empanelled audit, source and binary |
