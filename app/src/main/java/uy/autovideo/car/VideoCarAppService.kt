package uy.autovideo.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/** Punto de entrada que Android Auto usa para abrir la app en el auto. */
class VideoCarAppService : CarAppService() {

    // App instalada fuera de Play Store: se acepta cualquier host de Android Auto.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = VideoSession()
}
