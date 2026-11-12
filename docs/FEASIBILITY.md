# Technical Feasibility Analysis

Burhan Nexus AI Voice Studio - reviewed November 2026.
Rule followed: nothing is claimed that the underlying model cannot really do.

## 1. Model comparison

| Model | Runs inside an Android phone? | Urdu | English | Emotions | Voice cloning | License | Decision |
|---|---|---|---|---|---|---|---|
| **Piper (VITS)** | Yes, fast, via sherpa-onnx | Yes, 1 voice (Fasih, male) | Yes, many voices | No | No | Engine MIT; each voice has its own license | **Used** - offline engine |
| **Kokoro-82M** | Yes, but slower than real time on budget phones | No | Yes, 11 voices | No | No | Apache-2.0 | **Used** - higher quality English |
| **Android System TTS** | Yes, built in | Yes (Google voice data) | Yes | No | No | Part of the phone | **Used** - zero-download fallback, has pitch |
| **Gemini TTS (cloud)** | Cloud only | Yes (ur-PK, preview) | Yes | **Yes, real** | No | Google API terms, free tier with limits | **Used** - online engine |
| Pocket TTS | Yes (CPU) | No | Yes | No | Yes (English) | Open | Planned for English cloning |
| ZipVoice | Yes, heavy | No | Yes + Chinese | No | Yes (zero-shot) | Apache-2.0 | Candidate for cloning |
| Coqui XTTS-v2 | **No** (about 2 GB, needs PC/GPU) | Not official | Yes | Limited (copies the sample's style) | Yes | Coqui Public Model License - non-commercial | Server adapter only, later |
| F5-TTS | **No** (needs GPU) | No | Yes + Chinese | Follows the sample | Yes | Code MIT, weights CC-BY-NC | Server adapter only, later |
| OpenVoice v2 | **No** (PC) | No | Yes | Limited | Yes (tone colour) | MIT | Server adapter only, later |

## 2. What this means for the requested features

| Requirement | Reality | Status |
|---|---|---|
| Offline TTS, free | Fully possible with Piper / Kokoro / System TTS | Done |
| Urdu offline | Possible: Piper Fasih (male) and the phone's Google Urdu voices | Done |
| Urdu female, natural | Only through the cloud (Gemini) or the phone's system voice | Done (online) |
| 17 emotions | No offline phone model acts emotions. Only Gemini does | Done online; offline shows a warning instead of faking |
| Emotion tags | Parsed everywhere; acted only by Gemini. `[Pause: N seconds]` works on every engine | Done |
| 10-minute audio | Chunking + auto-join + resume | Done (logic unit-tested; device run still to be confirmed) |
| MP3 + WAV export | WAV native, MP3 through LAME | Done |
| Voice cloning in Urdu, offline | **Not possible today** with any open model on a phone | Pending - see plan |
| Voice cloning in English, offline | Possible with Pocket TTS | Pending (next phase) |
| Multi-speaker dialogue | Engine can already do it; needs its screen | Pending (next phase) |
| Audio editor | Standard DSP, no model needed | Pending (next phase) |
| No paid API required | True: offline engines need no key; Gemini uses the user's own free key | Done |

## 3. Architecture

```
UI (Jetpack Compose)
   |
GenerationManager  - chunk queue, pause / resume / stop, resume-after-error, ETA
   |                 foreground service + wake lock for background work
   |
TtsEngine adapter interface  (add a new model = add one class)
   |- SherpaEngine   offline  Piper / Kokoro   (sherpa-onnx 1.13.6 + ONNX Runtime)
   |- SystemEngine   offline  Android TextToSpeech
   |- GeminiEngine   online   Google Gemini TTS, user's key
   |
Audio: WAV reader/writer, silence trim, loudness match, resample, join, LAME MP3
Storage: Room (projects), private files (audio), Keystore-encrypted prefs (API key)
ModelManager: download, extract, verify, remove offline models
```

Notes:
- sherpa-onnx is pinned to 1.13.6. Version 2.x removed the built-in espeak phonemizer that the
  published Piper archives (including the Urdu voice) depend on.
- Minimum Android 8.0 (API 26). Offline neural models need a 64-bit or 32-bit ARM phone;
  2 GB RAM works for Piper, 4 GB+ is recommended for Kokoro.
- TensorFlow Lite, FFmpeg and WorkManager from the original wish-list are not used in this phase:
  ONNX Runtime covers every chosen model, the audio work is done with small native-free Kotlin
  DSP plus LAME, and a foreground service fits a user-started long job better than WorkManager.

## 4. Honest limits

- Offline neural Urdu has one voice. More Urdu voices would need new models to be trained.
- Piper's Urdu voice reads English words poorly; mixed Urdu + English scripts sound best with Gemini.
- Gemini's free tier has per-minute and per-day limits set by Google. A 10-minute script is
  roughly 10 or more requests and can hit the daily limit; the app waits, retries, and can resume later.
- Gemini accepts speed only as a hint (slow / normal / fast) and has no pitch control.
- Android does not report the gender of system voices; the app shows "Not reported".
