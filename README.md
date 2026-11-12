# Burhan Nexus AI Voice Studio

**Your Voice. Your Emotion. Your Studio.**

Android text-to-speech studio by Burhan Nexus: offline AI voices (Piper, Kokoro, phone TTS) plus an
optional online engine (Google Gemini TTS) for Urdu, English and real emotions.

- APK: see **Releases -> latest** (built automatically by GitHub Actions on every push).
- Urdu guide (install, models, API key, usage): [docs/GUIDE-URDU.md](docs/GUIDE-URDU.md)
- Feasibility analysis and model comparison: [docs/FEASIBILITY.md](docs/FEASIBILITY.md)
- Testing report and feature status: [docs/TESTING.md](docs/TESTING.md)

## Build

Open the folder in Android Studio (JDK 17) or run `gradle assembleDebug` with Gradle 8.7.
The sherpa-onnx AAR is downloaded automatically into `app/libs` on the first build.

A signed release APK is produced by CI only when these repository secrets exist:
`RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
`app/debug.keystore` is a debug-only key so that test builds can be updated in place; it is not a release key.

Developed by Burhan Nexus | 03475093446
