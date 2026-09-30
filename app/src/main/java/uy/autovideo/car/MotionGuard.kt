package uy.autovideo.car

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Protección extra con el GPS del teléfono: avisa cuando el vehículo se mueve.
 *
 * La protección principal la hace Android Auto, que solo permite las apps
 * "para usar estacionado" con el auto detenido. Esto es una segunda capa:
 * si no hay permiso o no hay señal GPS, no bloquea nada.
 */
class MotionGuard(
    private val context: Context,
    private val onMovingChanged: (Boolean) -> Unit,
) {

    var isMoving = false
        private set

    private val main = Handler(Looper.getMainLooper())
    private var locationManager: LocationManager? = null
    private var listening = false
    private var slowSince = 0L

    /** Si el GPS deja de informar mucho tiempo (garaje, túnel), se libera el bloqueo. */
    private val staleTimeout = Runnable { setMoving(false) }

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handle(location)
        }

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (listening || !hasPermission()) return
        val lm = context.getSystemService(LocationManager::class.java) ?: return
        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                listener,
                Looper.getMainLooper()
            )
            locationManager = lm
            listening = true
        } catch (e: Exception) {
            Log.w(TAG, "GPS no disponible", e)
        }
    }

    fun stop() {
        main.removeCallbacks(staleTimeout)
        try {
            locationManager?.removeUpdates(listener)
        } catch (e: Exception) {
            Log.w(TAG, "removeUpdates", e)
        }
        locationManager = null
        listening = false
        slowSince = 0L
    }

    private fun handle(location: Location) {
        if (!location.hasSpeed()) return
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_M) return

        main.removeCallbacks(staleTimeout)
        main.postDelayed(staleTimeout, STALE_MS)

        val speed = location.speed
        val now = SystemClock.elapsedRealtime()
        when {
            speed > MOVING_MPS -> {
                slowSince = 0L
                setMoving(true)
            }
            speed < STOPPED_MPS -> {
                if (slowSince == 0L) slowSince = now
                if (now - slowSince >= STOP_CONFIRM_MS) setMoving(false)
            }
            else -> slowSince = 0L
        }
    }

    private fun setMoving(moving: Boolean) {
        if (moving != isMoving) {
            isMoving = moving
            onMovingChanged(moving)
        }
    }

    private companion object {
        const val TAG = "AutoVideo"
        const val MOVING_MPS = 2.5f        // ~9 km/h
        const val STOPPED_MPS = 1.0f       // ~3,6 km/h
        const val STOP_CONFIRM_MS = 3_000L
        const val STALE_MS = 90_000L
        const val MAX_ACCURACY_M = 50f
    }
}
