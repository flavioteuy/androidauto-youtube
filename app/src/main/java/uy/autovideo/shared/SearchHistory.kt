package uy.autovideo.shared

import android.content.Context

/** Últimas búsquedas, para poder repetirlas en el auto sin teclado. */
object SearchHistory {

    private const val PREFS = "search_history"
    private const val KEY = "items"
    private const val MAX_ITEMS = 6

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return raw.split('\n').filter { it.isNotBlank() }
    }

    fun add(context: Context, query: String) {
        val q = query.trim().replace('\n', ' ')
        if (q.isEmpty()) return
        val items = load(context).filterNot { it.equals(q, ignoreCase = true) }.toMutableList()
        items.add(0, q)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, items.take(MAX_ITEMS).joinToString("\n"))
            .apply()
    }
}
