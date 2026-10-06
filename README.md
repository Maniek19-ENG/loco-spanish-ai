# LOCO Spanish AI — real-time voice tutor on Android (Kotlin + Gemini Live)

**Latest release: 2.3.0** · 18 scenario lesson plans · 47 unit tests + 10 client tests

An Android app that holds a **spoken Spanish conversation** with the learner: the
phone streams microphone audio to the **Gemini Live API** over a WebSocket and plays
the model's voice back with barge-in, so the user can interrupt mid-sentence like in
a real conversation. Explanations are in Polish, practice is in Spanish, across
CEFR levels A1–C2 and 18 role-play scenarios.

No backend, no server, no PC: the phone talks to Google directly. The user supplies
their own Gemini API key, which is encrypted with the Android Keystore and never
shipped inside the APK.

```
 microphone ──16 kHz PCM──▶ WebSocket (TLS) ──▶ Gemini Live
     ▲                                              │
     └──── barge-in / interrupt ◀── 24 kHz PCM ◀────┘
```

## Why it's interesting

- **Full-duplex audio pipeline, hand-written** — `AudioRecord` capture at 16 kHz
  mono PCM in ~100 ms chunks, Base64-framed into Google's bidirectional protocol;
  playback through a separate `AudioTrack` queue at 24 kHz off the UI thread.
  Hardware `AcousticEchoCanceler` and `NoiseSuppressor` are enabled when the device
  exposes them, and Bluetooth/wired headset routing is handled explicitly.
- **Interruption that actually works** — user speech cancels the in-flight model
  turn, flushes the playback queue and marks the transcript entry as interrupted,
  rather than letting the two sides talk over each other.
- **Defensive networking** — 25 s connection timeout, 20 s WebSocket ping,
  256 kB send-queue ceiling, bounded receive buffer, typed error mapping for Google
  status codes, and a manual resume that replays a short conversation summary
  instead of the whole history.
- **Secrets done properly** — the personal API key is encrypted with AES-GCM via
  the Android Keystore (`AccessTokenStore.kt`); nothing sensitive is compiled into
  the APK and the repository contains no keys.
- **Teaching logic as code, not prompt soup** — `PersonalityEngine.kt` and
  `PromptBuilder.kt` turn level, scenario, four personality sliders and profanity
  settings into a bounded system instruction, with a contract test
  (`PersonalityContractTest.kt`) asserting the generated prompt keeps its guarantees.

## Features

- Speech-to-speech conversation with continuous streaming and voice interruption.
- Polish explanations, Spanish practice; levels A1–C2 control sentence length,
  grammar difficulty and how much Spanish is used.
- 18 scenarios: daily talk, bar, restaurant, café, shopping, hotel, airport, taxi,
  meeting people, dating, renting, job interview, employer, hotel work, maintenance,
  electronics, emergency, free talk.
- Four personality presets (Teacher, Spanish buddy, Roast tutor, Custom), four 0–100
  sliders (sarcasm, cheek, roast intensity, correction strength), three profanity
  levels and up to 2000 characters of custom style preferences.
- Live transcript of both sides, mute, repeat, slow down, "I don't understand",
  "explain in Polish", and an offline personality test that previews the tutor's
  style without starting a session.
- Per-scenario lesson plans (2.3.0): each of the 18 scenarios has its own goal
  sequence and vocabulary; correct attempts are recorded and advance the next goal.
- In-app rollback switch to the previous teaching style, so a behaviour change can be
  reverted on the phone without reinstalling or losing data.
- Local progress: session history, time, streak, vocabulary, recurring mistakes and
  completed scenarios, stored with Preferences DataStore — nothing is invented
  before the first real conversation.

## Tech stack

| Area | Choice |
|------|--------|
| Language / UI | Kotlin, Jetpack Compose, Material 3 (dark theme) |
| Architecture | `AndroidViewModel`, `StateFlow`, coroutines |
| Realtime | Gemini Live API (`gemini-3.8-live`) over OkHttp WebSocket; `gemini-3.8-flash` for the personality preview |
| Audio | `AudioRecord` / `AudioTrack`, PCM 16-bit, AEC + noise suppression, SCO/Bluetooth routing |
| Storage | Preferences DataStore, Gson; API key in Android Keystore (AES-GCM) |
| Build | Gradle KTS, compileSdk 37, minSdk 26 (Android 8.0), Java 17 |
| Tests | JUnit unit tests + instrumented tests (lifecycle, PCM audio, Gemini integration) |

~1,500 lines of Kotlin across 16 source files.

## Build and run

```bash
./gradlew assembleRelease     # APK in app/build/outputs/apk/release
./gradlew test                # unit tests
./gradlew connectedAndroidTest  # instrumented tests (device/emulator needed)
```

Then, on the phone: install the APK → **Settings → open Google AI Studio** → create a
key in a **free-tier project with no billing attached** → paste it into LOCO → pick a
level and scenario → **Start conversation** and allow microphone access.

The latest signed sideload build, **2.3.0**, is in [`release/`](release/).
Its SHA-256 is
`9B7AA931958DECD5C004B5E89F3274C033843D5CEE43BF49AB95A86BF387335F`, matching the
hash recorded in the 2.3.0 release note. Full version history:
[`docs/RELEASES.md`](docs/RELEASES.md).

## Versions and verification (honest version)

Shipped through seven iterations, 2.1.0 → 2.3.0 — see
[`docs/RELEASES.md`](docs/RELEASES.md) for the full table.

Checked for the latest release (**2.3.0**):

- release build and lint clean;
- **47 unit tests + 10 client tests** green in the emulator;
- four scenarios (hotel, café, taxi, electronics) exercised against the **live**
  Gemini API, with recordings kept;
- APK hash published in the release note and re-verified against the binary here.

Earlier, release 2.1.1 was verified the same way with 27 unit tests and a nine-reply
live session.

**Caveats:** the source in this repository is the 2.2.0 iteration — the 2.2.1 and
2.3.0 trees were built in a later working copy that is not part of this archive, so
the code is one minor version behind the APK. Pronunciation quality was never
independently assessed, latency was not measured on a physical phone, and Gemini is
non-deterministic: the app constrains style, pacing and teaching mechanics, not the
exact wording of a reply.

## Repository layout

```
├── app/                      Android application module (Kotlin, Compose)
│   ├── src/main/java/com/locospanish/ai/
│   │   ├── realtime/         Gemini Live WebSocket client, protocol, events
│   │   ├── voice/            PCM capture/playback, audio routing
│   │   ├── personality/      personality engine (presets, sliders)
│   │   ├── tutor/            prompt builder (level, scenario, memory)
│   │   ├── ui/               Compose UI + ViewModel
│   │   └── data/, model/, repository/
│   ├── src/test/             unit tests (domain, protocol, personality contract)
│   └── src/androidTest/      instrumented tests
├── release/                  signed sideload APK (2.3.0) + published hash
├── docs/RELEASES.md          version history and verification per release
├── docs/pl/                  original Polish documentation, spec and release notes
└── tools/                    PowerShell QA harnesses (live-API style and scenario
                              checks, pronunciation sampling) + emulator logs
```

## Privacy and cost

The app sends audio only to Google's Gemini endpoint and stores progress locally on
the device. The API key belongs to the user and stays on the device. Google's free
tier limits apply; a key from a billing-enabled project can incur charges — the app
cannot verify a project's billing plan from the key alone, so the in-app confirmation
is a user declaration, not a technical guarantee.

## License

MIT — see [LICENSE](LICENSE).
