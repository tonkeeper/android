package com.tonapps.wallet.data.core

data class SearchEngine(val title: String) {

    companion object {
        val GOOGLE = SearchEngine("Google")
        val DUCKDUCKGO = SearchEngine("DuckDuckGo")

        val all = listOf(GOOGLE, DUCKDUCKGO)

        fun byId(id: Long): SearchEngine? = all.find { it.id == id }
    }

    val id: Long
        get() = title.hashCode().toLong()
}