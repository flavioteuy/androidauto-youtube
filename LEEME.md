# AutoVideo — YouTube en la pantalla de Android Auto

App para Android que muestra YouTube (versión móvil) en la pantalla del auto a través de Android Auto.
Se maneja tocando la pantalla del auto como en el celular, con botones de Android Auto, o enviando
videos desde el teléfono.

**Seguridad:** la imagen solo se muestra con el vehículo detenido. Al avanzar se tapa con un aviso
y el audio sigue sonando; al detenerse vuelve el video. Usa la velocidad que informa el auto y,
como respaldo, el GPS del teléfono.

---

## 1. Obtener el APK (elige una opción)

### Opción A — GitHub, sin instalar nada (recomendada)

GitHub compila el APK gratis en sus servidores.

1. Descomprime `AutoVideo.zip`.
2. Entra a <https://github.com>, crea una cuenta gratis si no tienes, y toca **+ → New repository**.
   Nombre: `AutoVideo`, marca **Private** y toca **Create repository**.
3. En el repositorio vacío toca **uploading an existing file** y arrastra **todo el contenido**
   de la carpeta `AutoVideo` (las carpetas `app`, `gradle`, `.github` y los archivos sueltos).
   Toca **Commit changes**.
4. Ve a la pestaña **Actions**. Verás "Compilar APK" en marcha (tarda unos 5 minutos).
5. Cuando tenga la marca verde ✓, entra en esa ejecución, baja hasta **Artifacts** y descarga
   **AutoVideo-apk**. Dentro del ZIP está `app-debug.apk`.

> **Si en Actions no aparece nada** (en Mac, Finder oculta la carpeta `.github` y no se sube):
> en **Actions** toca **set up a workflow yourself**, borra lo que aparezca, pega el contenido de
> abajo y toca **Commit changes**.

```yaml
name: Compilar APK

on:
  push:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: android-actions/setup-android@v3
      - uses: gradle/actions/setup-gradle@v4
      - run: |
          chmod +x ./gradlew
          ./gradlew assembleDebug --no-daemon --stacktrace
      - uses: actions/upload-artifact@v4
        with:
          name: AutoVideo-apk
          path: app/build/outputs/apk/debug/*.apk
          if-no-files-found: error
```

### Opción B — Android Studio

Abre la carpeta `AutoVideo` en Android Studio, espera a que termine de sincronizar y usa
**Build → Build App Bundle(s) / APK(s) → Build APK(s)**. El APK queda en
`app/build/outputs/apk/debug/`.

---

## 2. Instalar en el teléfono

1. Pasa `app-debug.apk` al teléfono y ábrelo.
2. Si pide permiso para "instalar apps desconocidas", concédelo a esa app (navegador o archivos).
3. Si Play Protect avisa, toca **Más detalles → Instalar de todos modos**.
4. Abre **AutoVideo** en el teléfono y toca **Conceder permiso** (ubicación).

## 3. Activarlo en Android Auto (una sola vez)

Android Auto oculta las apps que no vienen de Play Store hasta que activas esto:

1. Abre los ajustes de Android Auto en el teléfono
   (Ajustes → Dispositivos conectados → Preferencias de conexión → Android Auto).
2. Baja hasta **Versión** y tócala unas 10 veces hasta que pregunte si quieres activar los ajustes
   para desarrolladores. Acepta.
3. Menú **⋮** → **Configuración para desarrolladores** → activa **Fuentes desconocidas**.
4. En los ajustes de Android Auto → **Personalizar launcher**, verifica que AutoVideo esté marcado.
5. Conecta el teléfono al auto y abre **AutoVideo** desde el menú de apps de Android Auto.

---

## 4. Uso

**En la pantalla del auto**

| Botón | Qué hace |
|---|---|
| 🔍 | Buscar en YouTube con el teclado del auto (y repetir búsquedas recientes) |
| ← | Volver a la página anterior / salir de pantalla completa |
| ▶ / ⏸ | Reproducir o pausar |
| ⌂ | Inicio de YouTube |
| ↑ ↓ | Desplazar la página (útil en autos sin pantalla táctil) |

- Toca videos, botones y la barra del reproductor como en el celular.
- Arrastra el dedo para desplazar la página.
- Para verlo en grande, toca el botón de **pantalla completa** del reproductor de YouTube.

**Desde el teléfono**

- Escribe una búsqueda o pega un enlace y toca **Enviar al auto**.
- O, en la app de YouTube: **Compartir → AutoVideo**. El video se abre directamente en el auto.
  Si Android Auto todavía no está abierto, se abre en cuanto entres a AutoVideo en el auto.

---

## 5. Problemas comunes

- **AutoVideo no aparece en Android Auto:** revisa los pasos 3 y 4 de la sección 3. Desconecta y
  vuelve a conectar el teléfono; a veces hace falta reiniciarlo.
- **Aparece entre las apps de navegación:** es normal. Android Auto solo deja dibujar libremente
  en pantalla a las apps de navegación, por eso AutoVideo se declara como una.
- **Aviso "Abre AutoVideo en el teléfono y concede el permiso de ubicación":** abre la app en el
  teléfono y toca **Conceder permiso**.
- **Aviso "No se puede confirmar que el vehículo esté detenido":** pasa si el auto no informa la
  velocidad y el teléfono no tiene señal GPS (por ejemplo, en un estacionamiento subterráneo).
- **Sin sonido en el auto:** revisa que el audio del teléfono salga por Android Auto (se oye igual
  que cualquier app de música) y el volumen multimedia.

**Ten en cuenta:** al no venir de Play Store, la app no se actualiza sola, y Google puede cambiar
Android Auto en el futuro de forma que deje de funcionar. Si pasa, el código está todo aquí para
ajustarlo.

---

## Cómo funciona (para curiosos)

- `car/VideoCarAppService.kt`: punto de entrada que abre Android Auto.
- `car/CarWebDisplay.kt`: crea una pantalla virtual que dibuja sobre la superficie del auto,
  muestra ahí un WebView con `m.youtube.com` y le reenvía los toques y desplazamientos.
- `car/DrivingMonitor.kt`: velocidad del auto + GPS → detenido / en movimiento.
- `car/VideoScreen.kt` y `car/SearchScreen.kt`: botones y buscador de Android Auto.
- `MainActivity.kt`: app del teléfono (enviar al auto, permisos, instrucciones).
