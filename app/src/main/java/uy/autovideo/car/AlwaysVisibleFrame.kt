package uy.autovideo.car

import android.content.Context
import android.view.View
import android.widget.FrameLayout

/**
 * Marco para la pantalla completa del reproductor.
 *
 * En pantalla completa, el WebView deja de mirar a [BackgroundWebView] y mira a la vista de
 * pantalla completa que entrega YouTube. Cuando Android Auto tapa la pantalla al manejar, esa
 * vista avisaba "ventana oculta" y el WebView pausaba el video. Este marco le dice a todo lo que
 * tiene adentro que la ventana sigue visible, así el audio continúa (la imagen igual queda tapada
 * por Android Auto).
 */
class AlwaysVisibleFrame(context: Context) : FrameLayout(context) {
    override fun dispatchWindowVisibilityChanged(visibility: Int) {
        super.dispatchWindowVisibilityChanged(View.VISIBLE)
    }
}
