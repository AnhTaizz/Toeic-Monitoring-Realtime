@echo off
setlocal
title TOEIC Client (Internet)
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-release.ps1" -Role Client -ServerUrl https://toeic.anhtaizz.id.vn -ClientJar "%~dp0client\target\client-0.1.0-SNAPSHOT-all.jar" %*
set "TOEIC_EXIT=%ERRORLEVEL%"
if not "%TOEIC_EXIT%"=="0" pause
exit /b %TOEIC_EXIT%
