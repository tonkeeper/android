package com.tonapps.wallet.data.earn

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * Wallets that sent an Earn transaction within the last minute. The positions the backend loads
 * right after a transaction may predate it, so every portfolio load of such a wallet asks the
 * backend to load them again rather than reuse what it has.
 */
class EarnPortfolioDirtyFlags {

    private val markedAt = ConcurrentHashMap<String, Long>()

    fun markDirty(walletId: String, nowMillis: Long = SystemClock.elapsedRealtime()) {
        markedAt[walletId] = nowMillis
    }

    internal fun isDirty(walletId: String, nowMillis: Long = SystemClock.elapsedRealtime()): Boolean {
        val marked = markedAt[walletId] ?: return false
        if (nowMillis - marked < WINDOW_MILLIS) {
            return true
        }
        markedAt.remove(walletId, marked)
        return false
    }

    private companion object {
        private const val WINDOW_MILLIS = 60_000L
    }
}
