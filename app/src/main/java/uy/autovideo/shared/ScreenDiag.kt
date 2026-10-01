package uy.autovideo.shared

import android.content.Context
import android.util.Log
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Guarda cómo estaba armada la página de YouTube en la pantalla del auto (solo la estructura,
 * sin textos ni datos de la cuenta), para poder ajustar el diseño. Se copia desde la app del teléfono.
 */
object ScreenDiag {

    const val VIDEO = "video"
    const val MIX = "mix"

    private const val TAG = "AutoVideo"

    private fun file(context: Context, kind: String) = File(context.filesDir, "diag_$kind.txt")

    fun save(context: Context, kind: String, text: String) {
        try {
            val stamp = DateFormat.getDateTimeInstance().format(Date())
            file(context, kind).writeText("capturado: $stamp\n$text")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo guardar el diagnóstico", e)
        }
    }

    fun has(context: Context, kind: String): Boolean = file(context, kind).exists()

    /** Diagnóstico completo (video común y Mix), o null si todavía no se capturó nada. */
    fun combined(context: Context, header: String): String? {
        val parts = listOf(VIDEO to "VIDEO COMUN", MIX to "MIX / LISTA").mapNotNull { (kind, title) ->
            val f = file(context, kind)
            if (f.exists()) "===== $title =====\n" + f.readText() else null
        }
        if (parts.isEmpty()) return null
        return header + "\n\n" + parts.joinToString("\n\n")
    }
}
