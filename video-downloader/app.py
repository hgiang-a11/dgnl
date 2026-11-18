"""
Tải Video – máy chủ cho ứng dụng tải video từ link.

Chạy:  python app.py   (hoặc dùng run.bat / run.sh)
Sau đó mở http://127.0.0.1:8000 trên trình duyệt.

Luồng xử lý:
  1. Giao diện gửi link -> POST /api/jobs, máy chủ tạo một "lượt tải" chạy nền.
  2. yt-dlp đọc thông tin video (tiêu đề, ảnh bìa...) rồi tải video về thư mục downloads/.
  3. Giao diện hỏi tiến độ qua GET /api/jobs/<id> vài lần mỗi giây.
  4. Xong thì giao diện phát xem trước qua /media và tải về qua /download.
"""

from __future__ import annotations

import mimetypes
import os
import re
import shutil
import socket
import subprocess
import threading
import time
import unicodedata
import urllib.parse
import urllib.request
import uuid
import webbrowser
from pathlib import Path

import yt_dlp
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

BASE_DIR = Path(__file__).resolve().parent
STATIC_DIR = BASE_DIR / "static"
DOWNLOAD_DIR = Path(os.environ.get("DOWNLOAD_DIR", BASE_DIR / "downloads"))
COOKIE_FILE = BASE_DIR / "cookies.txt"

HOST = os.environ.get("HOST", "127.0.0.1")
PORT = int(os.environ.get("PORT", "8000"))
JOB_TTL = int(os.environ.get("JOB_TTL", 2 * 60 * 60))  # giữ tệp 2 giờ rồi tự xóa
MAX_PARALLEL = int(os.environ.get("MAX_PARALLEL", 3))

QUALITIES = {"best": None, "1080": 1080, "720": 720, "480": 480, "360": 360, "audio": "audio"}

PLATFORM_NAMES = {
    "youtube": "YouTube", "youtubetab": "YouTube", "facebook": "Facebook", "instagram": "Instagram",
    "tiktok": "TikTok", "twitter": "X (Twitter)", "vimeo": "Vimeo", "reddit": "Reddit",
    "dailymotion": "Dailymotion", "soundcloud": "SoundCloud", "twitch": "Twitch",
    "bilibili": "Bilibili", "pinterest": "Pinterest", "threads": "Threads", "linkedin": "LinkedIn",
    "zingmp3": "Zing MP3", "nhaccuatui": "NhacCuaTui",
}


# ---------------------------------------------------------------------------
# Công cụ phụ: ffmpeg (ghép hình + tiếng, đổi sang MP3) và Deno (cần cho YouTube)
# ---------------------------------------------------------------------------

def find_ffmpeg() -> str | None:
    """Ưu tiên ffmpeg đã cài trên máy, nếu không có thì dùng bản đi kèm imageio-ffmpeg."""
    if shutil.which("ffmpeg"):
        return None  # yt-dlp tự tìm được
    try:
        import imageio_ffmpeg

        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        return None


def find_js_runtimes() -> dict:
    """YouTube cần một trình chạy JavaScript; bật mọi loại yt-dlp hỗ trợ, kèm Deno cài qua pip."""
    runtimes: dict = {"deno": {}, "node": {}, "bun": {}, "quickjs": {}}
    if not shutil.which("deno"):
        try:
            import deno

            runtimes["deno"] = {"path": deno.find_deno_bin()}
        except Exception:
            pass
    return runtimes


FFMPEG_LOCATION = find_ffmpeg()
HAS_FFMPEG = bool(FFMPEG_LOCATION or shutil.which("ffmpeg"))
JS_RUNTIMES = find_js_runtimes()


# ---------------------------------------------------------------------------
# Tiện ích
# ---------------------------------------------------------------------------

ANSI_RE = re.compile(r"\x1b\[[0-9;]*m")


def clean_error(msg: str) -> str:
    msg = ANSI_RE.sub("", str(msg or "")).strip()
    msg = re.sub(r"^ERROR:\s*", "", msg)
    msg = re.sub(r"^\[[^\]]+\]\s*", "", msg)  # bỏ tiền tố [youtube] ...
    msg = re.sub(r"^[\w-]{6,}:\s*", "", msg)  # bỏ mã video ở đầu
    return msg.split("\n")[0][:300]


