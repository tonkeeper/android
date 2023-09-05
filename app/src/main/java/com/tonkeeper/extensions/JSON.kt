package com.tonkeeper.extensions

import org.json.JSONObject

fun JSONObject.getFloat(key: String): Float {
    return this.optDouble(key).toFloat()
}

fun JSONObject.optFloat(key: String): Float {
    return this.optDouble(key).toFloat()
}