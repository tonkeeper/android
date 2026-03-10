package com.tonapps.extensions

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.toDuration

interface TimedCache<T> {
    fun get(key: String): T?
    fun put(key: String, value: T)
}

fun interface DateTimeProvider {
    fun currentTimeMillis(): Long
}

suspend fun <T : Any?> TimedCache<T>.getOrLoad(key: String, loader: suspend () -> T): T {
    return get(key)
        ?: loader().also { put(key, it) }
}

class TimedCacheMemory<T>(
    private val dateTimeProvider: DateTimeProvider = DateTimeProvider {
        Clock.System.now().toEpochMilliseconds()
    },
    private val cache: ConcurrentMap<String, Pair<Long, T>> = ConcurrentHashMap(),
    private val ttl: Long = DEFAULT_TTL,
) : TimedCache<T> {

    companion object {
        val DEFAULT_TTL = 10.toDuration(DurationUnit.MINUTES).inWholeMilliseconds
    }

    override fun get(key: String): T? {
        val (createdAt, value) = cache[key] ?: return null
        return if ((dateTimeProvider.currentTimeMillis() - createdAt) < ttl) {
            value
        } else {
            cache.remove(key)
            null
        }
    }

    override fun put(key: String, value: T) {
        cache[key] = dateTimeProvider.currentTimeMillis() to value
    }
}