def friendly_error(raw: str) -> str:
    """Đổi lỗi kỹ thuật của yt-dlp thành câu dễ hiểu."""
    m = raw.lower()
    rules = [
        (("unsupported url",), "Link này chưa được hỗ trợ hoặc trang không chứa video."),
        (("not a bot", "confirm you"), "YouTube đang yêu cầu xác minh bạn không phải máy. Hãy thử lại sau ít phút, "
                                       "hoặc dùng tệp cookies.txt (xem README)."),
        (("private", "login", "log in", "sign in", "cookies", "registered users", "authentication"),
         "Video riêng tư hoặc cần đăng nhập mới xem được. Bạn có thể dùng tệp cookies.txt (xem README)."),
        (("age-restricted", "age restricted", "confirm your age", "inappropriate"), "Video giới hạn độ tuổi, cần đăng nhập. Hãy dùng tệp cookies.txt (xem README)."),
        (("unavailable", "removed", "deleted", "does not exist", "no longer available", "404"),
         "Video không tồn tại, đã bị xóa hoặc bị chặn ở khu vực của bạn."),
        (("no video", "no media", "there's no video", "no formats"), "Không tìm thấy video nào trong link này."),
        (("requested format",), "Không có chất lượng phù hợp. Hãy chọn chất lượng khác rồi thử lại."),
        (("geo", "country", "region"), "Video bị chặn ở khu vực của bạn."),
        (("timed out", "getaddrinfo", "unable to download webpage", "connection", "network", "resolve"),
         "Không kết nối được tới trang video. Hãy kiểm tra mạng rồi thử lại."),
        (("ffmpeg",), "Thiếu ffmpeg để xử lý video. Hãy chạy lại bằng run.bat / run.sh để cài tự động."),
    ]
    for keys, text in rules:
        if any(k in m for k in keys):
            return text
    return "Không tải được video. Hãy kiểm tra lại link hoặc thử chất lượng khác."


def safe_filename(name: str, ext: str) -> str:
    name = unicodedata.normalize("NFC", name or "video")
    name = re.sub(r'[\\/:*?"<>|\x00-\x1f]+', " ", name)
    name = re.sub(r"\s+", " ", name).strip(" .") or "video"
    return f"{name[:120].rstrip()}.{ext}"


def content_disposition(kind: str, filename: str) -> str:
    plain = filename.replace("đ", "d").replace("Đ", "D")
    ascii_name = unicodedata.normalize("NFKD", plain).encode("ascii", "ignore").decode()
    ascii_name = re.sub(r'[^A-Za-z0-9 ._()-]+', "", ascii_name).strip() or "video"
    quoted = urllib.parse.quote(filename, safe="")
    return f'{kind}; filename="{ascii_name}"; filename*=UTF-8\'\'{quoted}'


def normalize_url(url: str) -> str:
    url = (url or "").strip()
    match = re.search(r"https?://\S+", url)  # lấy link nếu người dùng dán cả đoạn chữ
    if match:
        url = match.group(0)
    elif not re.match(r"^[a-z][a-z0-9+.-]*://", url, re.I):
        url = "https://" + url
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme not in ("http", "https") or "." not in (parsed.hostname or ""):
        raise HTTPException(400, "Link không hợp lệ. Hãy dán link bắt đầu bằng http:// hoặc https://")
    return url


def platform_name(info: dict) -> str:
    key = (info.get("extractor_key") or info.get("extractor") or "").lower().split(":")[0]
    if key in PLATFORM_NAMES:
        return PLATFORM_NAMES[key]
    for k, v in PLATFORM_NAMES.items():
        if key.startswith(k):
            return v
    host = urllib.parse.urlparse(info.get("webpage_url") or "").hostname or ""
    return host.removeprefix("www.") or "Trang web"


def probe_media(path: Path) -> dict:
    """Đọc độ phân giải và thời lượng của tệp bằng ffmpeg (không cần ffprobe)."""
    exe = FFMPEG_LOCATION or shutil.which("ffmpeg")
    if not exe:
        return {}
    try:
        out = subprocess.run([exe, "-hide_banner", "-i", str(path)], capture_output=True,
                             text=True, encoding="utf-8", errors="replace", timeout=20).stderr
    except Exception:
        return {}
    result: dict = {}
    m = re.search(r"Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)", out)
    if m:
        result["duration"] = int(m[1]) * 3600 + int(m[2]) * 60 + float(m[3])
    m = re.search(r"Stream #.*?Video:.*?[\s,](\d{2,5})x(\d{2,5})[\s,\[]", out)
    if m:
        result["width"], result["height"] = int(m[1]), int(m[2])
    return result


