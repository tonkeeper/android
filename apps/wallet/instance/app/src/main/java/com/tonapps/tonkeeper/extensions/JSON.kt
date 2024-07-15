package com.tonapps.tonkeeper.extensions

import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.getStringArray(key: String): List<String> {
    val array = this.getJSONArray(key)
    val list = mutableListOf<String>()
    for (i in 0 until array.length()) {
        list.add(array.getString(i))
    }
    return list
}

fun List<String>.toJSONArray(): JSONArray {
    val array = JSONArray()
    for (i in this.indices) {
        array.put(this[i])
    }
    return array
}