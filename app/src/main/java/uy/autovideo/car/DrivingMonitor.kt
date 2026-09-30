package uy.autovideo.car

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.CarInfo
import androidx.car.app.hardware.info.Speed
import androidx.car.app.versioning.CarAppApiLevels
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * Decide si el vehículo está detenido, usando la velocidad que informa el auto
 * (Android Auto) y, como respaldo, el GPS del teléfono.
 *
 * Mientras el vehículo se mueve, la imagen se oculta (el audio sigue).
 */
class DrivingMonitor(
    private val carContext: CarContext,
    private val onStateChanged: (State) -> Unit,
) {

    enum class State {
        /** Recién conectado, esperando el primer dato de velocidad. */
        CHECKING,
        PARKED,
        MOVING,
        /** No hay forma de saber la velocidad (sin datos del auto ni GPS). */
        NO_SENSOR,
    }

    var state = State.CHECKING
        private set

    private val main = Handler(Looper.getMainLooper())
    private var running = false
    private var startTime = 0L

    // Velocidad del auto (m/s)
    private var carInfo: CarInfo? = null
    private var carSpeed: Float? = null

    // Velocidad GPS del teléfono (m/s)
    private var locationManager: LocationManager? = null
    private var gpsListening = false
    private var gpsSpeed: Float? = null
    private var gpsTime = 0L

    private val speedListener = OnCarDataAvailableListener<Speed> { speed ->
        val raw = speed.rawSpeedMetersPerSecond
        val shown = speed.displaySpeedMetersPerSecond
        val value = when {
            raw.status == CarValue.STATUS_SUCCESS && raw.value != null -> raw.value
            shown.status == CarValue.STATUS_SUCCESS && shown.value != null -> shown.value
            else -> null
        }
        if (value != null) {
            carSpeed = abs(value)
            evaluate()
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!location.hasSpeed()) return
            if (location.hasAccuracy() && location.accuracy > 50f) return
            gpsSpeed = location.speed
            gpsTime = SystemClock.elapsedRealtime()
            evaluate()
        }

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (!gpsListening) startGps() // por si el permiso se concedió después
            evaluate()
            main.postDelayed(this, TICK_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        startTime = SystemClock.elapsedRealtime()
        startCarSpeed()
        startGps()
        main.post(tick)
    }

    fun stop() {
        running = false
        main.removeCallbacks(tick)
        try {
            carInfo?.removeSpeedListener(speedListener)
        } catch (e: Exception) {
            Log.w(TAG, "removeSpeedListener", e)
        }
        carInfo = null
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            Log.w(TAG, "removeUpdates", e)
        }
        locationManager = null
        gpsListening = false
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(carContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun startCarSpeed() {
        if (carContext.carAppApiLevel < CarAppApiLevels.LEVEL_3) return
        try {
            val hardware = carContext.getCarService(CarHardwareManager::class.java)
            val info = hardware.carInfo
            info.addSpeedListener(ContextCompat.getMainExecutor(carContext), speedListener)
            carInfo = info
        } catch (e: Exception) {
            // Sin permiso o no disponible en este auto: se usa solo el GPS.
            Log.w(TAG, "Velocidad del auto no disponible", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (gpsListening || !hasLocationPermission()) return
        val lm = carContext.getSystemService(LocationManager::class.java) ?: return
        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                locationListener,
                Looper.getMainLooper()
            )
            locationManager = lm
            gpsListening = true
        } catch (e: Exception) {
            Log.w(TAG, "GPS no disponible", e)
        }
    }

    private fun evaluate() {
        if (!running) return
        val now = SystemClock.elapsedRealtime()

        // -1 = detenido, 0 = dudoso, 1 = en movimiento
        val readings = ArrayList<Int>(2)
        carSpeed?.let { readings.add(classify(it, CAR_MOVING_MPS, CAR_STOPPED_MPS)) }
        val freshGps = gpsSpeed?.takeIf { now - gpsTime < GPS_STALE_MS }
        freshGps?.let { readings.add(classify(it, GPS_MOVING_MPS, GPS_STOPPED_MPS)) }

        val newState = when {
            readings.any { it > 0 } -> State.MOVING
            readings.isNotEmpty() && readings.all { it < 0 } -> State.PARKED
            // Velocidad intermedia (maniobrando): se mantiene lo último, y si no hay nada, se bloquea.
            readings.isNotEmpty() -> if (state == State.PARKED) State.PARKED else State.MOVING
            // Sin datos frescos: se mantiene el último estado conocido.
            state == State.PARKED || state == State.MOVING -> state
            now - startTime < CHECK_WINDOW_MS -> State.CHECKING
            else -> State.NO_SENSOR
        }

        if (newState != state) {
            state = newState
            onStateChanged(newState)
        }
    }

    private fun classify(speed: Float, movingAbove: Float, stoppedBelow: Float): Int = when {
        speed > movingAbove -> 1
        speed < stoppedBelow -> -1
        else -> 0
    }

    private companion object {
        const val TAG = "AutoVideo"
        const val TICK_MS = 2_000L
        const val CHECK_WINDOW_MS = 6_000L
        const val GPS_STALE_MS = 10_000L

        // El auto informa la velocidad real de las ruedas: umbral bajo.
        const val CAR_MOVING_MPS = 1.0f   // ~3,6 km/h
        const val CAR_STOPPED_MPS = 0.3f

        // El GPS tiene ruido aun estando quieto: umbral más alto.
        const val GPS_MOVING_MPS = 2.5f   // ~9 km/h
        const val GPS_STOPPED_MPS = 1.2f
    }
}