class QuietLogger:
    def debug(self, msg):
        pass

    def info(self, msg):
        pass

    def warning(self, msg):
        pass

    def error(self, msg):
        pass


# ---------------------------------------------------------------------------
# Lượt tải
# ---------------------------------------------------------------------------

class Cancelled(Exception):
    pass


class Job:
    def __init__(self, url: str, quality: str):
        self.id = uuid.uuid4().hex
        self.url = url
        self.quality = quality
        self.dir = DOWNLOAD_DIR / self.id
        self.state = "queued"  # queued | analyzing | downloading | processing | done | error | cancelled
        self.message = "Đang chờ đến lượt…"
        self.error: str | None = None
        self.error_detail: str | None = None
        self.info: dict | None = None
        self.progress = 0.0
        self.speed: float | None = None
        self.eta: int | None = None
        self.downloaded = 0
        self.total: int | None = None
        self.file: Path | None = None
        self.file_info: dict | None = None
        self.thumb_url: str | None = None
        self.thumb_path: Path | None = None
        self.thumb_type = "image/jpeg"
        self.http_headers: dict = {}
        self.cancelled = False
        self.created = time.time()
        self.lock = threading.Lock()
        self._parts: dict[str, list] = {}  # mã định dạng -> [đã tải, tổng]
        self._expected: dict[str, int | None] = {}

    # ----- tiến độ -----
    def on_progress(self, d: dict):
        if self.cancelled:
            raise Cancelled()
        fid = str((d.get("info_dict") or {}).get("format_id") or d.get("filename") or "0")
        status = d.get("status")
        if status == "downloading":
            done = d.get("downloaded_bytes") or 0
            total = d.get("total_bytes") or d.get("total_bytes_estimate")
            self._parts[fid] = [done, total]
            self.speed = d.get("speed")
            self.eta = d.get("eta")
        elif status == "finished":
            done = d.get("total_bytes") or d.get("downloaded_bytes") or self._parts.get(fid, [0])[0]
            self._parts[fid] = [done, done]
            self.speed = None
            self.eta = None
        self._recompute()

    def _recompute(self):
        keys = list(self._expected) or list(self._parts)
        for k in self._parts:
            if k not in keys:
                keys.append(k)
        downloaded = sum(p[0] or 0 for p in self._parts.values())
        totals = []
        for k in keys:
            part = self._parts.get(k)
            totals.append(part[1] if part and part[1] else self._expected.get(k))
        if totals and all(totals):
            total = sum(totals)
            pct = downloaded / total if total else 0
        else:  # chưa biết dung lượng: chia đều theo từng tệp
            fracs = [(p[0] / p[1]) if p[1] else 0 for p in self._parts.values()]
            pct = sum(fracs) / max(len(keys), 1)
            total = sum(t for t in totals if t) or None
        self.downloaded = downloaded
        self.total = total
        self.progress = max(self.progress, min(pct * 100, 99.0))

    def on_postprocess(self, d: dict):
        if self.cancelled:
            raise Cancelled()
        if d.get("status") != "started":
            return
        pp = d.get("postprocessor") or ""
        if pp in ("MoveFiles", "Metadata"):
            return
        self.state = "processing"
        self.speed = self.eta = None
        if pp == "Merger":
            self.message = "Đang ghép hình ảnh và âm thanh…"
        elif pp == "ExtractAudio":
            self.message = "Đang chuyển sang MP3…"
        else:
            self.message = "Đang hoàn thiện tệp…"

    # ----- tùy chọn yt-dlp -----
    def ydl_options(self) -> dict:
        opts = {
            "quiet": True,
            "no_warnings": True,
            "noprogress": True,
            "logger": QuietLogger(),
            "noplaylist": True,
            "playlist_items": "1",
            "outtmpl": "media.%(ext)s",
            "paths": {"home": str(self.dir), "temp": str(self.dir)},
            "progress_hooks": [self.on_progress],
            "postprocessor_hooks": [self.on_postprocess],
            "socket_timeout": 30,
            "retries": 5,
            "fragment_retries": 10,
            "concurrent_fragment_downloads": 4,
            "overwrites": True,
            "js_runtimes": JS_RUNTIMES,
        }
        if FFMPEG_LOCATION:
            opts["ffmpeg_location"] = FFMPEG_LOCATION
        if COOKIE_FILE.is_file():
            opts["cookiefile"] = str(COOKIE_FILE)

        target = QUALITIES.get(self.quality)
        if target == "audio":
            opts["format"] = "ba/b"
            if HAS_FFMPEG:
                opts["postprocessors"] = [
                    {"key": "FFmpegExtractAudio", "preferredcodec": "mp3", "preferredquality": "192"}
                ]
        else:
            # Ưu tiên độ phân giải cao nhất trong giới hạn, sau đó ưu tiên H.264 + AAC
            # để tệp MP4 xem được trên mọi trình duyệt, điện thoại, máy tính.
            res = f"res:{target}" if target else "res"
            opts["format_sort"] = [res, "fps", "vcodec:h264", "acodec:aac"]
            if HAS_FFMPEG:
                opts["format"] = "bv*+ba/b"
                opts["merge_output_format"] = "mp4/mkv"
                opts["postprocessors"] = [{"key": "FFmpegVideoRemuxer", "preferedformat": "flv>mp4/ts>mp4"}]
            else:
                opts["format"] = "b/bv*+ba"
        return opts

    # ----- chạy -----
    def run(self):
        with SLOTS:
            if self.cancelled:
                return
            try:
                self._run()
            except Cancelled:
                self.state = "cancelled"
                self.message = "Đã hủy"
                shutil.rmtree(self.dir, ignore_errors=True)
            except Exception as exc:  # noqa: BLE001
                if self.cancelled or isinstance(getattr(exc, "exc_info", [None, None])[1], Cancelled):
                    self.state = "cancelled"
                    self.message = "Đã hủy"
                    shutil.rmtree(self.dir, ignore_errors=True)
                    return
                raw = clean_error(getattr(exc, "msg", None) or str(exc))
                self.error = getattr(exc, "friendly", None) or friendly_error(raw)
                self.error_detail = raw
                self.state = "error"
                self.message = self.error
                print(f"[lỗi] {self.url}\n        {raw}")

    def _run(self):
        self.dir.mkdir(parents=True, exist_ok=True)
        self.state = "analyzing"
        self.message = "Đang phân tích link…"
        with yt_dlp.YoutubeDL(self.ydl_options()) as ydl:
            info = ydl.extract_info(self.url, download=False)
            if info.get("_type") in ("playlist", "multi_video"):
                entries = [e for e in (info.get("entries") or []) if e]
                if not entries:
                    raise RuntimeError("no video found")
                info = entries[0]
            if self.cancelled:
                raise Cancelled()
            if info.get("is_live"):
                err = RuntimeError("live")
                err.friendly = "Không hỗ trợ tải video đang phát trực tiếp. Hãy thử lại khi buổi phát đã kết thúc."
                raise err

            self._set_info(info)
            self.state = "downloading"
            self.message = "Đang tạo video…"
            result = ydl.process_ie_result(info, download=True)

        if self.cancelled:
            raise Cancelled()
        self._locate_file(result)
        self.progress = 100.0
        self.state = "done"
        self.message = "Hoàn tất"

    def _set_info(self, info: dict):
        formats = info.get("requested_formats") or [info]
        self._expected = {
            str(f.get("format_id") or "0"): (f.get("filesize") or f.get("filesize_approx")) for f in formats
        }
        thumbs = [t for t in (info.get("thumbnails") or []) if t.get("url")]
        self.thumb_url = info.get("thumbnail") or (thumbs[-1]["url"] if thumbs else None)
        self.http_headers = info.get("http_headers") or {}
        upload = info.get("upload_date") or ""
        self.info = {
            "title": info.get("title") or info.get("fulltitle") or "Video",
            "uploader": info.get("uploader") or info.get("channel") or info.get("creator") or info.get("uploader_id"),
            "duration": info.get("duration"),
            "platform": platform_name(info),
            "webpage_url": info.get("webpage_url") or self.url,
            "view_count": info.get("view_count"),
            "upload_date": f"{upload[6:8]}/{upload[4:6]}/{upload[:4]}" if len(upload) == 8 else None,
            "has_thumbnail": bool(self.thumb_url),
            "width": info.get("width"),
            "height": info.get("height"),
        }

    def _locate_file(self, result: dict | None):
        path = None
        for item in (result or {}).get("requested_downloads") or []:
            p = item.get("filepath")
            if p and Path(p).is_file():
                path = Path(p)
        if not path:
            skip = (".part", ".ytdl", ".jpg", ".jpeg", ".png", ".webp", ".json", ".temp")
            files = [p for p in self.dir.iterdir() if p.is_file() and not p.name.endswith(skip)
                     and p.name != "thumb"]
            if not files:
                raise RuntimeError("no media file produced")
            path = max(files, key=lambda p: p.stat().st_size)
        ext = path.suffix.lstrip(".").lower() or "mp4"
        if ext in ("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp", "html", "htm", "txt", "json"):
            err = RuntimeError(f"not a video file (.{ext})")
            err.friendly = "Link này là ảnh hoặc trang thường, không chứa video. Hãy dán link của một video."
            raise err
        size = path.stat().st_size
        is_audio = self.quality == "audio" or ext in ("mp3", "m4a", "opus", "ogg", "wav", "aac", "flac")
        d = result or {}
        height = d.get("height") or (self.info or {}).get("height")
        width = d.get("width") or (self.info or {}).get("width")
        missing_size = not is_audio and not (width and height)
        missing_duration = bool(self.info) and not self.info.get("duration")
        if missing_size or missing_duration:
            probed = probe_media(path)
            width = width or probed.get("width")
            height = height or probed.get("height")
            if self.info and not self.info.get("duration") and probed.get("duration"):
                self.info["duration"] = probed["duration"]
        self.file = path
        self.downloaded = size
        self.total = size
        self.file_info = {
            "ext": ext,
            "size": size,
            "is_audio": is_audio,
            "width": None if is_audio else width,
            "height": None if is_audio else height,
            "mime": mimetypes.guess_type(path.name)[0] or ("audio/mpeg" if is_audio else "video/mp4"),
            "filename": safe_filename((self.info or {}).get("title") or "video", ext),
        }

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "url": self.url,
            "quality": self.quality,
            "state": self.state,
            "message": self.message,
            "progress": round(self.progress, 1),
            "speed": self.speed,
            "eta": self.eta,
            "downloaded": self.downloaded,
            "total": self.total,
            "info": self.info,
            "file": self.file_info,
            "error": self.error,
            "error_detail": self.error_detail,
        }


