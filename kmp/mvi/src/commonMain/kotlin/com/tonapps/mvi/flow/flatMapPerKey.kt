package com.tonapps.mvi.flow

import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One inner flow per key of the latest collection, for a node whose values belong to items rather
 * than to the positions they sit in. A key that stays keeps its flow running untouched, a key that
 * leaves has its flow cancelled and its value dropped, a new key starts a flow of its own. Emits
 * the latest value of every current key that has emitted, after each change.
 *
 * An item is identified by [key] alone: the item a key arrived with is the one its flow keeps, even
 * if a later collection carries a different item under the same key.
 */
fun <T, K, R> Flow<Iterable<T>>.flatMapPerKey(
    key: (T) -> K,
    transform: (T) -> Flow<R>,
): Flow<Map<K, R>> = channelFlow {
    // Guards [running] and [latest] between the upstream and the per-key flows, and orders a key's
    // removal before any value its cancelled flow was about to publish.
    val lock = Mutex()
    val running = mutableMapOf<K, Job>()
    var latest = emptyMap<K, R>()

    collect { items ->
        val wanted = items.associateBy(key)

        lock.withLock {
            (running.keys - wanted.keys).forEach { running.remove(it)?.cancel() }

            val kept = latest.filterKeys(wanted::containsKey)
            if (kept.size != latest.size) {
                latest = kept
                send(latest)
            }

            wanted.forEach { (k, item) ->
                if (k in running) {
                    return@forEach
                }

                running[k] = launch {
                    transform(item).collect { value ->
                        lock.withLock {
                            ensureActive()
                            latest = latest + (k to value)
                            send(latest)
                        }
                    }
                }
            }
        }
    }
}
