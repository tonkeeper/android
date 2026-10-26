package com.tonapps.portfolio.screens.search

import com.tonapps.perps.data.CatalogSearchRow

enum class SearchSection {
    TOKENS,
    PERPETUALS,
}

sealed interface SearchListItem {
    val key: String

    data class Row(val row: CatalogSearchRow) : SearchListItem {
        override val key: String get() = row.id
    }

    data class Header(val section: SearchSection, val nextRowId: String) : SearchListItem {
        override val key: String get() = "header_$nextRowId"
    }
}