JOBS: dict[str, Job] = {}
JOBS_LOCK = threading.Lock()
SLOTS = threading.BoundedSemaphore(MAX_PARALLEL)


def get_job(job_id: str) -> Job:
    job = JOBS.get(job_id)
    if not job:
        raise HTTPException(404, "Không tìm thấy lượt tải này (có thể đã hết hạn).")
    return job


def cleanup_loop():
    while True:
        time.sleep(300)
        now = time.time()
        with JOBS_LOCK:
            old = [j for j in JOBS.values() if now - j.created > JOB_TTL]
            for j in old:
                j.cancelled = True
                JOBS.pop(j.id, None)
        for j in old:
            shutil.rmtree(j.dir, ignore_errors=True)


# ---------------------------------------------------------------------------
# Giao diện lập trình (API)
# ---------------------------------------------------------------------------

app = FastAPI(title="Tải Video", docs_url="/api/docs", redoc_url=None)


class JobRequest(BaseModel):
    url: str
    quality: str = "1080"


@app.get("/api/health")
def health():
    return {
        "ok": True,
        "yt_dlp": yt_dlp.version.__version__,
        "ffmpeg": HAS_FFMPEG,
        "cookies": COOKIE_FILE.is_file(),
    }


@app.post("/api/jobs")
def create_job(req: JobRequest):
    url = normalize_url(req.url)
    quality = req.quality if req.quality in QUALITIES else "1080"
    job = Job(url, quality)
    with JOBS_LOCK:
        JOBS[job.id] = job
    threading.Thread(target=job.run, daemon=True, name=f"job-{job.id[:8]}").start()
    return job.to_dict()


