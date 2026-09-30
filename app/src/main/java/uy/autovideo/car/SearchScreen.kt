package uy.autovideo.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import uy.autovideo.R
import uy.autovideo.shared.SearchHistory

/** Búsqueda con el teclado del auto (solo disponible con el vehículo detenido). */
class SearchScreen(
    carContext: CarContext,
    private val onQuery: (String) -> Unit,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val history = SearchHistory.load(carContext)

        val list = ItemList.Builder()
        history.forEach { query ->
            list.addItem(
                Row.Builder()
                    .setTitle(query)
                    .setOnClickListener { submit(query) }
                    .build()
            )
        }
        if (history.isEmpty()) {
            list.setNoItemsMessage(carContext.getString(R.string.search_no_history))
        }

        val callback = object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {}

            override fun onSearchSubmitted(searchText: String) {
                submit(searchText)
            }
        }

        return SearchTemplate.Builder(callback)
            .setHeaderAction(Action.BACK)
            .setSearchHint(carContext.getString(R.string.search_hint))
            .setShowKeyboardByDefault(true)
            .setItemList(list.build())
            .build()
    }

    private fun submit(query: String) {
        if (query.isBlank()) return
        SearchHistory.add(carContext, query)
        onQuery(query.trim())
        screenManager.pop()
    }
}
