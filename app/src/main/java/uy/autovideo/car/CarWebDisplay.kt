package uy.autovideo.car

import android.annotation.SuppressLint
import android.app.Presentation
import android.graphics.Color
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import uy.autovideo.shared.YouTubeLinks

/**
 * Dibuja un WebView con YouTube móvil sobre la superficie que Android Auto
 * entrega a las apps de navegación.
 *
 * Truco: se crea una pantalla virtual (VirtualDisplay) que escribe en la
 * superficie del auto, y se muestra en ella una "Presentation" con el WebView.
 * Los toques y desplazamientos del auto se reenvían al WebView.
 */
class CarWebDisplay(private val carContext: CarContext) : SurfaceCallback {

    /** Se llama cuando cambia el estado de reproducción (para actualizar el botón). */
    var onPlayStateChanged: (() -> Unit)? = null

    var isPlaying = false
        private set

    val isLocked: Boolean
        get() = lockMessage != null

    private val main = Handler(Looper.getMainLooper())

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var root: FrameLayout? = null
    private var webView: WebView? = null
    private var lockView: TextView? = null
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var density = 1f
    private var stableArea: Rect? = null
    private var pendingUrl: String? = null
    private var lockMessage: String? = null
    private var released = false

    // ------------------------------------------------------------------
    // SurfaceCallback (lo llama Android Auto en el hilo principal)
    // ------------------------------------------------------------------

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        if (released) return
        val surface = surfaceContainer.surface ?: return
        val width = surfaceContainer.width
        val height = surfaceContainer.height
        val dpi = if (surfaceContainer.dpi > 0) surfaceContainer.dpi else 160
        if (width <= 0 || height <= 0) return

        surfaceWidth = width
        surfaceHeight = height
        density = dpi / 160f

