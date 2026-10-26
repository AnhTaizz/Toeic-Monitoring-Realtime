@echo off
setlocal
title TOEIC Server + Cloudflare Tunnel
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-cloudflare.ps1" -Action Start %*
set "TOEIC_EXIT=%ERRORLEVEL%"
pause
exit /b %TOEIC_EXIT%
