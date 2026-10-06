# QA harnesses

PowerShell scripts used to exercise the app against the **live** Gemini API and to
patch teaching instructions between iterations. They read the API key from the
console at runtime — no key is stored in this repository.

| Script | Purpose |
|--------|---------|
| `sprawdz-styl-live.ps1` | Opens a Live WebSocket session and checks the tutor's style (jab, correction, retry) on real model replies |
| `sprawdz-scenariusze-wymowe.ps1` | Walks scenario lesson plans and captures audio for pronunciation review |
| `ocen-wymowe-audio.ps1` | Scores recorded scenario audio samples |
| `adapt-roast.ps1`, `adapt-neon-roast.ps1` | Apply instruction changes to `PersonalityEngine.kt` between the 2.1.1 → 2.2.x iterations |

`logs/` holds raw emulator runs kept as evidence of the test sessions.
