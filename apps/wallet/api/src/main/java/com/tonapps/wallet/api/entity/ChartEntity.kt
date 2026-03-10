package com.tonapps.wallet.api.entity

import org.json.JSONArray

data class ChartEntity(
    val date: Long,
    val price: Float
) {

    val isEmpty: Boolean
        get() = this == EMPTY

    constructor(array: JSONArray) : this(
        array.getLong(0),
        array.getDouble(1).toFloat()
    )

    companion object {
        val EMPTY = ChartEntity(0, 0f)
    }
}