package uy.autovideo.car

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import org.json.JSONTokener
import uy.autovideo.media.NowPlaying
import uy.autovideo.R
import uy.autovideo.shared.CarBridge
import uy.autovideo.shared.ScreenDiag
import uy.autovideo.shared.SearchHistory
import uy.autovideo.shared.YouTubeLinks

/**
 * Pantalla que Android Auto muestra en el auto (app "para usar estacionado").
 * YouTube ocupa toda la pantalla; los botones de AutoVideo flotan arriba a la izquierda.
 * Los ajustes de diseño sobre la página están en assets/youtube_tweaks.js.
 */
class CarPlayerActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var pill: View
    private lateinit var webView: WebView
    private lateinit var searchField: AutoCompleteTextView
    private lateinit var lockView: TextView
    private lateinit var motionGuard: MotionGuard

    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var usingDocumentStartScript = false

    private val tweaksJs: String by lazy {
        try {
            assets.open(TWEAKS_ASSET).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer $TWEAKS_ASSET", e)
            ""
        }
    }

    private val bridgeListener: (String) -> Unit = { url -> load(url) }

    private var destroyed = false
    private var visibleNow = false

    /** Si queda en pausa sin mostrarse mucho tiempo, se apaga el servicio de reproducción. */
    private val idleStop = Runnable { PlaybackService.stop(this) }

    /** Botones del volante y del tablero → reproductor de YouTube. */
    private val mediaController = object : NowPlaying.Controller {
        override fun play() = mediaAction("play")
        override fun pause() = mediaAction("pause")
        override fun next() = mediaAction("nexttrack")
        override fun previous() = mediaAction("previoustrack")
        override fun seekTo(positionMs: Long) = mediaAction("seekto", positionMs / 1000.0)
    }

    /** Recibe de la página lo que está sonando (título, canal, tiempo). */
    inner class MediaJsBridge {
        @JavascriptInterface
        fun update(json: String) {
            main.post { onMediaUpdate(json) }
        }
    }

    // Diagnóstico: se captura la estructura de cada página de video a los 6 y a los 20 segundos.
    private val main = Handler(Looper.getMainLooper())
    private var diagUrl: String? = null
    private var diagSince = 0L
    private var diagStage = 0
    private val diagTick = object : Runnable {
        override fun run() {
            checkDiag()
            main.postDelayed(this, DIAG_TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyPhonePreview(intent)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_car_player)

        root = findViewById(R.id.car_root)
        pill = findViewById(R.id.car_pill)
        webView = findViewById(R.id.car_web)
        searchField = findViewById(R.id.car_search)
        lockView = findViewById(R.id.car_lock)

        configureWebView()
        setUpControls()

        motionGuard = MotionGuard(this) { moving -> showLock(moving) }

        val restored = savedInstanceState != null && webView.restoreState(savedInstanceState) != null
        val pending = CarBridge.consumePending()
        when {
            pending != null -> load(pending)
            !restored -> load(YouTubeLinks.HOME)
        }
        CarBridge.addListener(bridgeListener)
        NowPlaying.controller = mediaController
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyPhonePreview(intent)
        CarBridge.consumePending()?.let { load(it) }
    }

    override fun onStart() {
        super.onStart()
        visibleNow = true
        // Con la pantalla visible Android deja iniciar el servicio; después sigue aunque se oculte.
        PlaybackService.start(this)
        main.removeCallbacks(idleStop)
        motionGuard.start()
        main.removeCallbacks(diagTick)
        main.postDelayed(diagTick, DIAG_TICK_MS)
    }

    override fun onStop() {
        visibleNow = false
        main.removeCallbacks(diagTick)
        motionGuard.stop()
        showLock(false)
        super.onStop()
    }

    private fun mediaAction(action: String, arg: Double? = null) {
        main.post {
            if (destroyed) return@post
            val argJs = if (arg != null) ", $arg" else ""
            webView.evaluateJavascript("window.__avMediaAction && window.__avMediaAction('$action'$argJs)", null)
        }
    }

    private fun onMediaUpdate(json: String) {
        if (destroyed) return
        try {
            val o = JSONObject(json)
            if (!o.optBoolean("has")) {
                NowPlaying.stop()
                return
            }
            val playing = o.optBoolean("playing")
            if (playing) {
                main.removeCallbacks(idleStop)
                if (!PlaybackService.running) PlaybackService.start(this)
            } else if (!visibleNow) {
                main.removeCallbacks(idleStop)
                main.postDelayed(idleStop, IDLE_STOP_MS)
            }
            NowPlaying.update(
                context = this,
                newTitle = o.optString("t"),
                newArtist = o.optString("a"),
                newDurationMs = o.optLong("d"),
                positionMs = o.optLong("p"),
                playing = playing,
                buffering = o.optBoolean("buf"),
                rate = o.optDouble("r", 1.0).toFloat(),
                artworkUrl = o.optString("art"),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Datos de reproducción inválidos", e)
        }
    }

    /** Abierta desde la app del teléfono para probar: en horizontal, como la pantalla del auto. */
    private fun applyPhonePreview(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_PHONE_PREVIEW, false) == true) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun checkDiag() {
        val url = webView.url ?: return
        val now = SystemClock.elapsedRealtime()
        if (url != diagUrl) {
            diagUrl = url
            diagSince = now
            diagStage = 0
        }
        if (!url.contains("/watch")) return
        val age = now - diagSince
        val due = (diagStage == 0 && age >= DIAG_FIRST_MS) || (diagStage == 1 && age >= DIAG_SECOND_MS)
        if (!due) return
        diagStage++
        val kind = if (url.contains("list=")) ScreenDiag.MIX else ScreenDiag.VIDEO
        webView.evaluateJavascript("(window.__avDiag ? window.__avDiag() : 'sin __avDiag')") { result ->
            val text = try {
                JSONTokener(result).nextValue() as? String ?: result
            } catch (e: Exception) {
                result
            }
            if (!text.isNullOrBlank()) ScreenDiag.save(this, kind, text)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        destroyed = true
        main.removeCallbacks(idleStop)
        PlaybackService.stop(this)
        CarBridge.removeListener(bridgeListener)
        if (NowPlaying.controller === mediaController) {
            NowPlaying.controller = null
            NowPlaying.stop()
        }
        motionGuard.stop()
        exitFullscreen(notifyPage = true)
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            searchField.visibility == View.VISIBLE -> hideSearch()
            fullscreenView != null -> exitFullscreen(notifyPage = true)
            webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }

    // ------------------------------------------------------------------
    // Botones flotantes y buscador
    // ------------------------------------------------------------------

    private fun setUpControls() {
        findViewById<ImageButton>(R.id.car_back).setOnClickListener {
            when {
                searchField.visibility == View.VISIBLE -> hideSearch()
                fullscreenView != null -> exitFullscreen(notifyPage = true)
                webView.canGoBack() -> webView.goBack()
            }
        }
        findViewById<ImageButton>(R.id.car_home).setOnClickListener {
            hideSearch()
            load(YouTubeLinks.HOME)
        }
        findViewById<ImageButton>(R.id.car_search_go).setOnClickListener {
            if (searchField.visibility == View.VISIBLE) {
                val text = searchField.text?.toString().orEmpty()
                if (text.isBlank()) hideSearch() else submitSearch(text)
            } else {
                showSearch()
            }
        }

        searchField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                submitSearch(searchField.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }
        searchField.setOnItemClickListener { parent, _, position, _ ->
            submitSearch(parent.getItemAtPosition(position)?.toString().orEmpty())
        }
        searchField.setOnClickListener { searchField.showDropDown() }
        // Al tocar la página, el buscador se cierra solo si está vacío.
        searchField.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && searchField.text.isNullOrBlank()) hideSearch()
        }
    }

    private fun showSearch() {
        refreshHistory()
        searchField.setText("")
        searchField.visibility = View.VISIBLE
        searchField.requestFocus()
        getSystemService(InputMethodManager::class.java)
            ?.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT)
        // Búsquedas recientes, útiles si el auto no muestra teclado.
        searchField.post { if (searchField.visibility == View.VISIBLE) searchField.showDropDown() }
    }

    private fun hideSearch() {
        if (searchField.visibility != View.VISIBLE) return
        searchField.dismissDropDown()
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(searchField.windowToken, 0)
        searchField.clearFocus()
        searchField.visibility = View.GONE
    }

    private fun refreshHistory() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            SearchHistory.load(this)
        )
        searchField.setAdapter(adapter)
    }

    private fun submitSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        SearchHistory.add(this, q)
        searchField.setText("")
        hideSearch()
        load(YouTubeLinks.resolve(q) ?: YouTubeLinks.searchUrl(q))
    }

    // ------------------------------------------------------------------
    // WebView
    // ------------------------------------------------------------------

    fun load(url: String) {
        exitFullscreen(notifyPage = true)
        webView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
    private fun configureWebView() {
        webView.setBackgroundColor(Color.BLACK)
        // Que no se comporte como un navegador: sin selección de texto ni barras de desplazamiento.
        webView.isLongClickable = false
        webView.setOnLongClickListener { true }
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        // El motor de la página mantiene prioridad alta aunque Android Auto tape la pantalla.
        webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)

        webView.settings.apply {
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
            setAcceptThirdPartyCookies(webView, true)
        }
        // Lo que suena, para el tablero del auto (ver assets/youtube_tweaks.js).
        webView.addJavascriptInterface(MediaJsBridge(), "AutoVideoMedia")

        // Ajustes de diseño desde el primer instante de cada página (sin parpadeo).
        if (tweaksJs.isNotEmpty() && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(webView, tweaksJs, YOUTUBE_ORIGINS)
                usingDocumentStartScript = true
            } catch (e: Exception) {
                Log.w(TAG, "addDocumentStartJavaScript", e)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                // Solo páginas web: se bloquean los saltos a la app de YouTube (intent://, vnd.youtube:...).
                val scheme = request?.url?.scheme?.lowercase() ?: return false
                return scheme != "http" && scheme != "https"
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                injectTweaks(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                injectTweaks(view)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                showFullscreen(view, callback)
            }

            override fun onHideCustomView() {
                exitFullscreen(notifyPage = false)
            }
        }
    }

    /** Respaldo si el WebView no admite scripts al inicio de la página (el script no se duplica). */
    private fun injectTweaks(view: WebView?) {
        if (view == null || tweaksJs.isEmpty()) return
        view.evaluateJavascript(tweaksJs, null)
    }

    // ------------------------------------------------------------------
    // Pantalla completa (botón de pantalla completa del reproductor)
    // ------------------------------------------------------------------

    private fun showFullscreen(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        if (view == null || fullscreenView != null) {
            callback?.onCustomViewHidden()
            return
        }
        hideSearch()
        fullscreenView = view
        fullscreenCallback = callback
        view.setBackgroundColor(Color.BLACK)
        // Encima de todo, pero debajo del aviso de movimiento.
        root.addView(
            view,
            root.indexOfChild(lockView),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        pill.visibility = View.GONE
        setSystemBarsHidden(true)
    }

    private fun exitFullscreen(notifyPage: Boolean) {
        val view = fullscreenView ?: return
        root.removeView(view)
        fullscreenView = null
        val callback = fullscreenCallback
        fullscreenCallback = null
        pill.visibility = View.VISIBLE
        setSystemBarsHidden(false)
        if (notifyPage) callback?.onCustomViewHidden()
    }

    private fun setSystemBarsHidden(hidden: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val controller = window.insetsController ?: return
        if (hidden) {
            controller.hide(WindowInsets.Type.systemBars())
        } else {
            controller.show(WindowInsets.Type.systemBars())
        }
    }

    // ------------------------------------------------------------------
    // Protección extra: si el GPS indica movimiento, se tapa la imagen
    // ------------------------------------------------------------------

    private fun showLock(moving: Boolean) {
        lockView.visibility = if (moving) View.VISIBLE else View.GONE
        if (moving) lockView.bringToFront()
    }

    companion object {
        /** Extra para abrir esta pantalla en el teléfono (vista previa en horizontal). */
        const val EXTRA_PHONE_PREVIEW = "uy.autovideo.PHONE_PREVIEW"

        private const val TAG = "AutoVideo"
        private const val TWEAKS_ASSET = "youtube_tweaks.js"
        private const val DIAG_TICK_MS = 3_000L
        private const val DIAG_FIRST_MS = 6_000L
        private const val DIAG_SECOND_MS = 20_000L
        private const val IDLE_STOP_MS = 30 * 60_000L
        private val YOUTUBE_ORIGINS = setOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com")
    }
}
