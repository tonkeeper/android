package com.tonapps.wallet.api

import io.tonapi.infrastructure.Serializer
import com.squareup.moshi.adapter

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T> toJSON(obj: T?): String {
    if (obj == null) {
        return ""
    }
    return Serializer.moshi.adapter<T>().toJson(obj)
}

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T> fromJSON(json: String): T {
    return Serializer.moshi.adapter<T>().fromJson(json)!!
}