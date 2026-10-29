# Offline Music Player

A small Android music library/player with direct audio downloads and an optional `yt-dlp` + FFmpeg companion extractor. It is intentionally designed for content you own or are authorized to download; it does not bypass DRM, paywalls, private content, or access controls.

## Android app

The app downloads direct `http`/`https` audio URLs into app-private external storage and plays downloaded files offline. The GitHub Actions workflow builds a debug APK and uploads it as an artifact.

1. Open **Actions → Android APK → Run workflow** (or push to `main`).
2. Download the `offline-music-player-debug` artifact.
3. Install the APK on an Android 8+ device.

The current app accepts direct MP3/M4A/OGG URLs. A service URL such as YouTube requires the companion extractor below; the Android UI includes a clear note rather than silently pretending it can run Python binaries locally.

## Optional extractor service

```bash
cd service
python -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
# Install FFmpeg using your OS package manager, then:
uvicorn main:app --host 0.0.0.0 --port 8000
```

The service endpoint accepts `POST /extract` with `{ "url": "https://..." }` and uses `yt-dlp` plus FFmpeg to create an MP3. Put it behind HTTPS and authentication before exposing it publicly, and only process authorized content. The Android app currently supports the direct output URL returned by the service; wiring a hosted endpoint into the app is the next configuration step.

## Local validation

The sandbox used to author this project does not include the Android SDK or Gradle executable. The repository is therefore configured for reproducible GitHub Actions builds using Java 17, Android API 35, Build Tools 35.0.0, and Gradle 8.9. The workflow is the authoritative APK build/test path.
