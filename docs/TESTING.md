# Testing Report - version 0.1.0 (Phase 1)

## What was verified automatically (GitHub Actions, every push)

| Check | Result |
|---|---|
| Project compiles, debug APK is produced (about 69 MB) | PASS |
| Script parser: emotion tags, `[Pause]` tags, unknown-tag reporting | PASS (unit tests) |
| Long script (about 10,000 Urdu characters, roughly 10 minutes) splits into chunks with no text lost | PASS (unit test) |
| Over-long sentences are split under the chunk limit | PASS (unit test) |
| WAV write / read round trip, streaming join of chunks, duration maths | PASS (unit test) |
| Silence trim, loudness matching, resampling | PASS (unit test) |

## What has NOT been tested yet

The build machine has no phone, no speaker and no API key, so **nothing below has been run on a real
device yet**. The code paths are complete (no dummy buttons), but each needs a real-phone check.

| # | Feature | Code status | Device test |
|---|---|---|---|
| 1 | Male voice generation | Implemented (Piper Ryan / Alan / Fasih, Kokoro, Gemini) | Needed |
| 2 | Female voice generation | Implemented (Piper Lessac / HFC, Kokoro, Gemini) | Needed |
| 3 | Urdu TTS | Implemented (Piper Fasih, phone TTS, Gemini) | Needed |
| 4 | English TTS | Implemented | Needed |
| 5 | Emotion generation | Implemented for Gemini only; offline engines show a warning | Needed (API key) |
| 6 | Voice cloning | **Pending** | - |
| 7 | Multi-speaker dialogue | **Pending** (engine ready, screen missing) | - |
| 8 | 10-minute audio generation | Implemented (chunk + join + resume); parser unit-tested | Needed |
| 9 | MP3 export | Implemented (LAME) | Needed |
| 10 | WAV export | Implemented | Needed |
| 11 | Offline processing | Implemented | Needed |
| 12 | Online processing | Implemented | Needed (API key) |
| 13 | Pause / Resume / Stop, resume after error | Implemented | Needed |
| 14 | Project saving, rename, delete, share | Implemented (Room database) | Needed |
| 15 | Model downloading, install, remove | Implemented | Needed |
| - | Audio editor | **Pending** | - |
| - | Full Urdu interface | **Pending** | - |
| - | Signed release APK | **Pending** - needs the owner's keystore in GitHub Secrets | - |

## Suggested first test on the phone

1. Install the APK, open **AI Model Manager**, install "Piper Urdu - Fasih".
2. **Text to Speech**: paste two Urdu lines, pick the Fasih voice, press Generate, then Play.
3. Export MP3 and WAV, open both in the phone's music player.
4. Paste a long script (about 9,000 characters). While it runs: Pause, Resume, Stop, then "Resume".
5. Optional: add a Gemini API key in Settings, pick a Gemini voice, try `[Happy]` and `[Sad]` lines.

Please report any step that fails together with the message shown on screen.
