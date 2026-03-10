package com.tonapps.tonkeeper.ui.screen.browser.search.list

import android.net.Uri
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.browser.entities.BrowserAppEntity

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_TITLE = 0
        const val TYPE_SEARCH = 1
        const val TYPE_LINK = 2
        const val TYPE_APP = 3
    }

    data class Title(val value: String) : Item(TYPE_TITLE)

    data class Link(
        val position: ListCell.Position,
        val url: String,
        val title: String
    ): Item(TYPE_LINK) {

        val host: String
            get() = Uri.parse(url).host!!
    }

    data class App(
        val position: ListCell.Position,
        val app: BrowserAppEntity
    ): Item(TYPE_APP) {

        val icon: Uri
            get() = app.icon

        val name: String
            get() = app.name

        val description: String
            get() = app.description

        val url: String
            get() = app.url.toString()

        val host: String
            get() = app.url.host!!
    }

    abstract class Search(
        val query: String,
        val position: ListCell.Position,
        urlPrefix: String,
    ): Item(TYPE_SEARCH) {

        val url = "$urlPrefix$query"

        val host: String
            get() = Uri.parse(url).host!!
    }

    class SearchGoogle(
        query: String,
        position: ListCell.Position
    ): Search(query, position, "https://www.google.com/search?q=")

    class SearchDuckDuckGo(
        query: String,
        position: ListCell.Position
    ): Search(query, position, "https://duckduckgo.com/?q=")
}