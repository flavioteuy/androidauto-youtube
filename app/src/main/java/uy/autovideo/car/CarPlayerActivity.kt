package uy.autovideo.car

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
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
import uy.autovideo.R
import uy.autovideo.shared.CarBridge
import uy.autovideo.shared.SearchHistory
import uy.autovideo.shared.YouTubeLinks

/**
 * Pantalla que Android Auto muestra en el auto (app "para usar estacionado").
 * Es una pantalla normal de Android: los toques del auto llegan directo al WebView.
 */
class CarPlayerActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var toolbar: View
    private lateinit var webView: WebView
    private lateinit var searchField: AutoCompleteTextView
    private lateinit var lockView: TextView
    private lateinit var motionGuard: MotionGuard

    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    private val bridgeListener: (String) -> Unit = { url -> load(url) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_car_player)

        root = findViewById(R.id.car_root)
        toolbar = findViewById(R.id.car_toolbar)
        webView = findViewById(R.id.car_web)
        searchField = findViewById(R.id.car_search)
        lockView = findViewById(R.id.car_lock)

        configureWebView()
        setUpToolbar()

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
            fullscreenView != null -> exitFullscreen(notifyPage = true)
            webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }

    // ------------------------------------------------------------------
    // Barra superior
    // ------------------------------------------------------------------

    private fun setUpToolbar() {
        findViewById<ImageButton>(R.id.car_back).setOnClickListener {
            when {
                fullscreenView != null -> exitFullscreen(notifyPage = true)
                webView.canGoBack() -> webView.goBack()
            }
        }
        findViewById<ImageButton>(R.id.car_home).setOnClickListener { load(YouTubeLinks.HOME) }
        findViewById<ImageButton>(R.id.car_search_go).setOnClickListener {
            val text = searchField.text?.toString().orEmpty()
            if (text.isBlank()) {
                searchField.requestFocus()
                refreshHistory()
                searchField.showDropDown()
            } else {
                submitSearch(text)
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
            val query = parent.getItemAtPosition(position)?.toString().orEmpty()
            submitSearch(query)
        }
        // Al tocar el buscador se muestran las búsquedas recientes (útil si el auto no muestra teclado)
        searchField.setOnClickListener {
            refreshHistory()
            searchField.showDropDown()
        }
        searchField.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                refreshHistory()
                searchField.post { if (searchField.hasFocus()) searchField.showDropDown() }
            }
        }
        refreshHistory()
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
        searchField.dismissDropDown()
        searchField.setText("")
        searchField.clearFocus()
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(searchField.windowToken, 0)
        refreshHistory()
        load(YouTubeLinks.resolve(q) ?: YouTubeLinks.searchUrl(q))
    }

    // ------------------------------------------------------------------
    // WebView
    // ------------------------------------------------------------------

    fun load(url: String) {
        exitFullscreen(notifyPage = true)
        webView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.setBackgroundColor(Color.BLACK)
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

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                // Solo páginas web: se bloquean los saltos a la app de YouTube (intent://, vnd.youtube:...).
                val scheme = request?.url?.scheme?.lowercase() ?: return false
                return scheme != "http" && scheme != "https"
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

    // ------------------------------------------------------------------
    // Pantalla completa (botón de pantalla completa del reproductor)
    // ------------------------------------------------------------------

    private fun showFullscreen(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        if (view == null || fullscreenView != null) {
            callback?.onCustomViewHidden()
            return
        }
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
        toolbar.visibility = View.GONE
        setSystemBarsHidden(true)
    }

    private fun exitFullscreen(notifyPage: Boolean) {
        val view = fullscreenView ?: return
        root.removeView(view)
        fullscreenView = null
        val callback = fullscreenCallback
        fullscreenCallback = null
        toolbar.visibility = View.VISIBLE
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
}