@app.get("/api/jobs/{job_id}")
def job_status(job_id: str):
    return get_job(job_id).to_dict()


@app.delete("/api/jobs/{job_id}")
def cancel_job(job_id: str):
    job = get_job(job_id)
    job.cancelled = True
    if job.state in ("done", "error", "queued"):
        with JOBS_LOCK:
            JOBS.pop(job.id, None)
        shutil.rmtree(job.dir, ignore_errors=True)
    return {"ok": True}


def _ready_file(job_id: str) -> Job:
    job = get_job(job_id)
    if job.state != "done" or not job.file or not job.file.is_file():
        raise HTTPException(409, "Video chưa sẵn sàng.")
    return job


@app.get("/api/jobs/{job_id}/media")
def job_media(job_id: str):
    """Phát xem trước (hỗ trợ tua nhờ Range request)."""
    job = _ready_file(job_id)
    fi = job.file_info
    return FileResponse(job.file, media_type=fi["mime"],
                        headers={"Content-Disposition": content_disposition("inline", fi["filename"]),
                                 "Cache-Control": "private, max-age=3600"})


@app.get("/api/jobs/{job_id}/download")
def job_download(job_id: str):
    job = _ready_file(job_id)
    fi = job.file_info
    return FileResponse(job.file, media_type="application/octet-stream",
                        headers={"Content-Disposition": content_disposition("attachment", fi["filename"])})


