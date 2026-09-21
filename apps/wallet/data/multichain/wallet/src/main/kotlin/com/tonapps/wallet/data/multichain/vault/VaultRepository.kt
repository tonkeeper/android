package com.tonapps.wallet.data.multichain.vault

import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.tracer.AnalyticException
import com.tonapps.chainkit.core.chain.model.account.KeyPair
import com.tonapps.log.L
import com.tonapps.security.multichain.VaultStorage
import com.tonapps.wallet.SecureProofProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class VaultRepository(
    private val dao: VaultMetadataDao,
    private val proofProvider: SecureProofProvider,
) : VaultStorage {

    private val deviceKeyMutex = Mutex()

    private val deviceSessionLock = ReentrantReadWriteLock()
    private var deviceSession: DeviceSession? = null

    override fun getMasterSalt(): ByteArray? = dao.get(KEY_MASTER_SALT)
    override fun getMasterIv(): ByteArray? = dao.get(KEY_MASTER_IV)
    override fun getEncryptedMasterKey(): ByteArray? = dao.get(KEY_MASTER_KEY)
    override fun getMasterVerification(): ByteArray? = dao.get(KEY_MASTER_VERIFICATION)

    override fun getSessionKeyIv(): ByteArray? = dao.get(KEY_SESSION_IV)
    override fun getEncryptedSessionKey(): ByteArray? = dao.get(KEY_SESSION_KEY)

    override fun getDeviceSessionKey(): ByteArray? = dao.get(KEY_DEVICE_SESSION_KEY)

    override fun saveDeviceSessionKey(blob: ByteArray): Result<Unit> = runCatching {
        dao.put(VaultMetadataEntity(KEY_DEVICE_SESSION_KEY, blob))
    }

    override fun clearDeviceSessionKey(): Result<Unit> = runCatching {
        dao.delete(KEY_DEVICE_SESSION_KEY)
    }

    override fun saveMasterKey(
        salt: ByteArray,
        iv: ByteArray,
        encryptedMasterKey: ByteArray,
        verification: ByteArray,
    ): Result<Unit> = runCatching {
        dao.putAll(
            listOf(
                VaultMetadataEntity(KEY_MASTER_SALT, salt),
                VaultMetadataEntity(KEY_MASTER_IV, iv),
                VaultMetadataEntity(KEY_MASTER_KEY, encryptedMasterKey),
                VaultMetadataEntity(KEY_MASTER_VERIFICATION, verification),
            )
        )
    }

    override fun saveSessionKey(
        iv: ByteArray,
        encryptedSessionKey: ByteArray,
    ): Result<Unit> = runCatching {
        dao.putAll(
            listOf(
                VaultMetadataEntity(KEY_SESSION_IV, iv),
                VaultMetadataEntity(KEY_SESSION_KEY, encryptedSessionKey),
            )
        )
    }

    /**
     * The device certificate (ECDSA P-256) behind every device proof — register, refresh, push
     * subscribe. Normally minted with the vault ([createVault]); this is the lazy fallback for
     * installs with no vault yet, or when that minting failed. The mutex keeps two callers from
     * racing a second certificate over the one the backend already registered. Null when minting
     * fails.
     */
    suspend fun getOrCreateDeviceKey(): KeyPair? {
        return deviceKeyMutex.withLock {
            try {
                readDeviceKey() ?: mintDeviceKey()
            } catch (e: Throwable) {
                L.e(e)
                AnalyticsHelper.Default.captureException(
                    AnalyticException.DeviceAuth("device key unavailable", e)
                )
                null
            }
        }
    }

    override suspend fun createVault(
        salt: ByteArray,
        iv: ByteArray,
        encryptedMasterKey: ByteArray,
        verification: ByteArray,
        sessionIv: ByteArray,
        encryptedSessionKey: ByteArray,
    ): Result<Unit> = runCatching {
        deviceKeyMutex.withLock {
            val deviceKey = if (dao.get(KEY_DEVICE_KEY) == null) {
                proofProvider.generateDeviceCertificate()
            } else {
                null
            }

            try {
                dao.putAll(
                    listOfNotNull(
                        VaultMetadataEntity(KEY_MASTER_SALT, salt),
                        VaultMetadataEntity(KEY_MASTER_IV, iv),
                        VaultMetadataEntity(KEY_MASTER_KEY, encryptedMasterKey),
                        VaultMetadataEntity(KEY_MASTER_VERIFICATION, verification),
                        VaultMetadataEntity(KEY_SESSION_IV, sessionIv),
                        VaultMetadataEntity(KEY_SESSION_KEY, encryptedSessionKey),
                        deviceKey?.let { VaultMetadataEntity(KEY_DEVICE_KEY, it.privateKey) },
                    )
                )
            } finally {
                deviceKey?.privateKey?.fill(0)
            }
        }
    }

    fun hasDeviceKey(): Boolean {
        return dao.get(KEY_DEVICE_KEY) != null
    }

    fun deleteDeviceKey() {
        deviceSessionLock.write {
            dao.deleteAll(listOf(KEY_DEVICE_KEY) + DEVICE_SESSION_KEYS)
            deviceSession = null
        }
    }

    fun getDeviceId(): String? {
        return dao.get(KEY_DEVICE_ID)?.decodeToString()
    }

    fun getDeviceSession(): DeviceSession? {
        deviceSessionLock.read {
            deviceSession?.let { return it }
        }

        return deviceSessionLock.write {
            deviceSession ?: readDeviceSession()?.also { deviceSession = it }
        }
    }

    fun saveDeviceSession(session: DeviceSession) {
        deviceSessionLock.write {
            dao.putAll(
                listOf(
                    VaultMetadataEntity(KEY_DEVICE_ID, session.deviceId.encodeToByteArray()),
                    VaultMetadataEntity(
                        KEY_DEVICE_ACCESS_TOKEN,
                        session.accessToken.encodeToByteArray(),
                    ),
                    VaultMetadataEntity(
                        KEY_DEVICE_REFRESH_TOKEN,
                        session.refreshToken.encodeToByteArray(),
                    ),
                )
            )

            deviceSession = session
        }
    }

    fun deleteDeviceSession() {
        deviceSessionLock.write {
            dao.deleteAll(DEVICE_SESSION_KEYS)
            deviceSession = null
        }
    }

    private fun readDeviceSession(): DeviceSession? {
        val session = dao.getAll(DEVICE_SESSION_KEYS).associate { it.key to it.value }
        val deviceId = session[KEY_DEVICE_ID]?.decodeToString() ?: return null
        val accessToken = session[KEY_DEVICE_ACCESS_TOKEN]?.decodeToString() ?: return null
        val refreshToken = session[KEY_DEVICE_REFRESH_TOKEN]?.decodeToString() ?: return null

        return DeviceSession(
            deviceId = deviceId,
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
    }

    // The stored bytes are handed to the KeyPair, which keeps the same array — the caller owns
    // its lifetime, so it must not be zeroed here.
    private fun readDeviceKey(): KeyPair? {
        val privateKey = dao.get(KEY_DEVICE_KEY)
            ?: return null

        return try {
            KeyPair.fromPrivateKey(privateKey)
        } catch (e: Throwable) {
            L.e(e)
            AnalyticsHelper.Default.captureException(
                AnalyticException.DeviceAuth("corrupted device key dropped", e)
            )
            deleteDeviceKey()
            null
        }
    }

    // Throws when the fresh certificate could not be persisted — handing back a certificate we
    // cannot re-read would register a device we can never prove again.
    private fun mintDeviceKey(): KeyPair {
        val keyPair = proofProvider.generateDeviceCertificate()

        try {
            deleteDeviceSession()
            dao.put(VaultMetadataEntity(KEY_DEVICE_KEY, keyPair.privateKey))
        } catch (e: Throwable) {
            keyPair.privateKey.fill(0)
            throw e
        }

        return keyPair
    }

    override fun clear(): Result<Unit> = runCatching {
        deviceSessionLock.write {
            dao.clear()
            deviceSession = null
        }
    }

    private companion object {
        const val KEY_MASTER_SALT = "salt"
        const val KEY_MASTER_IV = "iv"
        const val KEY_MASTER_KEY = "master"
        const val KEY_MASTER_VERIFICATION = "verification"
        const val KEY_SESSION_IV = "session_iv"
        const val KEY_SESSION_KEY = "session_key"
        const val KEY_DEVICE_SESSION_KEY = "device_session_key"
        const val KEY_DEVICE_KEY = "device_key"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DEVICE_ACCESS_TOKEN = "device_access_token"
        const val KEY_DEVICE_REFRESH_TOKEN = "device_refresh_token"

        val DEVICE_SESSION_KEYS = listOf(
            KEY_DEVICE_ID,
            KEY_DEVICE_ACCESS_TOKEN,
            KEY_DEVICE_REFRESH_TOKEN,
        )
    }
}
