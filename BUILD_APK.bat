@echo off
setlocal
cd /d "%~dp0"
title Building Money app
echo.
echo  ===============================
echo   Building the Money app (APK)
echo  ===============================
echo.
echo  Normally you don't need this: GitHub builds Money.apk by itself
echo  (github.com/debangshu29/money-app/releases). This is only for a PC with Android Studio.
echo.
if not defined ANDROID_HOME if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if not defined ANDROID_HOME (
  echo Android SDK not found. Install Android Studio once from https://developer.android.com/studio
  pause
  exit /b 1
)
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
> local.properties echo sdk.dir=%ANDROID_HOME:\=\\%
call gradlew.bat assembleRelease
if errorlevel 1 (
  echo Build failed. Scroll up to see the error.
  pause
  exit /b 1
)
copy /y "app\build\outputs\apk\release\app-release.apk" "Money.apk" >nul
echo  Done! Money.apk is in this folder.
pause
