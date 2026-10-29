from pathlib import Path
import os, secrets, subprocess
from fastapi import FastAPI, HTTPException
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, AnyHttpUrl

app = FastAPI(title="Offline Music Extractor")
OUT = Path(os.getenv("OUTPUT_DIR", "/tmp/offline-music")); OUT.mkdir(parents=True, exist_ok=True)
app.mount("/files", StaticFiles(directory=OUT), name="files")

class ExtractRequest(BaseModel):
    url: AnyHttpUrl

@app.post("/extract")
def extract(request: ExtractRequest):
    # Deploy only for content you own or have permission to download.
    token = secrets.token_hex(8)
    template = str(OUT / f"{token}.%(ext)s")
    try:
        result = subprocess.run([
            "yt-dlp", "--no-playlist", "--restrict-filenames", "-x", "--audio-format", "mp3",
            "--audio-quality", "192K", "--ffmpeg-location", os.getenv("FFMPEG_LOCATION", "ffmpeg"),
            "-o", template, str(request.url)
        ], check=True, capture_output=True, text=True, timeout=600)
    except subprocess.CalledProcessError as exc:
        raise HTTPException(422, f"Extractor failed: {exc.stderr[-500:]}")
    except subprocess.TimeoutExpired:
        raise HTTPException(504, "Extractor timed out")
    files = list(OUT.glob(f"{token}.*"))
    if not files: raise HTTPException(500, "No output was produced")
    return {"title": files[0].stem, "download_url": f"/files/{files[0].name}"}
