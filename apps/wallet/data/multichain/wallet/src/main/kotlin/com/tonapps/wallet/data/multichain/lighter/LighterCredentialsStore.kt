package com.tonapps.wallet.data.multichain.lighter

import com.tonapps.async.Async
import com.tonapps.log.L
import com.tonapps.security.multichain.SessionKeyStore
import com.tonapps.wallet.data.multichain.wallet.CredentialDao
import com.tonapps.wallet.data.multichain.wallet.CredentialEntity
import com.tonapps.wallet.data.multichain.wallet.CredentialIds
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Lighter credentials per wallet. The L2 key is sealed with the session key in the shared
 * `credential` table: kept there from the wallet's creation or its first unlock, and the same key
 * once an account is registered with it. The account binding is not a secret and lives in
 * [accounts]. Saves propagate failures, and key arrays crossing this store stay caller-owned:
 * never zeroed here.
 */
internal class LighterCredentialsStore(
    private val dao: CredentialDao,
    private val sessionKeyStore: SessionKeyStore,
    private val accounts: LighterAccountPrefs,
) {

    suspend fun loadL2PrivateKey(walletId: String): ByteArray? = withContext(Async.Io) {
        awaitUnlocked(walletId)

        readSealed(l2KeyId(walletId))
    }

    suspend fun loadAccount(walletId: String): StoredLighterAccount? = withContext(Async.Io) {
        accounts.load(walletId) ?: migrateLegacyAccount(walletId) // TODO remove migration before release
    }

    // The key goes first: a key without an account is a wallet not activated yet, never a broken one.
    suspend fun saveActivation(walletId: String, key: ByteArray, account: StoredLighterAccount) {
        withContext(Async.Io) {
            awaitUnlocked(walletId)

            dao.insertCredential(CredentialEntity(id = l2KeyId(walletId), data = sessionKeyStore.encrypt(key)))
            accounts.save(walletId, account)
        }
    }

    suspend fun hasL2Key(walletId: String): Boolean = withContext(Async.Io) {
        dao.credentialExists(l2KeyId(walletId))
    }

    /** Keeps [key] unless the slot holds one already: a key an account is registered with is never replaced here. */
    suspend fun keepL2Key(walletId: String, key: ByteArray) {
        withContext(Async.Io) {
            awaitUnlocked(walletId)

            dao.insertCredentialIfAbsent(CredentialEntity(id = l2KeyId(walletId), data = sessionKeyStore.encrypt(key)))
        }
    }

    /** Drops what is kept outside the `credential` table; the wallet's rows go with the wallet. */
    fun forgetAccount(walletId: String) {
        accounts.remove(walletId)
    }

    // Accounts were sealed in the `credential` table before they moved to prefs: moved on first read.
    // Moving is idempotent, so two reads racing write the same account and drop the same row.
    private suspend fun migrateLegacyAccount(walletId: String): StoredLighterAccount? {
        val accountId = legacyAccountId(walletId)
        // Read before waiting for the session key: a wallet with no legacy row never waits.
        val sealed = dao.getData(accountId)
            ?: return null

        awaitUnlocked(walletId)

        val payload = unseal(accountId, sealed)
            ?: return null

        val account = try {
            parseLegacyAccount(payload)
        } finally {
            payload.fill(0)
        }

        if (account == null) {
            L.e("lighter: malformed account payload, dropping $accountId")
        } else {
            accounts.save(walletId, account)
        }

        dao.deleteCredential(accountId)

        return account
    }

    private fun parseLegacyAccount(payload: ByteArray): StoredLighterAccount? {
        return if (payload.size == ACCOUNT_PAYLOAD_SIZE && payload[0] == ACCOUNT_PAYLOAD_VERSION) {
            val buffer = ByteBuffer.wrap(payload, VERSION_SIZE, ACCOUNT_PAYLOAD_SIZE - VERSION_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN)
            // The key derivation index that follows is no longer used.
            StoredLighterAccount(accountIndex = buffer.long, apiKeyIndex = buffer.int)
        } else if (payload.size == ACCOUNT_PAYLOAD_V1_SIZE && payload[0] == ACCOUNT_PAYLOAD_V1) {
            val buffer = ByteBuffer.wrap(payload, VERSION_SIZE, ACCOUNT_PAYLOAD_V1_SIZE - VERSION_SIZE)
                .order(ByteOrder.LITTLE_ENDIAN)
            StoredLighterAccount(accountIndex = buffer.long, apiKeyIndex = buffer.int)
        } else {
            null
        }
    }

    private fun l2KeyId(walletId: String) = CredentialIds.lighterL2Key(walletId, CredentialIds.LIGHTER_MAINNET)

    private fun legacyAccountId(walletId: String) = CredentialIds.lighterAccount(walletId, CredentialIds.LIGHTER_MAINNET)

    private suspend fun awaitUnlocked(walletId: String) {
        if (!sessionKeyStore.awaitUnlocked(UNLOCK_TIMEOUT_MS)) {
            throw IllegalStateException("Session key is locked, wallet $walletId")
        }
    }

    private fun readSealed(id: String): ByteArray? {
        val sealed = dao.getData(id)
            ?: return null

        return unseal(id, sealed)
    }

    private fun unseal(id: String, sealed: ByteArray): ByteArray? {
        return try {
            sessionKeyStore.decrypt(sealed)
        } catch (e: Throwable) {
            if (!sessionKeyStore.isUnlocked) {
                throw e
            }
            L.e(e, "lighter: unreadable credential, dropping $id")
            // Only what was read: a key written since, by an activation or a keep, stays.
            dao.deleteCredentialIfUnchanged(id, sealed)
            null
        }
    }

    private companion object {
        const val UNLOCK_TIMEOUT_MS = 5_000L
        const val VERSION_SIZE = 1
        const val ACCOUNT_PAYLOAD_V1_SIZE = VERSION_SIZE + Long.SIZE_BYTES + Int.SIZE_BYTES
        const val ACCOUNT_PAYLOAD_V1: Byte = 1
        const val ACCOUNT_PAYLOAD_SIZE = ACCOUNT_PAYLOAD_V1_SIZE + Long.SIZE_BYTES
        const val ACCOUNT_PAYLOAD_VERSION: Byte = 2
    }
}
