# TrailSage AI

TrailSage AI is an Android-only offline-first GPS audio tour guide: **Your private road trip storyteller.** It downloads a local Gemma model, LiteRT-LM assets, a Sherpa-ONNX neural voice, tour content, local source notes, and an offline map before travel.

## Build

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
adb -s 37220DLJG001ML install -r app\build\outputs\apk\debug\app-debug.apk
```

The normal debug build needs no secret. Place an untracked `app/google-services.json` only when connecting Firebase. Large AI, voice, native runtime, and PMTiles assets are manifest-driven external downloads and are not committed.

## Included

- Compose Material 3 setup gate and Stitch-derived screens
- SHA-256 asset manifests and resumable WorkManager downloader
- RAG-first local AI abstraction with compile-safe LiteRT-LM adapter
- Neural-first TTS selection with opt-in Android TTS fallback
- GPS radius and bearing trigger engine
- Public-source Adirondack High Peaks Loop demo pack
- Python Wikimedia/OSM tour-pack builder
- Firebase Spark-compatible SDK dependencies and setup documentation

See [`docs/`](docs), [`PRIVACY.md`](PRIVACY.md), and [`ATTRIBUTION.md`](ATTRIBUTION.md). No paid API or API-key-required content service is used.

## Known Limitations

The checked-in PMTiles file and runtime adapters are explicit demo placeholders. A production build must package the Sherpa Android native runtime, wire the current LiteRT-LM Android binding, download verified model assets, and build a real local OSM PMTiles extract.

