@echo off
setlocal
title Diagnostico de AutoYouTube
color 0E

rem ------------------------------------------------------------------
rem  Junta la informacion necesaria para saber por que Android Auto
rem  no muestra AutoVideo. Guarda un archivo en el Escritorio.
rem ------------------------------------------------------------------

set "DIR=%LOCALAPPDATA%\AutoVideoInstalador"
set "ADB=%DIR%\platform-tools\adb.exe"
set "ADB_URL=https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
set "RAW=%DIR%\logcat-completo.txt"

set "DESK="
for /f "usebackq delims=" %%d in (`powershell -NoProfile -Command "[Environment]::GetFolderPath('Desktop')"`) do set "DESK=%%d"
if not defined DESK set "DESK=%USERPROFILE%\Desktop"
set "OUT=%DESK%\autovideo-diagnostico.txt"

if not exist "%DIR%" mkdir "%DIR%"

echo.
echo  ==================================================
echo    Diagnostico de AutoYouTube
echo  ==================================================
echo.

if not exist "%ADB%" (
  echo  Descargando las herramientas de Android...
  curl.exe -L --fail --progress-bar -o "%DIR%\platform-tools.zip" "%ADB_URL%"
  if errorlevel 1 goto :err_download
  tar.exe -xf "%DIR%\platform-tools.zip" -C "%DIR%"
)
if not exist "%ADB%" goto :err_download

echo  Buscando el telefono...
echo  (si aparece "Permitir depuracion USB", toca PERMITIR)
"%ADB%" start-server >nul 2>&1
set /a TRIES=0
:waitloop
"%ADB%" get-state 2>nul | findstr /c:"device" >nul
if not errorlevel 1 goto :found
set /a TRIES+=1
if %TRIES% GEQ 90 goto :err_device
timeout /t 2 /nobreak >nul
goto :waitloop

:found
echo  Telefono encontrado.
echo.
"%ADB%" shell am force-stop com.google.android.projection.gearhead >nul 2>&1
"%ADB%" logcat -c >nul 2>&1

echo  ==================================================
echo    AHORA, EN EL TELEFONO:
echo     1. Abre los ajustes de Android Auto.
echo     2. Entra a "Personalizar launcher".
echo     3. Quedate unos 10 segundos en esa pantalla.
echo.
echo    Despues vuelve aqui y presiona cualquier tecla.
echo  ==================================================
pause >nul

echo.
echo  Guardando el diagnostico...

> "%OUT%" echo ===== DIAGNOSTICO AUTOVIDEO =====
>>"%OUT%" echo.
>>"%OUT%" echo --- Telefono: modelo / Android / SDK ---
"%ADB%" shell getprop ro.product.model >>"%OUT%" 2>&1
"%ADB%" shell getprop ro.build.version.release >>"%OUT%" 2>&1
"%ADB%" shell getprop ro.build.version.sdk >>"%OUT%" 2>&1
>>"%OUT%" echo.
>>"%OUT%" echo --- Version de Android Auto ---
"%ADB%" shell dumpsys package com.google.android.projection.gearhead | findstr /i /l "versionName" >>"%OUT%" 2>&1
>>"%OUT%" echo.
>>"%OUT%" echo --- Origen de instalacion de AutoVideo ---
"%ADB%" shell pm list packages -i uy.autovideo >>"%OUT%" 2>&1
"%ADB%" shell dumpsys package uy.autovideo | findstr /i /l "installer initiating originating packageSource versionName targetSdk" >>"%OUT%" 2>&1
>>"%OUT%" echo.
>>"%OUT%" echo --- Pantallas para Android Auto (CAR_LAUNCHER) ---
"%ADB%" shell cmd package query-activities --components -a android.intent.action.MAIN -c android.intent.category.CAR_LAUNCHER >>"%OUT%" 2>&1
>>"%OUT%" echo.
>>"%OUT%" echo --- Categoria de AutoVideo ---
"%ADB%" shell dumpsys package uy.autovideo | findstr /i /l "category flags=" >>"%OUT%" 2>&1
>>"%OUT%" echo.
>>"%OUT%" echo --- Mensajes de Android Auto ---
"%ADB%" logcat -d -v time > "%RAW%" 2>&1
findstr /i /l "autovideo CAR. VALIDATOR parked car.app CarApp gearhead GH. projection allowlist unknown" "%RAW%" >>"%OUT%"

echo.
echo  ==================================================
echo    LISTO. En el Escritorio quedo el archivo:
echo      autovideo-diagnostico.txt
echo.
echo    Arrastralo al chat con Claude.
echo  ==================================================
echo.
explorer /select,"%OUT%"
pause
exit /b 0

:err_download
echo.
echo  ERROR: no se pudieron descargar las herramientas de Android.
echo  Revisa la conexion a internet y vuelve a ejecutar este archivo.
goto :end_error

:err_device
echo.
echo  ERROR: no se detecto el telefono. Revisa el cable, que
echo  "Depuracion USB" siga activada y que el Bloqueador automatico
echo  de Samsung este apagado. Luego ejecuta este archivo otra vez.
goto :end_error

:end_error
echo.
pause
exit /b 1
