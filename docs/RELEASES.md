# Release history

All builds share the application id `com.locospanish.ai` and the same local signing
identity, so each one installs over the previous as an update without wiping the
user's key, settings or progress.

| Version | Build | Headline change | Verification |
|---------|-------|-----------------|--------------|
| 2.1.0 | 3 | Migration from OpenAI to the Gemini Live API; phone talks to Google directly, no backend | build + unit tests |
| 2.1.1 | 11 | "Roast tutor" personality: reacts to mistakes with a short jab, then the correct Spanish form | release build, lint, **27 unit tests**, live session with 9 model replies; APK hash recorded |
| 2.1.2 | — | Laughter samples retimed (2 s), Bluetooth/headset routing fixes, neon UI pass | manual device checks |
| 2.2.0 | 30 | PL↔ES teaching rhythm: one attempt → jab → correction → retry; Polish ripostes, Spanish drills; "Quiz me / Go easier / Next round" actions | build + unit tests |
| 2.2.1 | — | Jabs also after correct answers and ordinary questions, not only after mistakes; style reminder before every graded turn; uncertain audio is never mocked; in-app rollback switch to 2.2.0 behaviour | unit tests for config, migration and rollback |
| **2.3.0** | **24** | **Per-scenario lesson plans and vocabulary** for all 18 scenarios (hotel starts with booking → room → key); correct attempts are stored and move the next goal; Polish commentary and Spanish model sentences are separate utterances spoken by the same Gemini voice | build + lint, **47 unit tests + 10 client tests** in the emulator, 4 scenarios (hotel, café, taxi, electronics) exercised against the live API |

## What is in this repository

- **Source**: the 2.2.0 iteration (`versionName 2.2.0-roast-pl-es`, versionCode 30) —
  the most recent snapshot available in the project archive.
- **Binary**: the 2.3.0 release APK in [`release/`](../release), SHA-256
  `9B7AA931958DECD5C004B5E89F3274C033843D5CEE43BF49AB95A86BF387335F`, which matches
  the hash recorded in the 2.3.0 release note.
- The 2.2.1 and 2.3.0 source trees were produced in a later working copy that is not
  part of this archive, so the code here is one minor version behind the shipped APK.

## Known gaps (deliberately not hidden)

- Pronunciation quality has not been independently assessed; the phonetic review
  described in `docs/pl/DIAGNOZA-STYLU-LIVE.md` was never finished.
- Latency was not measured on a physical phone, and behaviour is not guaranteed on
  every Android model.
- Gemini is non-deterministic: the app constrains style, pacing and teaching
  mechanics, but not the exact wording of any reply.
