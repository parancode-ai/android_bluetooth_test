@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat clean assembleDebug --console=plain
exit /b %ERRORLEVEL%
