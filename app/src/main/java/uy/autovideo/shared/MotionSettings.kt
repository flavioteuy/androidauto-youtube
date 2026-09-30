package uy.autovideo.shared

import android.content.Context

/**
 * Ajustes de la protección en movimiento (se cambian desde la app del teléfono).
 *
 * La imagen se tapa solo si el GPS indica que el auto va a más de [speedKmh]
 * durante [sustainedSeconds] seguidos. Así el GPS no la tapa por error con el
 * auto estacionado. Los rangos están limitados a propósito: la protección no se
 * puede alargar tanto que deje ver video durante el viaje.
 */
object MotionSettings {

    private const val PREFS = "motion_settings"
    private const val KEY_SPEED = "min_speed_kmh"
    private const val KEY_SECONDS = "sustained_seconds"

    const val MIN_SPEED_KMH = 5
    const val MAX_SPEED_KMH = 300
    const val DEFAULT_SPEED_KMH = 15

    const val MIN_SECONDS = 2
    const val MAX_SECONDS = 7200
    const val DEFAULT_SECONDS = 10

    fun speedKmh(context: Context): Int =
        prefs(context).getInt(KEY_SPEED, DEFAULT_SPEED_KMH).coerceIn(MIN_SPEED_KMH, MAX_SPEED_KMH)

    fun sustainedSeconds(context: Context): Int =
        prefs(context).getInt(KEY_SECONDS, DEFAULT_SECONDS).coerceIn(MIN_SECONDS, MAX_SECONDS)

    fun setSpeedKmh(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_SPEED, value.coerceIn(MIN_SPEED_KMH, MAX_SPEED_KMH)).apply()
    }

    fun setSustainedSeconds(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_SECONDS, value.coerceIn(MIN_SECONDS, MAX_SECONDS)).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
