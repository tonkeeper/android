package com.tonapps.perps.trade

import com.tonapps.chainkit.lighter.trading.PerpsExecutionErrorKind
import com.tonapps.chainkit.lighter.trading.PerpsExecutionException
import com.tonapps.chainkit.perpetual.PerpsScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Back-to-back steps share one relay nonce until the venue advances it, so the cursor is local.
internal class PerpsNonceCoordinator {

    private data class Key(
        val accountIndex: Long,
        val chainId: Int,
    )

    private val mutex = Mutex()

    private val cursors = mutableMapOf<Key, Long>()

    private val accepted = mutableMapOf<Key, Long>()

    private val issuedCounts = mutableMapOf<Key, Long>()

    suspend fun next(scope: PerpsScope, fetch: suspend () -> Long): Long {
        val key = key(scope)
        mutex.withLock {
            val cursor = cursors[key]
            if (cursor != null) {
                return advance(key, cursor)
            }
        }

        val remote = fetch()
        if (remote < 0) {
            throw PerpsExecutionException(PerpsExecutionErrorKind.Validation, "nonce response is negative")
        }

        return mutex.withLock {
            val cursor = cursors[key]
            if (cursor == null) {
                val first = maxOf(remote, (accepted[key] ?: remote - 1) + 1)
                cursors[key] = first
                countIssued(key)
                first
            } else {
                advance(key, cursor)
            }
        }
    }

    suspend fun resync(scope: PerpsScope, fetch: suspend () -> Long) {
        val key = key(scope)
        val remote = try {
            fetch()
        } catch (e: Throwable) {
            mutex.withLock { cursors.remove(key) }
            throw e
        }
        if (remote < 0) {
            mutex.withLock { cursors.remove(key) }
            throw PerpsExecutionException(PerpsExecutionErrorKind.Validation, "nonce response is negative")
        }
        mutex.withLock { cursors[key] = maxOf(remote - 1, accepted[key] ?: remote - 1) }
    }

    suspend fun catchUp(scope: PerpsScope, fetch: suspend () -> Long) {
        val key = key(scope)
        val (cursorBeforeRead, issuedBeforeRead) = mutex.withLock { cursors[key] to (issuedCounts[key] ?: 0L) }
        val remote = try {
            fetch()
        } catch (e: Throwable) {
            mutex.withLock { cursors.remove(key) }
            throw e
        }
        if (remote < 0) {
            mutex.withLock { cursors.remove(key) }
            throw PerpsExecutionException(PerpsExecutionErrorKind.Validation, "nonce response is negative")
        }
        mutex.withLock {
            val movedDuringRead = cursors[key]?.takeIf { current ->
                (issuedCounts[key] ?: 0L) != issuedBeforeRead || cursorBeforeRead == null || current > cursorBeforeRead
            }
            cursors[key] = listOfNotNull(remote - 1, accepted[key], movedDuringRead).max()
        }
    }

    suspend fun markAccepted(scope: PerpsScope, nonce: Long) {
        val key = key(scope)
        mutex.withLock {
            accepted[key] = maxOf(accepted[key] ?: nonce, nonce)
        }
    }

    suspend fun returnUnconsumed(scope: PerpsScope, nonce: Long) {
        val key = key(scope)
        mutex.withLock {
            if (cursors[key] == nonce) {
                cursors[key] = nonce - 1
            }
        }
    }

    suspend fun forget(scope: PerpsScope) {
        mutex.withLock { cursors.remove(key(scope)) }
    }

    private fun advance(key: Key, cursor: Long): Long {
        if (cursor == Long.MAX_VALUE) {
            throw PerpsExecutionException(PerpsExecutionErrorKind.Validation, "nonce overflow")
        }
        val next = cursor + 1
        cursors[key] = next
        countIssued(key)

        return next
    }

    private fun countIssued(key: Key) {
        issuedCounts[key] = (issuedCounts[key] ?: 0L) + 1
    }

    private fun key(scope: PerpsScope): Key {
        return Key(
            accountIndex = scope.accountIndex,
            chainId = scope.chainId,
        )
    }
}
