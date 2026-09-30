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
import uy.autovideo.shared.MotionSettings

/**
 * Protección extra con el GPS del teléfono: tapa la imagen cuando el vehículo se mueve.
 *
 * La protección principal la hace Android Auto, que solo permite las apps
 * "para usar estacionado" con el auto detenido. Esto es una segunda capa.
 *
 * Para no activarse por error con el auto estacionado (el GPS a veces "salta" bajo
 * techo o entre edificios), solo cuenta el movimiento sostenido: más de la velocidad
 * configurada durante los segundos configurados, con lecturas continuas y precisas.
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

    /** Desde cuándo va rápido sin cortes (0 = no va rápido). */
    private var fastSince = 0L
    /** Desde cuándo va lento o detenido (0 = no). */
    private var slowSince = 0L
    private var lastFixAt = 0L

    /** Si el GPS deja de informar mucho tiempo (garaje, túnel), se libera el bloqueo. */
    private val staleTimeout = Runnable {
        fastSince = 0L
        setMoving(false)
    }

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
        fastSince = 0L
        slowSince = 0L
        lastFixAt = 0L
    }

    private fun handle(location: Location) {
        if (!location.hasSpeed()) return
        // Lecturas poco confiables: se descartan (son las que causan activaciones falsas).
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_M) return
        if (location.hasSpeedAccuracy() && location.speedAccuracyMetersPerSecond > MAX_SPEED_ERROR_MPS) return

        val now = SystemClock.elapsedRealtime()
        // Si hubo un corte largo entre lecturas, el movimiento "sostenido" vuelve a empezar.
        if (lastFixAt != 0L && now - lastFixAt > MAX_GAP_MS) fastSince = 0L
        lastFixAt = now

        main.removeCallbacks(staleTimeout)
        main.postDelayed(staleTimeout, STALE_MS)

        val speedKmh = location.speed * 3.6f
        val thresholdKmh = MotionSettings.speedKmh(context).toFloat()
        val sustainMs = MotionSettings.sustainedSeconds(context) * 1000L
        val stoppedBelowKmh = maxOf(3f, thresholdKmh / 3f)

        when {
            speedKmh >= thresholdKmh -> {
                slowSince = 0L
                if (fastSince == 0L) fastSince = now
                if (now - fastSince >= sustainMs) setMoving(true)
            }
            speedKmh < stoppedBelowKmh -> {
                fastSince = 0L
                if (slowSince == 0L) slowSince = now
                if (now - slowSince >= STOP_CONFIRM_MS) setMoving(false)
            }
            else -> {
                // Velocidad intermedia: no cuenta como movimiento sostenido ni como detenido.
                fastSince = 0L
                slowSince = 0L
            }
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
        const val STOP_CONFIRM_MS = 3_000L
        const val MAX_GAP_MS = 5_000L
        const val STALE_MS = 90_000L
        const val MAX_ACCURACY_M = 30f
        const val MAX_SPEED_ERROR_MPS = 2f
    }
}
