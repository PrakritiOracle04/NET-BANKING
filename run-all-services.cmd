@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-all-services.ps1"
if errorlevel 1 (
  echo.
  echo Service startup failed. Review the message above.
  pause
)
