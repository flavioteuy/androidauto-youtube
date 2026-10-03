@echo off
setlocal
title Instalar AutoYouTube
color 0B

rem ------------------------------------------------------------------
rem  Instala AutoVideo en el telefono conectado por USB, marcado como
rem  instalado desde Play Store, para que Android Auto lo muestre.
rem ------------------------------------------------------------------

set "DIR=%LOCALAPPDATA%\AutoVideoInstalador"
set "ADB=%DIR%\platform-tools\adb.exe"
set "APK=%DIR%\AutoVideo.apk"
set "APK_URL=https://github.com/flavioteuy/androidauto-youtube/releases/latest/download/AutoVideo.apk"
set "ADB_URL=https://dl.google.com/android/repository/platform-tools-latest-windows.zip"

if not exist "%DIR%" mkdir "%DIR%"
cd /d "%DIR%"

echo.
echo  ==================================================
echo    Instalador de AutoYouTube para Android Auto
echo  ==================================================
echo.

rem ---- 1. Herramientas de Android (adb) ----
echo  [1/5] Descargando las herramientas de Android (adb)...
if exist "%ADB%" (
  echo        Ya estaban descargadas.
) else (
  curl.exe -L --fail --progress-bar -o "%DIR%\platform-tools.zip" "%ADB_URL%"
  if errorlevel 1 goto :err_download
  tar.exe -xf "%DIR%\platform-tools.zip" -C "%DIR%"
  if errorlevel 1 goto :err_download
)
if not exist "%ADB%" goto :err_download

rem ---- 2. APK de AutoVideo ----
echo  [2/5] Descargando la ultima version de AutoYouTube...
curl.exe -L --fail --progress-bar -o "%APK%" "%APK_URL%"
if errorlevel 1 goto :err_apk

rem ---- 3. Esperar el telefono ----
echo  [3/5] Buscando el telefono...
"%ADB%" start-server >nul 2>&1
set /a TRIES=0
:waitloop
"%ADB%" get-state 2>nul | findstr /c:"device" >nul
if not errorlevel 1 goto :found
set /a TRIES+=1
if %TRIES%==1 (
  echo.
  echo        MIRA LA PANTALLA DEL TELEFONO:
  echo        si aparece "Permitir depuracion USB?", marca
  echo        "Permitir siempre desde este equipo" y toca PERMITIR.
  echo.
)
if %TRIES% GEQ 90 goto :err_device
timeout /t 2 /nobreak >nul
goto :waitloop

:found
echo        Telefono encontrado.

rem ---- 4. Instalar (instalacion normal: Android Auto acepta apps "para estacionado"
rem         instaladas por fuera de Play Store si "Fuentes desconocidas" esta activado) ----
echo  [4/5] Instalando AutoYouTube...
rem Primero se actualiza encima (conserva la sesion de YouTube y los ajustes).
"%ADB%" install -r "%APK%" > "%DIR%\install.log" 2>&1
findstr /c:"Success" "%DIR%\install.log" >nul
if not errorlevel 1 goto :installed
findstr /c:"INSTALL_FAILED_UPDATE_INCOMPATIBLE" "%DIR%\install.log" >nul
if not errorlevel 1 goto :reinstall
type "%DIR%\install.log"
goto :err_install

:reinstall
rem La version instalada tiene otra firma (versiones viejas): hay que reinstalar desde cero.
echo        La version anterior tiene otra firma: se reinstala desde cero...
"%ADB%" uninstall uy.autovideo >nul 2>&1
"%ADB%" install "%APK%"
if errorlevel 1 goto :err_install

:installed
echo        Instalado.
echo        Dando permiso de ubicacion (para tapar la imagen si el auto se mueve)...
"%ADB%" shell pm grant uy.autovideo android.permission.ACCESS_FINE_LOCATION >nul 2>&1
"%ADB%" shell pm grant uy.autovideo android.permission.ACCESS_COARSE_LOCATION >nul 2>&1
"%ADB%" shell pm grant uy.autovideo android.permission.POST_NOTIFICATIONS >nul 2>&1

rem ---- 5. Reiniciar Android Auto para que vea la app ----
echo  [5/5] Reiniciando Android Auto...
"%ADB%" shell am force-stop com.google.android.projection.gearhead >nul 2>&1

echo.
echo  ==================================================
echo    LISTO. AutoYouTube quedo instalado.
echo  ==================================================
echo.
echo   Ahora:
echo    1. En el telefono, revisa en los ajustes para desarrolladores
echo       de Android Auto que "Fuentes desconocidas" siga activado.
echo    2. Ajustes de Android Auto - Personalizar launcher:
echo       AutoYouTube deberia aparecer. Marcalo.
echo    3. Con el auto estacionado, abrelo desde el menu de apps
echo       de Android Auto.
echo.
echo   Ya puedes desconectar el cable. Si quieres, vuelve a
echo   activar el Bloqueador automatico de Samsung.
echo.
pause
exit /b 0

:err_download
echo.
echo  ERROR: no se pudieron descargar las herramientas de Android.
echo  Revisa la conexion a internet y vuelve a ejecutar este archivo.
goto :end_error

:err_apk
echo.
echo  ERROR: no se pudo descargar AutoYouTube desde GitHub.
echo  Revisa la conexion a internet y vuelve a ejecutar este archivo.
goto :end_error

:err_device
echo.
echo  ERROR: no se detecto el telefono. Revisa que:
echo   - el cable sea de datos (algunos cables solo cargan),
echo   - "Depuracion USB" este activada en Opciones de desarrollador,
echo   - aceptaste el aviso "Permitir depuracion USB" en el telefono,
echo   - el "Bloqueador automatico" de Samsung este apagado.
echo  Desconecta y vuelve a conectar el cable, y ejecuta este archivo otra vez.
goto :end_error

:err_install
echo.
echo  ERROR: la instalacion fallo. Copia el mensaje de arriba
echo  (la linea que dice "Failure") y pegaselo a Claude.
goto :end_error

:err_installer
echo.
echo  AVISO: AutoYouTube se instalo, pero el telefono no acepto marcarlo
echo  como instalado desde Play Store. Avisale a Claude.
goto :end_error

:end_error
echo.
pause
exit /b 1
