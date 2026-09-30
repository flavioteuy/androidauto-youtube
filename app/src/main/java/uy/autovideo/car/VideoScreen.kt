package uy.autovideo.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import uy.autovideo.R
import uy.autovideo.shared.YouTubeLinks

/**
 * Pantalla principal en el auto: la superficie muestra YouTube y los botones
 * de Android Auto controlan la navegación y la reproducción.
 */
class VideoScreen(
    carContext: CarContext,
    private val display: CarWebDisplay,
) : Screen(carContext) {

    init {
        display.onPlayStateChanged = { invalidate() }
    }

    override fun onGetTemplate(): Template {
        // Arriba a la derecha (máx. 4 botones)
        val actionStrip = ActionStrip.Builder()
            .addAction(iconAction(R.drawable.ic_search) {
                screenManager.push(SearchScreen(carContext) { query ->
                    display.load(YouTubeLinks.searchUrl(query))
                })
            })
            .addAction(iconAction(R.drawable.ic_back) { display.goBack() })
            .addAction(
                iconAction(if (display.isPlaying) R.drawable.ic_pause else R.drawable.ic_play) {
                    display.togglePlay()
                }
            )
            .addAction(iconAction(R.drawable.ic_home) { display.loadHome() })
            .build()

        // Abajo a la derecha: desplazar la página (útil en autos sin pantalla táctil)
        val mapActionStrip = ActionStrip.Builder()
            .addAction(iconAction(R.drawable.ic_up) { display.scrollPage(-1) })
            .addAction(iconAction(R.drawable.ic_down) { display.scrollPage(1) })
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .setMapActionStrip(mapActionStrip)
            .build()
    }

    private fun iconAction(iconRes: Int, onClick: () -> Unit): Action =
        Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes)).build())
            .setOnClickListener { onClick() }
            .build()
}
