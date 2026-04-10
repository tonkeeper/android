package com.tonapps.extensions

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.DurationUnit
import kotlin.time.toDuration

interface TimedCache<K : CacheKey> {
    // When it can have rase condition, but it's OK
    fun <V : Any> getUnsafe(key: K, payload: String? = null): V?

    suspend fun <V : Any> loadAndUpdate(
        key: K,
        payload: String? = null,
        withClean: Boolean = false,
        loader: suspend () -> V,
    ): V

    suspend fun <V : Any> getOrLoad(
        key: K,
        payload: String? = null,
        loader: suspend () -> V,
    ): V

    suspend fun remove(key: K, payload: String? = null)
    suspend fun <V : Any> get(key: K, payload: String? = null): V?
    suspend fun <V : Any> put(key: K, payload: String? = null, value: V)
}

fun interface DateTimeProvider {
    fun currentTimeMillis(): Long
}

interface CacheKey {
    companion object {
        val DEFAULT_TTL = 10.toDuration(DurationUnit.MINUTES).inWholeMilliseconds
    }

    val ttl: Long get() = Long.MAX_VALUE
    fun key(): Any = this::class
}

class TimedCacheMemory<K : CacheKey> : TimedCache<K> {

    private val mutexes = ConcurrentHashMap<Any, Mutex>()
    private val dateTimeProvider: DateTimeProvider = DateTimeProvider { Clock.System.now().toEpochMilliseconds() }

    private val cache = HashMap<Any, Pair<Long, Any>>()
    private val payloads = HashMap<Any, String?>()

    override suspend fun <V : Any> loadAndUpdate(
        key: K,
        payload: String?,
        withClean: Boolean,
        loader: suspend () -> V,
    ): V {
        val mutex = mutexes.computeIfAbsent(key) { Mutex() }
        return mutex.withLock {
            if (withClean) {
                cache.remove(key)
            }

            val newValue = loader()
            internalPut(key, payload, newValue)

            newValue
        }
    }

    override suspend fun <V : Any> getOrLoad(
        key: K,
        payload: String?,
        loader: suspend () -> V,
    ): V {
        val mutex = mutexes.computeIfAbsent(key) { Mutex() }
        return mutex.withLock {
            val value = internalGet<V>(key, payload)

            if (value != null) {
                return@withLock value
            }

            val newValue = loader()
            internalPut(key, payload, newValue)

            newValue
        }
    }

    override suspend fun remove(key: K, payload: String?) {
        val mutex = mutexes.computeIfAbsent(key) { Mutex() }
        mutex.withLock {
            payloads.remove(key)
            cache.remove(key)
        }
    }

    override fun <V : Any> getUnsafe(key: K, payload: String?): V? {
        return internalGet(key, payload)
    }

    override suspend fun <V : Any> get(key: K, payload: String?): V? {
        val mutex = mutexes.computeIfAbsent(key) { Mutex() }
        return mutex.withLock {
            internalGet(key, payload)
        }
    }

    override suspend fun <V : Any> put(key: K, payload: String?, value: V) {
        val mutex = mutexes.computeIfAbsent(key) { Mutex() }
        return mutex.withLock {
            internalPut(key, payload, value)
        }
    }


    private fun <V : Any> internalGet(key: K, payload: String?): V? {
        val oldToken = payloads[key]
        if (oldToken != payload) {
            cache.remove(key)
        }

        val (createdAt, value) = cache[key]
            ?: return null

        return if ((dateTimeProvider.currentTimeMillis() - createdAt) < key.ttl) {
            @Suppress("UNCHECKED_CAST")
            value as? V
        } else {
            payloads.remove(key)
            cache.remove(key)
            null
        }
    }

    private fun <T : CacheKey, V : Any> internalPut(key: T, payload: String?, value: V) {
        payloads[key] = payload
        cache[key] = dateTimeProvider.currentTimeMillis() to value
    }
}
