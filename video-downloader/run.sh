#!/usr/bin/env bash
# Chạy ứng dụng Tải Video trên macOS / Linux:  bash run.sh
set -e
cd "$(dirname "$0")"

if command -v python3 >/dev/null 2>&1; then PY=python3
elif command -v python >/dev/null 2>&1; then PY=python
else
  echo "Chưa cài Python 3. Hãy tải tại https://www.python.org/downloads/ rồi chạy lại."
  exit 1
fi

if [ ! -x .venv/bin/python ]; then
  echo ">> Lần chạy đầu: đang chuẩn bị môi trường, vui lòng đợi 1-2 phút..."
  "$PY" -m venv .venv
fi

echo ">> Đang kiểm tra và cập nhật bộ tải..."
.venv/bin/python -m pip install -q --disable-pip-version-check -r requirements.txt
.venv/bin/python -m pip install -q --disable-pip-version-check -U "yt-dlp[default]"

.venv/bin/python app.py
