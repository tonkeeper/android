package io

import io.serializers.AnySerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

object Serializer {

    private val contextualModule = SerializersModule {
        contextual(AnySerializer)
    }

    @JvmStatic
    val JSON: Json by lazy {
        Json {
            encodeDefaults = false
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            serializersModule = contextualModule
        }
    }

    inline fun <reified T> toJSON(value: T): String {
        return JSON.encodeToString(value)
    }

    inline fun <reified T> fromJSON(string: String): T {
        return JSON.decodeFromString(string)
    }
}