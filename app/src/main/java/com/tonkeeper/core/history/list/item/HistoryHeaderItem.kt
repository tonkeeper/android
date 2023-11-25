package com.tonkeeper.core.history.list.item

import com.tonkeeper.helper.DateFormat

data class HistoryHeaderItem(
    val title: String,
    val titleResId: Int? = null
): HistoryItem(TYPE_HEADER) {

    constructor(timestamp: Long) : this(
        DateFormat.monthWithDate(timestamp)
    )

    constructor(titleResId: Int) : this(
        title = "",
        titleResId = titleResId
    )
}