        val existing = virtualDisplay
        if (existing == null) {
            createDisplay(width, height, dpi, surface)
        } else {
            // Se reutiliza la misma pantalla virtual: el video no se reinicia.
            existing.resize(width, height, dpi)
            existing.surface = surface
        }
        applyStableArea()
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        virtualDisplay?.surface = null
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        this.stableArea = Rect(stableArea)
        applyStableArea()
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        // Se usa el área estable para que el contenido no "salte" cuando
        // los botones del auto aparecen y desaparecen.
    }

    override fun onClick(x: Float, y: Float) {
        if (isLocked) return
        tap(x, y)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        if (isLocked || fullscreenView != null) return
        val dx = distanceX / density
        val dy = distanceY / density
        webView?.evaluateJavascript("window.scrollBy($dx,$dy);", null)
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        if (isLocked || fullscreenView != null) return
        val dx = -velocityX * 0.35f / density
        val dy = -velocityY * 0.35f / density
        webView?.evaluateJavascript(
            "window.scrollBy({left:$dx,top:$dy,behavior:'smooth'});", null
        )
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        // Sin zoom: la versión móvil de YouTube ya se adapta a la pantalla.
    }

    // ------------------------------------------------------------------
    // Acciones que usa la pantalla del auto
    // ------------------------------------------------------------------

    fun load(url: String) {
        val wv = webView
        if (wv == null) {
            pendingUrl = url
            return
        }
        exitFullscreen(notifyPage = true)
        wv.loadUrl(url)
    }

    fun loadHome() = load(YouTubeLinks.HOME)

    fun goBack() {
        if (fullscreenView != null) {
            exitFullscreen(notifyPage = true)
            return
        }
        val wv = webView ?: return
        if (wv.canGoBack()) wv.goBack()
    }

    fun togglePlay() {
        val wv = webView ?: return
        wv.evaluateJavascript(TOGGLE_PLAY_JS) { result ->
            when {
                result == null -> Unit
                result.contains("play") -> setPlaying(true)
                result.contains("pause") -> setPlaying(false)
            }
        }
    }

    /** direction: -1 sube, 1 baja. */
    fun scrollPage(direction: Int) {
        if (isLocked || fullscreenView != null) return
        webView?.evaluateJavascript(
            "window.scrollBy({top:($direction)*window.innerHeight*0.7,behavior:'smooth'});", null
        )
    }

    /**
     * Con un mensaje: tapa la imagen y bloquea los toques (el audio sigue).
     * Con null: vuelve a mostrar el video.
     */
    fun setLock(message: String?) {
        lockMessage = message
        updateLockView()
    }

    fun release() {
        if (released) return
        released = true
        exitFullscreen(notifyPage = true)
        webView?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.stopLoading()
            wv.destroy()
        }
        webView = null
        try {
            presentation?.dismiss()
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo cerrar la presentación", e)
        }
        presentation = null
        root = null
        lockView = null
        virtualDisplay?.release()
        virtualDisplay = null
    }

    // ------------------------------------------------------------------
    // Internos
    // ------------------------------------------------------------------

    private fun createDisplay(width: Int, height: Int, dpi: Int, surface: Surface) {
        val displayManager = carContext.getSystemService(DisplayManager::class.java) ?: return
        val vd = displayManager.createVirtualDisplay(
            "AutoVideo",
            width,
            height,
            dpi,
            surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        ) ?: return
        virtualDisplay = vd

        val pres = Presentation(carContext, vd.display)
        val ctx = pres.context

        val rootLayout = FrameLayout(ctx).apply { setBackgroundColor(Color.BLACK) }

        val wv = WebView(ctx)
        configureWebView(wv)
        rootLayout.addView(
            wv,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val lock = TextView(ctx).apply {
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            val pad = (32 * density).toInt()
            setPadding(pad, pad, pad, pad)
            isClickable = true
            visibility = View.GONE
        }
        rootLayout.addView(
            lock,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        pres.setContentView(rootLayout)
        pres.window?.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        try {
            pres.show()
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo mostrar en la pantalla del auto", e)
            vd.release()
            virtualDisplay = null
            return
        }

        presentation = pres
        root = rootLayout
        webView = wv
        lockView = lock
        updateLockView()

        val url = pendingUrl ?: YouTubeLinks.HOME
        pendingUrl = null
        wv.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun configureWebView(wv: WebView) {
        wv.setBackgroundColor(Color.BLACK)
        wv.isFocusable = true
        wv.isFocusableInTouchMode = true
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportMultipleWindows(false)
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }
        wv.addJavascriptInterface(JsBridge(), "AutoVideo")

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                // Solo páginas web: se bloquean los saltos a la app de YouTube (intent://, vnd.youtube:...).
                val scheme = request?.url?.scheme?.lowercase() ?: return false
                return scheme != "http" && scheme != "https"
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(HOOK_PLAY_STATE_JS, null)
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                showFullscreen(view, callback)
            }

            override fun onHideCustomView() {
                exitFullscreen(notifyPage = false)
            }
        }
    }

    private fun showFullscreen(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        val r = root
        if (r == null || view == null) {
            callback?.onCustomViewHidden()
            return
        }
        if (fullscreenView != null) {
            callback?.onCustomViewHidden()
            return
        }
        fullscreenView = view
        fullscreenCallback = callback
        view.setBackgroundColor(Color.BLACK)
        // Debajo del aviso de bloqueo, encima del WebView, ocupando toda la superficie.
        val index = lockView?.let { r.indexOfChild(it) }?.takeIf { it >= 0 } ?: r.childCount
        r.addView(
            view,
            index,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun exitFullscreen(notifyPage: Boolean) {
        val view = fullscreenView ?: return
        root?.removeView(view)
        fullscreenView = null
        val callback = fullscreenCallback
        fullscreenCallback = null
        if (notifyPage) callback?.onCustomViewHidden()
    }

    private fun applyStableArea() {
        val wv = webView ?: return
        val lp = wv.layoutParams as? FrameLayout.LayoutParams ?: return
        val area = stableArea
        if (area == null || area.isEmpty || surfaceWidth == 0 || surfaceHeight == 0) {
            lp.setMargins(0, 0, 0, 0)
        } else {
            lp.setMargins(
                area.left.coerceIn(0, surfaceWidth),
                area.top.coerceIn(0, surfaceHeight),
                (surfaceWidth - area.right).coerceIn(0, surfaceWidth),
                (surfaceHeight - area.bottom).coerceIn(0, surfaceHeight)
            )
        }
        wv.layoutParams = lp
    }

    private fun updateLockView() {
        val v = lockView ?: return
        val message = lockMessage
        if (message == null) {
            v.visibility = View.GONE
        } else {
            v.text = message
            v.visibility = View.VISIBLE
            v.bringToFront()
        }
    }

    /** Simula un toque de dedo en (x, y) de la superficie del auto. */
    private fun tap(x: Float, y: Float) {
        val target = root ?: return
        val downTime = SystemClock.uptimeMillis()
        dispatch(target, downTime, downTime, MotionEvent.ACTION_DOWN, x, y)
        main.postDelayed({
            if (root === target) {
                dispatch(target, downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y)
            }
        }, 60)
    }

    private fun dispatch(target: View, downTime: Long, eventTime: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        target.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun setPlaying(playing: Boolean) {
        if (isPlaying != playing) {
            isPlaying = playing
            onPlayStateChanged?.invoke()
        }
    }

    /** Recibe desde la página el estado del video (play/pausa). */
    inner class JsBridge {
        @JavascriptInterface
        fun onPlayState(playing: Boolean) {
            main.post { setPlaying(playing) }
        }
    }

    private companion object {
        const val TAG = "AutoVideo"

        const val MAIN_VIDEO =
            "(document.querySelector('.html5-main-video')||document.querySelector('video'))"

        const val HOOK_PLAY_STATE_JS = """
            (function(){
              if (window.__autoVideoHooked) return;
              window.__autoVideoHooked = true;
              function report(){
                var v = $MAIN_VIDEO;
                try { AutoVideo.onPlayState(!!(v && !v.paused && !v.ended)); } catch(e) {}
              }
              ['play','playing','pause','ended','emptied'].forEach(function(ev){
                document.addEventListener(ev, report, true);
              });
              report();
            })();
        """

        const val TOGGLE_PLAY_JS = """
            (function(){
              var v = $MAIN_VIDEO;
              if (!v) return 'none';
              if (v.paused || v.ended) {
                var p = v.play();
                if (p && p.catch) p.catch(function(){});
                return 'play';
              }
              v.pause();
              return 'pause';
            })();
        """
    }
}
