package com.tonkeeper.ton.extensions

import org.json.JSONObject

fun JSONObject.getStringsList(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    val list = mutableListOf<String>()
    for (i in 0 until array.length()) {
        list.add(array.getString(i))
    }
    return list
}

fun JSONObject.getFloat(name: String): Float {
    return getDouble(name).toFloat()
}