package com.tonapps.wallet.data.collectibles

internal fun <T> loadAllNftPages(
    fetchPage: (offset: Int, limit: Int) -> List<T>?,
    addressOf: (T) -> String,
    checkCancelled: () -> Unit = {},
): List<T>? {
    val items = linkedMapOf<String, T>()
    var offset = 0
    while (true) {
        checkCancelled()
        val page = fetchPage(offset, 1000)
        checkCancelled()
        if (page == null) return null
        if (page.isEmpty()) return items.values.toList()
        val previousSize = items.size
        page.forEach { items[addressOf(it)] = it }
        // A short page may be a server-side page cap; only an empty page completes the inventory.
        if (items.size == previousSize || offset > Int.MAX_VALUE - page.size) return null
        offset += page.size
    }
}
