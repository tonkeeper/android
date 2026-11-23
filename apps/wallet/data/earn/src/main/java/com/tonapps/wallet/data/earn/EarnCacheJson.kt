package com.tonapps.wallet.data.earn

import io.Serializer

// The generated Earn models carry @Contextual types (OffsetDateTime) that only Serializer.JSON registers.
internal object EarnCacheJson {

    inline fun <reified T> encode(value: T): String = Serializer.toJSON(value)

    inline fun <reified T> decode(string: String): T? = runCatching { Serializer.fromJSON<T>(string) }.getOrNull()
}
