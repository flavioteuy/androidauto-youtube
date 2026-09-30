package uy.autovideo.car

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
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
import uy.autovideo.R
import uy.autovideo.shared.CarBridge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        CarBridge.consumePending()?.let { load(it) }
    }

    override fun onStart() {
        super.onStart()
        motionGuard.start()
    }

    override fun onStop() {
        motionGuard.stop()
        showLock(false)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        CarBridge.removeListener(bridgeListener)
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

    private companion object {
        const val TAG = "AutoVideo"
        const val TWEAKS_ASSET = "youtube_tweaks.js"
        val YOUTUBE_ORIGINS = setOf("https://m.youtube.com", "https://www.youtube.com", "https://youtube.com")
    }
}
