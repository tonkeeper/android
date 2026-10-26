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
                cursors[key] = remote
                remote
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
        mutex.withLock { cursors[key] = remote - 1 }
    }

    private fun advance(key: Key, cursor: Long): Long {
        if (cursor == Long.MAX_VALUE) {
            throw PerpsExecutionException(PerpsExecutionErrorKind.Validation, "nonce overflow")
        }
        val next = cursor + 1
        cursors[key] = next

        return next
    }

    private fun key(scope: PerpsScope): Key {
        return Key(
            accountIndex = scope.accountIndex,
            chainId = scope.chainId,
        )
    }
}
