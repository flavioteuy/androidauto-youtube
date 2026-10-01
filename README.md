# AutoYouTube — YouTube en la pantalla de Android Auto (con el auto estacionado)

App para Android que muestra YouTube (versión móvil) en la pantalla del auto a través de Android Auto,
**solo con el vehículo estacionado**. Se maneja tocando la pantalla del auto como en el celular, o
enviando videos desde el teléfono.

## Cómo funciona

Android Auto solo muestra apps instaladas por fuera de Play Store si son de ciertos tipos. Las apps
de "plantillas" (como la versión 1 de AutoYouTube) **no** están incluidas, por eso Android Auto las
rechaza (`CAR.VALIDATOR: Package DENIED`). Las apps **"para usar estacionado"** sí lo están, siempre
que "Fuentes desconocidas" esté activado en los ajustes para desarrolladores de Android Auto.

Por eso la versión 2 es una app "para usar estacionado": una pantalla normal de Android que Android
Auto abre en el auto. Hoy Android Auto solo acepta esa categoría para juegos, así que la app se
declara como juego (`android:appCategory="game"`). No se puede publicar así en Play Store; es para
uso personal.

**Seguridad:** Android Auto solo permite estas apps con el auto detenido. Además, si el GPS del
teléfono detecta movimiento sostenido, la app tapa la imagen (el audio sigue). En la app del
teléfono se ajusta la velocidad mínima (5–30 km/h, por defecto 15) y cuántos segundos seguidos
tiene que durar el movimiento (2–30 s, por defecto 10), para que el GPS no la tape por error con
el auto estacionado.

---

## 1. Instalar (Windows)

1. En el teléfono: Ajustes → Acerca del teléfono → Información de software → toca
   **Número de compilación** 7 veces. Luego Ajustes → **Opciones de desarrollador** → activa
   **Depuración USB**.
2. En Samsung, apaga el **Bloqueador automático** (Ajustes → Seguridad y privacidad).
3. Conecta el teléfono por USB y ejecuta **`instalar-autovideo-windows.bat`** (doble clic).
   Descarga adb y la última versión de la app, la instala, le da el permiso de ubicación y
   reinicia Android Auto.

El APK también se puede bajar directo:
<https://github.com/flavioteuy/androidauto-youtube/releases/latest/download/AutoVideo.apk>

## 2. Activar en Android Auto (una sola vez)

1. Ajustes de Android Auto → baja hasta **Versión** y tócala unas 10 veces → acepta los ajustes
   para desarrolladores.
2. Menú **⋮** → **Configuración para desarrolladores** → activa **Fuentes desconocidas**.
3. Ajustes de Android Auto → **Personalizar launcher** → marca **AutoYouTube**.
4. Con el auto estacionado, abre AutoYouTube desde el menú de apps de Android Auto.

Requiere Android 15 o superior en el teléfono.

## 3. Uso

- **En el auto:** toca la pantalla como en el celular. Arriba a la izquierda flotan tres botones
  semitransparentes: atrás, inicio y buscar (al tocar la lupa aparece el buscador con las búsquedas
  recientes). Mientras ves un video, la lista de videos queda a la izquierda y el reproductor a la
  derecha, con el título y los botones debajo. Para verlo en grande, usa el botón de pantalla
  completa del reproductor.
- **Desde el teléfono:** abre AutoYouTube, escribe una búsqueda o pega un enlace y toca
  **Enviar al auto**. O en la app de YouTube: **Compartir → AutoYouTube**.

## 4. Si no aparece en Android Auto

Conecta el teléfono y ejecuta **`diagnostico-autovideo-windows.bat`**. Deja en el Escritorio un
archivo `autovideo-diagnostico.txt` con los mensajes de Android Auto que explican por qué no la
muestra.

---

## Compilación

GitHub compila el APK solo con cada cambio (pestaña **Actions**) y lo publica en **Releases**.
Todas las versiones se firman con la misma clave (`app/autovideo-debug.keystore`), así que una
versión nueva se instala encima de la anterior.

## Código

- `car/CarPlayerActivity.kt`: la pantalla del auto (WebView con `m.youtube.com`).
- `car/MotionGuard.kt`: protección extra con el GPS del teléfono.
- `MainActivity.kt`: app del teléfono (enviar al auto, permisos, instrucciones).
- `shared/`: enlaces de YouTube, historial de búsquedas y el puente teléfono ↔ auto.
