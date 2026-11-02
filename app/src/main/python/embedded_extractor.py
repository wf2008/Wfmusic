import json
import os
from yt_dlp import YoutubeDL


def _options(cookies_path=None):
    options = {
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "noprogress": True,
        "continuedl": False,
        "retries": 5,
        "fragment_retries": 5,
        "file_access_retries": 5,
        "socket_timeout": 30,
        "concurrent_fragment_downloads": 1,
    }
    if cookies_path and os.path.isfile(cookies_path):
        with open(cookies_path, "r", encoding="utf-8") as cookie_file:
            cookie_header = cookie_file.read().strip()
        if cookie_header:
            options["http_headers"] = {"Cookie": cookie_header}
    return options


def preview_info(url, cookies_path=None):
    options = _options(cookies_path)
    options["skip_download"] = True
    with YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=False)
        return json.dumps({
            "title": info.get("title") or "Untitled",
            "uploader": info.get("uploader") or info.get("channel") or "Unknown channel",
            "artist": info.get("artist") or info.get("uploader") or info.get("channel") or "Unknown artist",
            "album": info.get("album") or info.get("playlist") or "YouTube downloads",
            "duration": info.get("duration") or 0,
            "thumbnail": info.get("thumbnail") or ""
        })


def download_bestaudio(url, output_dir, cookies_path=None):
    os.makedirs(output_dir, exist_ok=True)
    options = _options(cookies_path)
    options.update({
        "outtmpl": os.path.join(output_dir, "%(title)s.%(ext)s"),
        "restrictfilenames": True,
        "overwrites": True,
    })
    last_error = None
    for audio_format in ("bestaudio[ext=m4a]", "bestaudio[acodec^=mp4a]", "bestaudio/best"):
        try:
            options["format"] = audio_format
            with YoutubeDL(options) as ydl:
                info = ydl.extract_info(url, download=True)
                path = ydl.prepare_filename(info)
                if os.path.isfile(path) and os.path.getsize(path) > 0:
                    return json.dumps({
                        "path": path,
                        "title": info.get("title") or "Untitled",
                        "artist": info.get("artist") or info.get("uploader") or info.get("channel") or "Unknown artist",
                        "album": info.get("album") or info.get("playlist") or "YouTube downloads",
                        "duration": info.get("duration") or 0,
                        "thumbnail": info.get("thumbnail") or ""
                    })
        except Exception as exc:
            last_error = exc
    raise last_error or RuntimeError("No playable audio stream was found")
