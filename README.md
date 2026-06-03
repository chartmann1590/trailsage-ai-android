# TrailSage AI

TrailSage AI is an Android-only offline-first GPS audio tour guide: **Your private road trip storyteller.** It downloads a local Gemma model, LiteRT-LM assets, a Sherpa-ONNX neural voice, tour content, local source notes, and an offline map before travel.

## Build

```powershell
$env:JAVA_HOME="H:\TrailSage-AI\.jdk\jdk-21.0.11+10" # or any JDK 21
.\gradlew.bat testDebugUnitTest assembleDebug
adb -s 37220DLJG001ML install -r app\build\outputs\apk\debug\app-debug.apk
```

The normal debug build needs no secret. Place an untracked `app/google-services.json` only when connecting Firebase locally. The app uses Kotlin 2.2, JDK 21 for the build JVM, Android SDK 36, and Java 21 source compatibility (required by the LiteRT-LM Android SDK, which ships Java 21 bytecode).

## Included

- Compose Material 3 setup gate and Stitch-derived screens
- A dedicated design system in `ui/theme/` (full Stitch token palette, light **and** dark/night-driving schemes, the Stitch type scale, and 24/12dp shape language) built directly from the Stitch `DESIGN.md` export
- A reusable component library in `ui/components/` (top bar, bottom nav, hero/story cards, dual-layer download bars, voice-preview card, large narration button, map status overlay, storage meter, status pills) and per-feature screens under `ui/screens/<feature>/`
- Room database, Hilt graph, ViewModels, WorkManager, DataStore, Flow, Navigation Compose
- SHA-256 asset manifests and resumable WorkManager downloader
- Real LiteRT-LM Android SDK with Gemma 4 E2B model lifecycle
- Bundled Sherpa-ONNX Android `v1.13.2` runtime with neural-first VITS/Piper voice playback
- GPS radius and bearing trigger engine
- Public-source Adirondack High Peaks Loop pack with verified OSM-derived PMTiles extract
- Python Wikimedia/OSM tour-pack builder
- Firebase Spark-compatible SDK dependencies and setup documentation

See [`docs/`](docs), [`PRIVACY.md`](PRIVACY.md), and [`ATTRIBUTION.md`](ATTRIBUTION.md). No paid API or API-key-required content service is used.

### Functional wiring

The AI Guide chat is RAG-first: it retrieves chunks from the installed tour pack and generates a structured answer through the on-device Gemma/LiteRT engine (falling back to a clearly-labeled demo template until a verified model is installed), and says it doesn't know when no local source context matches. Driving Mode plays narration through the neural-first `NarrationPlayer` and shows the Android-fallback warning (plus the `android_tts_fallback_used` analytics event) only when the system voice is used. Voice Settings previews and selects installed neural voices.

### Package namespace

The application namespace is intentionally `com.charles.trailsage` (the prompt suggested `com.trailsage.ai`). This is a deliberate project decision; everything else follows the prompt's package layout under `com/trailsage/ai/...` source directories.

## Production Asset Notes

Gemma and the LibriTTS neural voice archive are too large to commit and are downloaded through exact manifests with SHA-256 verification. Setup remains locked until required production assets verify. The debug APK includes the Sherpa native runtime and a real Adirondack PMTiles extract.
