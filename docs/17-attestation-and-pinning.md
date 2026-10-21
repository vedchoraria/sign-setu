# 17. Device attestation and certificate pinning: assessment and design

Status: assessment and design. Nothing in this document is built. Both measures depend on facts that do not exist yet (the production gateway host and its certificate authority), so a partial implementation now would be fragile. The decisions that block work are listed in section 6.

## 1. What exists today

| Topic | Current state |
| --- | --- |
| Transport | `UrlHttp` uses `HttpURLConnection` over HTTPS with the platform trust store. `network_security_config.xml` only sets `cleartextTrafficPermitted="false"`, and `UrlHttp` also refuses any URL that is not `https://`. There are no pins. |
| Gateway host | Set at build time with `-PgatewayUrl`; empty means the app never connects. The reference blueprint (`render.yaml`) deploys to a free Render host whose certificate chain is managed by the host. |
| Device identity | A one-time enrolment code, kept on the server only as a SHA-256 hash, is traded at `POST /v1/enroll` for a random 256-bit bearer token. The server keeps only the token's SHA-256. |
| Token handling on the phone | Wrapped by a non-exportable Keystore key (`signsetu.gateway.token`) that needs no screen unlock, stored in a file excluded from backup. Any code running as the app can use it. |
| Token lifetime | None. A token works until an administrator sets `revoked`; there is no rotation and no expiry. |
| Abuse limits | A per-device rate limit, and per-address and global wrong-code limits on enrolment (D-54). |

## 2. Threat model

Assets: the assistant proxy (cost and misuse), the model files, the consented feedback labels, and the confidentiality of masked text in transit. Subject data goes to the gateway only as masked text for the optional assistant and counsellor.

| Threat | Attacker | Current control | Gap |
| --- | --- | --- | --- |
| Network attacker reads or alters traffic | Hostile Wi-Fi, a proxy with a user-installed root certificate | TLS with the platform trust store | A certificate authority that the user or malware installed is trusted. Pinning narrows this. |
| Stolen enrolment code used from an unofficial client | Anyone who obtains a code | The code is single use; rate limits | Nothing proves the enrolling device is a genuine phone running the genuine app. Attestation narrows this. |
| Stolen token used from another machine | Malware on the phone, a copied file | Token wrapped by a non-exportable Keystore key | Malware running as the app can still call the gateway. Neither measure stops this; short token life and revocation do. |
| Rogue or compromised gateway host | Hosting provider, DNS hijack | TLS and hostname checks | Pinning to the department's certificate authority narrows it. |

Trust boundaries: the phone (officer-controlled, possibly rooted), the network (untrusted), the gateway (department-controlled), and the upstream AI service (third party; its key is held only by the gateway).

## 3. Certificate pinning

Recommendation: yes, but only after the production host and certificate authority are fixed, and only with the safeguards below. Android's own mechanism is the right tool, with no code and no new dependency: a `pin-set` in `network_security_config.xml` for the gateway domain.

Design:
- Pin the SubjectPublicKeyInfo of a certificate authority in the chain, not the leaf. Leaf certificates from managed hosts renew every 60 to 90 days, so pinning one would lock every phone out at each renewal.
- Ship at least two pins: the current authority and a backup, from a different authority or key, that is not yet in use.
- Set the pin-set `expiration` attribute. After that date Android ignores the pins and uses the normal trust store, so a pinning mistake or an unexpected authority change locks phones out of the gateway for a bounded time and never permanently. A build already stops starting sessions after 180 days, so set the expiration to the build date plus 180 days; pins and build life then end together.
- The app works fully offline, so a failed pin degrades to the on-device assistant with the existing "online assistant unavailable" notice. The app must never fall back to unpinned TLS by itself, and TLS verification is not weakened anywhere.
- Debug builds may use a `debug-overrides` element for a local test authority; release builds must not.
- A department that inspects TLS on its network breaks pinning by design. It ships its own build with its inspection authority pinned, or builds without pins.
- Recovery after an authority change: ship a new build with the new pin before the old authority stops issuing. The 180-day build life already forces regular updates, and the backup pin covers the gap.

Testing: a unit test that parses the generated config and checks for two pins and an expiration; a manual test against a staging host with a deliberately wrong pin, to confirm the call fails closed and the app falls back to offline.

## 4. Key Attestation

Recommendation: worth adding at enrolment, as one more check beside the one-time code, and not as proof of a clean phone. It needs server work and a policy the department must choose.

What it proves: an AndroidKeyStore key was generated in hardware-backed storage on a device whose boot state and security patch level are reported by the trusted environment, for an app with a given package name and signing certificate. It does not prove that the device is uncompromised, that the app is not hooked at run time, or who the officer is. It must not be described as proof of any of those.

Design:
1. `POST /v1/enroll/challenge` returns a random 32-byte nonce, stored on the server with a short life (two minutes).
2. The app generates an EC key in the Keystore with `setAttestationChallenge(nonce)` and sends the certificate chain with the enrolment code.
3. The server verifies, in this order: the chain is well formed and ends in a Google attestation root configured on the server; no certificate in the chain is on Google's revocation list; the nonce exists, is unexpired and is consumed (single use, which stops replay); the key description reports a hardware security level; the application id and signing-certificate digest equal the department's release key; verified boot meets the policy; the security patch level is inside the policy window.
4. Only then is the code consumed and a token issued, recorded against the attested key.

Nothing secret goes into the APK: the nonce and all trust roots live on the server.

Optional later step: bind the token to the attested key by having the app sign each request, so a copied token is useless. This changes every call and is not recommended until a threat justifies it.

Limits and risks:
- Phones without hardware-backed attestation, and custom ROMs, fail verification. The policy must say whether such a phone is refused or enrolled with a warning flag; refusing may exclude officers' older phones.
- Google's attestation roots and revocation list change over time. The server needs a documented update procedure, or enrolment will start failing without warning.
- Play Integrity is not an option in the `standard` flavor, which has no Play services.

Server requirements: a nonce table; an X.509 and ASN.1 parser for the key description (the existing `cryptography` dependency covers certificates, and the extension needs an ASN.1 library); configuration for roots and policy; and tests with recorded real chains (valid, expired, wrong package, replayed nonce, revoked).

## 5. Token life and revocation (recommended regardless)

Both measures above are weaker than ordinary token hygiene. Add a token expiry, with a new enrolment code needed after it; a `last_seen` timestamp on devices; and an admin route to revoke a device. These need no new infrastructure.

## 6. Decisions needed before building

1. The production gateway hostname and the certificate authority that issues its certificate, so the pins can be chosen.
2. Whether the department inspects TLS on its network.
3. The attestation policy: refuse or flag phones without hardware-backed attestation, and the required patch level.
4. Who maintains Google's attestation roots and revocation list on the server, and how often.
5. Token life.
