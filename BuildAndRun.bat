@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

set APK=app\build\outputs\apk\debug\app-debug.apk
set SDK_DIR=%LOCALAPPDATA%\Android\Sdk
if defined ANDROID_HOME set SDK_DIR=%ANDROID_HOME%
if defined ANDROID_SDK_ROOT set SDK_DIR=%ANDROID_SDK_ROOT%
set ADB=%SDK_DIR%\platform-tools\adb.exe
set EMULATOR=%SDK_DIR%\emulator\emulator.exe
set AVD_NAME=BluetoothMusicPlayer_Pixel8_API34

if not exist "%ADB%" (
    echo adb not found: "%ADB%"
    exit /b 1
)
if not exist "%EMULATOR%" (
    echo emulator not found: "%EMULATOR%"
    exit /b 1
)

call :find_physical_device
if not defined DEVICE_SERIAL (
    call :find_emulator_device
)
if not defined DEVICE_SERIAL (
    echo No connected device found. Starting visible emulator: %AVD_NAME%
    call :ensure_avd_exists
    if errorlevel 1 exit /b %ERRORLEVEL%
    start "Android Emulator - %AVD_NAME%" "%EMULATOR%" -avd "%AVD_NAME%" -no-snapshot-load
    call :wait_for_device
    if errorlevel 1 exit /b %ERRORLEVEL%
) else (
    echo Using connected device: %DEVICE_SERIAL%
)

echo %DEVICE_SERIAL% | findstr /b /c:"emulator-" >nul
if not errorlevel 1 (
    call :wait_for_boot
    if errorlevel 1 exit /b %ERRORLEVEL%
)

call gradlew.bat assembleDebug --console=plain
if errorlevel 1 exit /b %ERRORLEVEL%

"%ADB%" -s "%DEVICE_SERIAL%" install -r "%APK%"
if errorlevel 1 exit /b %ERRORLEVEL%
"%ADB%" -s "%DEVICE_SERIAL%" shell am start -S -n com.paran.music/.MainActivity
exit /b %ERRORLEVEL%

:find_physical_device
set DEVICE_SERIAL=
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
    if "%%B"=="device" (
        echo %%A | findstr /b /c:"emulator-" >nul
        if errorlevel 1 (
            set DEVICE_SERIAL=%%A
            goto :eof
        )
    )
)
goto :eof

:find_emulator_device
set DEVICE_SERIAL=
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
    if "%%B"=="device" (
        echo %%A | findstr /b /c:"emulator-" >nul
        if not errorlevel 1 (
            set DEVICE_SERIAL=%%A
            goto :eof
        )
    )
)
goto :eof

:ensure_avd_exists
for /f "usebackq delims=" %%A in (`"%EMULATOR%" -list-avds`) do (
    if "%%A"=="%AVD_NAME%" exit /b 0
)
echo AVD not found: %AVD_NAME%
echo Create it first in Android Studio, or run the setup once from this project notes.
exit /b 1

:wait_for_device
set DEVICE_SERIAL=
echo Waiting for emulator to connect...
for /l %%I in (1,1,60) do (
    call :find_emulator_device
    if defined DEVICE_SERIAL goto wait_for_boot
    ping -n 3 127.0.0.1 >nul
)
echo Timed out waiting for emulator device.
exit /b 1

:wait_for_boot
echo Waiting for Android boot on %DEVICE_SERIAL%...
"%ADB%" -s "%DEVICE_SERIAL%" wait-for-device
for /l %%I in (1,1,90) do (
    set BOOT_DONE=
    "%ADB%" -s "%DEVICE_SERIAL%" shell getprop sys.boot_completed > "%TEMP%\bmp_boot_completed.txt" 2>nul
    set /p BOOT_DONE=<"%TEMP%\bmp_boot_completed.txt"
    if "!BOOT_DONE!"=="1" exit /b 0
    ping -n 3 127.0.0.1 >nul
)
echo Timed out waiting for Android boot.
exit /b 1
