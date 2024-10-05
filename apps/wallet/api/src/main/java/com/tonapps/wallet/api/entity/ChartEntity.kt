package com.tonapps.wallet.api.entity

import org.json.JSONArray

data class ChartEntity(
    val date: Long,
    val price: Float
) {
    constructor(array: JSONArray) : this(
        array.getLong(0),
        array.getDouble(1).toFloat()
    )
}