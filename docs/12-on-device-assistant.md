# 12. On-Device Assistant

The assistant translates what the officer types into the subject's language and back into English, so the officer can see a wrong translation before showing it. It runs on the phone when a model is installed, and otherwise through the online path (§6) when the officer has switched that on. It never answers, judges or signs, plays no part in Auto mode, and never sees camera frames or landmarks.

## 1. What the officer sees

1. The officer types in the composer and taps **Assistant**.
2. A card shows the caption in the subject's language, marked "machine translation, not checked", and below it "Which means" with the caption translated back into English. If the back-translation failed, that line is left out; the officer's own words are never shown in its place.
3. If the meaning is right, Send shows the caption on the stage; the character still signs, gestures or spells the officer's own words. If it is wrong, the officer closes the card and the text is shown as typed.
4. The log keeps the officer's words and the caption that was shown.

The assistant is skipped when the subject's language is English, or Hindi typed in Devanagari, since nothing needs translating.

## 2. Model and runtime

| Item | Choice | Reason |
| --- | --- | --- |
| Runtime | llama.cpp v0.5.0 (MIT), built from source by CMake, vendored in the repository at a pinned commit | Open source, CPU only, runs on arm64 phones and the x86_64 emulator |
| Model | Gemma 3 1B instruction-tuned, Q4_K_M GGUF, 806 MB (Gemma Terms of Use) | Best Hindi of the small models tried; Qwen2.5-1.5B (Apache-2.0) mistranslated three of four test questions |
| Decoding | Greedy, at most 120 tokens per direction, a 1,024-token context emptied per request, 2 to 4 threads; the prompt is fed in batches of 256 tokens | The same input always gives the same output; a small batch keeps the compute buffer small |
| Native library | `libassistant.so` for arm64-v8a and x86_64 (about 5.9 MB each); text crosses JNI as real UTF-8 | 32-bit phones cannot map the model |

The Gemma licence is free for government use but is not an OSI open-source licence; it passes on Google's prohibited-use policy. A fully open model of the same size with good Indic output can replace it by adding its hash (§3).

## 3. Installing the model

- The department downloads the model once and hands it to officers as a file (SD card, USB, or a shared drive).
- Settings, **On-device assistant**, **Import model file**: the app copies the file into its private storage and computes its SHA-256 while copying.
- Only files whose hash is in `ModelStore.KNOWN_MODELS` are kept; any other file is deleted before it is parsed, so a swapped or damaged file never reaches the native parser.
- The app never downloads the model, even when it has a gateway.
- **Remove the model** deletes it.

| Model | SHA-256 |
| --- | --- |
| [`gemma-3-1b-it-Q4_K_M.gguf`](https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf) from `ggml-org/gemma-3-1b-it-GGUF` on Hugging Face | `8ccc5cd1f1b3602548715ae25a66ed73fd5dc68a210412eea643eb20eb75a135` |

## 4. Budgets

| Resource | Measured or set | Note |
| --- | --- | --- |
| Phone memory | Offered only on phones reporting at least 2.4 GB in total; loaded only when at least 1.2 GB is free and the system is not low on memory | A 3 GB phone reports well under 3 GB. The model maps about 0.9 GB; with it loaded the session exceeds the 300 MB memory target, so it is loaded on first use and freed when the app locks, under memory pressure, and whenever the stage shows or a sign window opens. The free-memory check at load time accounts for the camera and the vision engine |
| APK | `libassistant.so` adds about 5.9 MB per ABI | The arm64-v8a phone APK stays within the 100 MB target |
| Time | 66 s on the x86_64 emulator for one question, including loading the model | To be measured on the oldest supported arm64 phone; arm64 is built for the plain ARMv8 baseline |

## 5. Tests

- Unit: the hash allow-list and the "already in the subject's script" skip.
- Emulator: import accepted for the known file and refused for another; "are you hungry? do you want water?" gives "आप भूखे हैं? क्या आप पानी चाहते हैं?" and back "Are you hungry? Do you want some water?".
- Field: language reviewers spot-check assistant output for each pilot language before officers rely on it.

## 6. Online path

`CloudAi` serves the translation when no local model is ready, and is the only path for the AI counsellor and the draft report.

| Item | Behaviour |
| --- | --- |
| Where it goes | The department gateway (`POST /v1/assist`) when the build has one; otherwise the OpenAI compatible service (groq for test phase), with the key the officer enters in Settings, using chat completions |
| When | Only with the online switch on (Settings, after a notice), the phone on a validated network, and, for the gateway, the device enrolled with a one-time department code |
| Masking | `Pseudonymizer` replaces the subject's names and written details from the session, and email addresses, links, PAN-like ids and long numbers, with stand-ins before sending, and restores them in the reply |
| Consent | Session text (counsellor history, draft report) goes only after the officer allows it once for the session |
| Failure | One retry when the service is unreachable, rate-limited (429) or failing (5xx); a 401 clears the token; the officer is told once per change (offline, error, revoked, back online) |
| Prompts | The gateway accepts only the app's own system prompts, listed in `backend/signsetu_gateway/app_prompts.json` and written by `GatewayPromptContractTest` |
