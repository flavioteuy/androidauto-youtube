package uy.autovideo.car

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView

/**
 * WebView que siempre se considera "visible".
 *
 * Cuando el auto empieza a andar, Android Auto tapa la pantalla con "No disponible mientras
 * conduces". Para el WebView eso es como pasar a segundo plano, y la página de YouTube pausa el
 * video. Así la página no se entera: la imagen queda oculta por Android Auto, pero el audio sigue.
 */
class BackgroundWebView : WebView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(View.VISIBLE)
    }
}
