package uy.autovideo.car

import android.content.Intent
import androidx.car.app.AppManager
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import uy.autovideo.R
import uy.autovideo.shared.CarBridge

/** Una sesión = una conexión con Android Auto. */
class VideoSession : Session() {

    private var display: CarWebDisplay? = null
    private var monitor: DrivingMonitor? = null

    private val bridgeListener: (String) -> Unit = { url -> display?.load(url) }

    init {
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                CarBridge.removeListener(bridgeListener)
                monitor?.stop()
                monitor = null
                display?.release()
                display = null
            }
        })
    }

    override fun onCreateScreen(intent: Intent): Screen {
        val webDisplay = CarWebDisplay(carContext)
        display = webDisplay

        // Enlace enviado desde el teléfono antes de conectar el auto
        CarBridge.consumePending()?.let { webDisplay.load(it) }
        CarBridge.addListener(bridgeListener)

        val driving = DrivingMonitor(carContext) { state -> applyDriveState(state) }
        monitor = driving
        applyDriveState(driving.state)

        carContext.getCarService(AppManager::class.java).setSurfaceCallback(webDisplay)
        driving.start()

        return VideoScreen(carContext, webDisplay)
    }

    private fun applyDriveState(state: DrivingMonitor.State) {
        val ctx = carContext
        val message = when (state) {
            DrivingMonitor.State.PARKED -> null
            DrivingMonitor.State.MOVING -> ctx.getString(R.string.lock_moving)
            DrivingMonitor.State.CHECKING -> ctx.getString(R.string.lock_checking)
            DrivingMonitor.State.NO_SENSOR ->
                if (monitor?.hasLocationPermission() == true) {
                    ctx.getString(R.string.lock_no_signal)
                } else {
                    ctx.getString(R.string.lock_no_permission)
                }
        }
        display?.setLock(message)
    }
}
