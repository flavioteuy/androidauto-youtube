package uy.autovideo.shared

import android.net.Uri
import java.net.URLEncoder

/** Convierte lo que escribe o comparte el usuario en una URL de YouTube móvil. */
object YouTubeLinks {

    const val HOME = "https://m.youtube.com/"

    private val URL_REGEX = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val VIDEO_ID_REGEX =
        Regex("""(?:youtu\.be/|[?&]v=|/shorts/|/live/|/embed/)([A-Za-z0-9_-]{11})""")
    private val TIME_REGEX = Regex("""[?&]t=(\d+)""")

    fun searchUrl(query: String): String =
        "https://m.youtube.com/results?search_query=" + URLEncoder.encode(query.trim(), "UTF-8")

    fun isYouTubeHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return h == "youtu.be" || h == "youtube.com" || h.endsWith(".youtube.com")
    }

    /**
     * Devuelve la URL a abrir, o null si el texto está vacío o es un enlace que no es de YouTube.
     * - Enlace de video (youtu.be, watch?v=, shorts...) -> página del video en m.youtube.com
     * - Otro enlace de YouTube (canal, lista...) -> el mismo en m.youtube.com
     * - Texto normal -> búsqueda en YouTube
     */
    fun resolve(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null

        val url = URL_REGEX.find(text)?.value?.trimEnd('.', ',', ')', ';')
        if (url == null) return searchUrl(text)

        val uri = Uri.parse(url)
        if (!isYouTubeHost(uri.host)) return null

        val id = VIDEO_ID_REGEX.find(url)?.groupValues?.get(1)
        if (id != null) {
            val seconds = TIME_REGEX.find(url)?.groupValues?.get(1)
            return if (seconds != null) {
                "https://m.youtube.com/watch?v=$id&t=${seconds}s"
            } else {
                "https://m.youtube.com/watch?v=$id"
            }
        }

        return if (uri.host?.lowercase() == "youtu.be") {
            HOME
        } else {
            uri.buildUpon().scheme("https").authority("m.youtube.com").build().toString()
        }
    }
}
