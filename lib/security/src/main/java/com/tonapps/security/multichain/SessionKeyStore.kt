package com.tonapps.security.multichain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * App-lifetime holder of the plaintext multichain session key: attached after a successful unlock,
 * kept until the process dies. Only non-critical, re-derivable secrets may be sealed with it —
 * mnemonics and chain keys stay behind the master key.
 */
class SessionKeyStore {

    private val lock = Any()
    private var key: ByteArray? = null

    private val _unlockedFlow = MutableStateFlow(false)
    val unlockedFlow: StateFlow<Boolean> = _unlockedFlow.asStateFlow()

    val isUnlocked: Boolean
        get() = synchronized(lock) { key != null }

    /** Takes ownership of [sessionKey]; a previously attached key is zeroed. */
    internal fun attach(sessionKey: ByteArray) {
        synchronized(lock) {
            key?.fill(0)
            key = sessionKey
            _unlockedFlow.value = true
        }
    }

    fun clear() {
        synchronized(lock) {
            key?.fill(0)
            key = null
            _unlockedFlow.value = false
        }
    }

    /**
     * Suspends until the session unlocks, at most [timeoutMs], and returns the resulting state.
     * Bounded on purpose: with desynced PIN stores the session never unlocks, and callers must
     * fall back to their locked-state behavior instead of hanging forever.
     */
    suspend fun awaitUnlocked(timeoutMs: Long = AWAIT_UNLOCK_TIMEOUT_MS): Boolean {
        if (isUnlocked) {
            return true
        }

        withTimeoutOrNull(timeoutMs) {
            unlockedFlow.first { it }
        }

        return isUnlocked
    }

    fun encrypt(data: ByteArray): ByteArray {
        return withKey { sessionKey ->
            CryptoSecure.sealToBytes(sessionKey, data)
        }
    }

    fun decrypt(encryptedData: ByteArray): ByteArray {
        return withKey { sessionKey ->
            CryptoSecure.openBytes(sessionKey, encryptedData)
        }
    }

    // The copy is taken under the lock (attach/clear can't zero it mid-copy) and the crypto runs
    // outside the lock; the block's private copy is zeroed after use.
    private inline fun <T> withKey(block: (ByteArray) -> T): T {
        val copy = synchronized(lock) {
            key?.copyOf() ?: throw IllegalStateException("Session key is locked")
        }
        return try {
            block(copy)
        } finally {
            copy.fill(0)
        }
    }

    private companion object {
        const val AWAIT_UNLOCK_TIMEOUT_MS = 5_000L
    }
}
