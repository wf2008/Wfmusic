import json
import os
from yt_dlp import YoutubeDL


def _options(cookies_path=None):
    options = {"noplaylist": True, "quiet": True, "no_warnings": True}
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
            "duration": info.get("duration") or 0,
            "thumbnail": info.get("thumbnail") or ""
        })


def download_bestaudio(url, output_dir, cookies_path=None):
    os.makedirs(output_dir, exist_ok=True)
    options = _options(cookies_path)
    options.update({
        "format": "bestaudio[acodec^=mp4a]/bestaudio[ext=m4a]/bestaudio/best",
        "outtmpl": os.path.join(output_dir, "%(title)s.%(ext)s"),
        "restrictfilenames": True,
        "overwrites": False,
    })
    with YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=True)
        return ydl.prepare_filename(info)
