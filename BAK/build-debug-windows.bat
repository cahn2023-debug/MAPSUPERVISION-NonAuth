@echo off
setlocal

rem Stable Android debug build on Windows.
rem This avoids the domain classes.jar file-lock by disabling parallel execution
rem and configuration cache for the APK build command itself.
call gradlew.bat --stop >nul 2>nul
call gradlew.bat assembleDebug --no-parallel --no-configuration-cache
exit /b %errorlevel%
