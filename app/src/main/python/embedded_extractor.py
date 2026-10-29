import os
from yt_dlp import YoutubeDL


def download_bestaudio(url, output_dir):
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
    with YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=True)
        return ydl.prepare_filename(info)