@app.get("/api/jobs/{job_id}/thumbnail")
def job_thumbnail(job_id: str):
    """Tải ảnh bìa qua máy chủ, vì nhiều trang (Instagram, Facebook) chặn nhúng ảnh trực tiếp."""
    job = get_job(job_id)
    with job.lock:
        if not job.thumb_path:
            if not job.thumb_url:
                raise HTTPException(404, "Không có ảnh bìa.")
            headers = {"User-Agent": job.http_headers.get("User-Agent") or "Mozilla/5.0",
                       "Accept": "image/avif,image/webp,image/*,*/*;q=0.8"}
            try:
                req = urllib.request.Request(job.thumb_url, headers=headers)
                with urllib.request.urlopen(req, timeout=15) as resp:
                    data = resp.read(8 * 1024 * 1024)
                    ctype = resp.headers.get_content_type()
            except Exception:
                raise HTTPException(502, "Không tải được ảnh bìa.")
            if not ctype.startswith("image/"):
                ctype = "image/jpeg"
            job.dir.mkdir(parents=True, exist_ok=True)
            path = job.dir / "thumb"
            path.write_bytes(data)
            job.thumb_path = path
            job.thumb_type = ctype
    return FileResponse(job.thumb_path, media_type=job.thumb_type,
                        headers={"Cache-Control": "private, max-age=3600"})


app.mount("/", StaticFiles(directory=STATIC_DIR, html=True), name="static")


# ---------------------------------------------------------------------------
# Khởi động
# ---------------------------------------------------------------------------

def free_port(host: str, start: int) -> int:
    for port in range(start, start + 20):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            try:
                s.bind((host, port))
                return port
            except OSError:
                continue
    return start


def main():
    import uvicorn

    shutil.rmtree(DOWNLOAD_DIR, ignore_errors=True)
    DOWNLOAD_DIR.mkdir(parents=True, exist_ok=True)
    threading.Thread(target=cleanup_loop, daemon=True).start()

    port = free_port(HOST, PORT)
    shown_host = "127.0.0.1" if HOST in ("0.0.0.0", "::") else HOST
    url = f"http://{shown_host}:{port}"
    print("=" * 56)
    print("  TẢI VIDEO đang chạy tại:", url)
    print("  yt-dlp", yt_dlp.version.__version__, "| ffmpeg:", "có" if HAS_FFMPEG else "KHÔNG")
    print("  Đóng cửa sổ này (hoặc bấm Ctrl+C) để tắt ứng dụng.")
    print("=" * 56)
    if not os.environ.get("NO_BROWSER"):
        threading.Timer(1.2, lambda: webbrowser.open(url)).start()
    uvicorn.run(app, host=HOST, port=port, log_level="warning")


if __name__ == "__main__":
    main()
