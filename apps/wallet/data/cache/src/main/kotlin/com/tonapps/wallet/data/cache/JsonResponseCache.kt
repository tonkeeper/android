package com.tonapps.wallet.data.cache

import kotlinx.serialization.json.Json

object JsonResponseCache {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    inline fun <reified T> encode(value: T): String {
        return json.encodeToString(value)
    }

    inline fun <reified T> decode(string: String): T? {
        return runCatching {
            json.decodeFromString<T>(string)
        }.getOrNull()
    }
}
