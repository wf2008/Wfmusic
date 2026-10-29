# Wfmusic

A small Android music library/player with direct audio downloads and an optional `yt-dlp` + FFmpeg companion extractor. The app is branded **Wfmusic** with a WF launcher/notification logo. It is intentionally designed for content you own or are authorized to download; it does not bypass DRM, paywalls, private content, or access controls.

## Android app

The app now has two tabs:

- **Download**: direct MP3/M4A/OGG URLs work without a service. YouTube/Vimeo URLs are sent to the extractor service, which converts authorized media to MP3.
- **Library / Player**: shows every downloaded track and provides play/pause, seek, elapsed/duration display, previous/next, shuffle, repeat, and offline playback.

Downloads use Android `DownloadManager`, so they continue while the app is backgrounded. Active playback hands off to a foreground media service with a persistent notification, so audio can continue when the app is backgrounded or the screen is locked.

For an Android emulator, the default extractor URL is `http://10.0.2.2:8000`. For a physical phone, use the computer's LAN IP, for example `http://192.168.1.20:8000`, and keep the phone and computer on the same network. Use HTTPS/authentication before exposing the service outside a trusted network.

## Optional extractor service

```bash
cd service
python -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
# Install FFmpeg using your OS package manager, then:
uvicorn main:app --host 0.0.0.0 --port 8000
```

The service accepts `POST /extract` with `{ "url": "https://..." }`, calls `yt-dlp` with FFmpeg audio extraction, and returns a downloadable MP3 URL. The Android app automatically calls this endpoint when a YouTube or Vimeo URL is pasted.

Only process media you own or have explicit permission to download. Do not use the service to bypass DRM, private content, paywalls, or access controls.

## GitHub Actions

The workflow builds a debug APK on push to `main` or through **Actions → Android APK → Run workflow**. The successful artifact is uploaded as `offline-music-player-debug`.

## Local validation

The sandbox used to author this project does not include the Android SDK or Gradle executable. GitHub Actions is the reproducible APK build path. The Python service and XML resources are validated locally before each push.
