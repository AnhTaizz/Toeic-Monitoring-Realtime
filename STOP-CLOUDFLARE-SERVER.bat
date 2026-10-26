@echo off
setlocal
title TOEIC stop Server + Tunnel
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-cloudflare.ps1" -Action Stop %*
set "TOEIC_EXIT=%ERRORLEVEL%"
pause
exit /b %TOEIC_EXIT%
