@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM ---- CONFIG ----
set PKG=qu.astro.vrshellpatcher
set APK=app\build\outputs\apk\debug\app-debug.apk
set SHELLPKG=com.oculus.vrshell

REM Ensure we run from this script's folder (project root)
pushd "%~dp0"

:build
cls
echo ==============================================
echo   Android Build + Install Loop
echo   %date% %time%
echo ==============================================

echo [%time%] Cleaning Gradle project...
call gradlew.bat clean
if errorlevel 1 (
  echo [%time%] ERROR: Gradle clean failed. Check your Gradle setup.
  echo.
  pause
  goto build
)

echo [%time%] Building Debug APK...
call gradlew.bat assembleDebug
if errorlevel 1 (
  echo [%time%] ERROR: Build failed. Fix errors and retry.
  echo.
  pause
  goto build
)

if not exist "%APK%" (
  echo [%time%] ERROR: APK not found at "%APK%".
  echo        Verify your module path and build variant.
  echo.
  pause
  goto build
)


echo [%time%] Installing updated APK...
adb install -r "%APK%"
if errorlevel 1 (
  echo [%time%] ERROR: adb install failed.
  echo.
  pause
  goto build
)

echo [%time%] Restarting vrshell...
adb shell am force-stop %SHELLPKG% >nul 2>&1
adb shell am start %SHELLPKG%/.MainActivity

echo [%time%] Build and installation complete.
echo ==============================================
echo.
pause
goto build
