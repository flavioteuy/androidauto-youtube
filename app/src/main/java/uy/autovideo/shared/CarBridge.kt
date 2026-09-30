package uy.autovideo.shared

import android.os.Handler
import android.os.Looper

/**
 * Puente entre la app del teléfono y la pantalla del auto (mismo proceso).
 * Todo se usa desde el hilo principal.
 */
object CarBridge {

    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<(String) -> Unit>()
    private var pendingUrl: String? = null

    /** true si la pantalla del auto está abierta y recibió el enlace ahora mismo. */
    val isCarConnected: Boolean
        get() = listeners.isNotEmpty()

    /**
     * Envía una URL a la pantalla del auto. Si todavía no está abierta,
     * se guarda y se abre en cuanto se conecte Android Auto.
     */
    fun send(url: String): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { send(url) }
            return isCarConnected
        }
        return if (listeners.isEmpty()) {
            pendingUrl = url
            false
        } else {
            pendingUrl = null
            listeners.toList().forEach { it(url) }
            true
        }
    }

    fun consumePending(): String? {
        val url = pendingUrl
        pendingUrl = null
        return url
    }

    fun addListener(listener: (String) -> Unit) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }
}
