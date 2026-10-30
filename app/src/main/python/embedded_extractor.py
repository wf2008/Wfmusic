import os
from yt_dlp import YoutubeDL


def download_bestaudio(url, output_dir, cookies_path=None):
    os.makedirs(output_dir, exist_ok=True)
    options = {
        "format": "bestaudio/best",
        "outtmpl": os.path.join(output_dir, "%(title)s.%(ext)s"),
        "noplaylist": True,
        "restrictfilenames": True,
        "quiet": True,
        "no_warnings": True,
        "overwrites": False,
    }
    if cookies_path and os.path.isfile(cookies_path):
        with open(cookies_path, "r", encoding="utf-8") as cookie_file:
            cookie_header = cookie_file.read().strip()
        if cookie_header:
            options["http_headers"] = {"Cookie": cookie_header}
    with YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=True)
        return ydl.prepare_filename(info)
