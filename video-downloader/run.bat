@echo off
REM Chay ung dung Tai Video tren Windows: bam dup vao tep nay.
cd /d "%~dp0"

set "PY="
where py >nul 2>nul && set "PY=py -3"
if not defined PY where python >nul 2>nul && set "PY=python"
if not defined PY goto nopython

if not exist ".venv\Scripts\python.exe" (
  echo ^>^> Lan chay dau: dang chuan bi moi truong, vui long doi 1-2 phut...
  %PY% -m venv .venv || goto nopython
)

echo ^>^> Dang kiem tra va cap nhat bo tai...
".venv\Scripts\python.exe" -m pip install -q --disable-pip-version-check -r requirements.txt
".venv\Scripts\python.exe" -m pip install -q --disable-pip-version-check -U "yt-dlp[default]"

".venv\Scripts\python.exe" app.py
pause
exit /b

:nopython
echo.
echo Chua cai Python 3. Hay tai tai https://www.python.org/downloads/
echo Khi cai, nho tick o "Add python.exe to PATH" roi chay lai tep nay.
pause
