package com.tonapps.wallet.data.multichain.vault

import com.tonapps.log.L
import com.tonapps.security.multichain.SessionKeyStore
import com.tonapps.wallet.data.multichain.wallet.CredentialDao
import com.tonapps.wallet.data.multichain.wallet.CredentialEntity

/**
 * Per-wallet "app" private keys (the wallet auth key behind wallet_id / register proofs), sealed
 * with the session key. Non-critical: they cannot spend funds and are re-derivable from the
 * mnemonic, so losing them is always recoverable.
 */
class WalletKeyRepository(
    private val dao: CredentialDao,
    private val sessionKeyStore: SessionKeyStore,
) {

    fun credentialId(walletId: String): String = KEY_PREFIX + walletId

    fun hasAppKey(walletId: String): Boolean {
        return dao.credentialExists(credentialId(walletId))
    }

    fun sealedEntity(walletId: String, appPrivateKey: ByteArray): CredentialEntity? {
        return try {
            if (!sessionKeyStore.isUnlocked) {
                null
            } else {
                CredentialEntity(
                    data = sessionKeyStore.encrypt(appPrivateKey),
                    id = KEY_PREFIX + walletId,
                )
            }
        } catch (e: Throwable) {
            L.e(e)
            null
        } finally {
            appPrivateKey.fill(0)
        }
    }

    suspend fun save(walletId: String, appPrivateKey: ByteArray) {
        sessionKeyStore.awaitUnlocked()

        val entity = sealedEntity(walletId, appPrivateKey)
            ?: return

        try {
            dao.insertCredential(entity)
        } catch (e: Throwable) {
            L.e(e)
        }
    }

    suspend fun getAppPrivateKey(walletId: String): ByteArray? {
        if (!sessionKeyStore.awaitUnlocked()) {
            return null
        }

        return readAppPrivateKey(walletId)
    }

    suspend fun awaitAppPrivateKey(walletId: String, unlockTimeoutMs: Long): ByteArray? {
        if (!sessionKeyStore.awaitUnlocked(unlockTimeoutMs)) {
            return null
        }

        return readAppPrivateKey(walletId)
    }

    private fun readAppPrivateKey(walletId: String): ByteArray? {
        val encrypted = dao.getData(KEY_PREFIX + walletId)
            ?: return null

        return try {
            sessionKeyStore.decrypt(encrypted)
        } catch (e: Throwable) {
            L.e(e)
            dao.deleteCredential(KEY_PREFIX + walletId)
            null
        }
    }

    fun delete(walletId: String) {
        dao.deleteCredential(KEY_PREFIX + walletId)
    }

    private companion object {
        const val KEY_PREFIX = "app_key:"
    }
}
