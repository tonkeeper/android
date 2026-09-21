package io

import io.infrastructure.AtomicBooleanAdapter
import io.infrastructure.AtomicIntegerAdapter
import io.infrastructure.AtomicLongAdapter
import io.infrastructure.BigDecimalAdapter
import io.infrastructure.BigIntegerAdapter
import io.infrastructure.LocalDateAdapter
import io.infrastructure.LocalDateTimeAdapter
import io.infrastructure.OffsetDateTimeAdapter
import io.infrastructure.StringBuilderAdapter
import io.infrastructure.URIAdapter
import io.infrastructure.URLAdapter
import io.infrastructure.UUIDAdapter
import io.serializers.AnySerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object Serializer {

    private val contextualModule = SerializersModule {
        contextual(AnySerializer)
        contextual(BigDecimal::class, BigDecimalAdapter)
        contextual(BigInteger::class, BigIntegerAdapter)
        contextual(LocalDate::class, LocalDateAdapter)
        contextual(LocalDateTime::class, LocalDateTimeAdapter)
        contextual(OffsetDateTime::class, OffsetDateTimeAdapter)
        contextual(UUID::class, UUIDAdapter)
        contextual(AtomicInteger::class, AtomicIntegerAdapter)
        contextual(AtomicLong::class, AtomicLongAdapter)
        contextual(AtomicBoolean::class, AtomicBooleanAdapter)
        contextual(URI::class, URIAdapter)
        contextual(URL::class, URLAdapter)
        contextual(StringBuilder::class, StringBuilderAdapter)
    }

    @JvmStatic
    val JSON: Json by lazy {
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            explicitNulls = false
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