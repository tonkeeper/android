package com.tonapps.wallet.api.entity

import org.json.JSONArray
import org.json.JSONObject

data class ChartEntity(
    val date: Long,
    val price: Float
) {
    constructor(array: JSONArray) : this(
        array.getLong(0),
        array.getDouble(1).toFloat()
    )
}