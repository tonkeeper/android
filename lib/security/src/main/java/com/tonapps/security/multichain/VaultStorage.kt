package com.tonapps.security.multichain

/**
 * Storage interface for vault metadata (encrypted master key, salt, etc).
 * Implemented by Room-based storage in the app layer.
 */
interface VaultStorage {

    fun getMasterSalt(): ByteArray?
    fun getMasterIv(): ByteArray?
    fun getEncryptedMasterKey(): ByteArray?
    fun getMasterVerification(): ByteArray?

    fun getSessionKeyIv(): ByteArray?
    fun getEncryptedSessionKey(): ByteArray?

    /** The same session key sealed by [SessionKeyCipher] — a cache, dropped whenever unreadable. */
    fun getDeviceSessionKey(): ByteArray?
    fun saveDeviceSessionKey(blob: ByteArray): Result<Unit>
    fun clearDeviceSessionKey(): Result<Unit>

    fun saveMasterKey(salt: ByteArray, iv: ByteArray, encryptedMasterKey: ByteArray, verification: ByteArray): Result<Unit>
    fun saveSessionKey(iv: ByteArray, encryptedSessionKey: ByteArray): Result<Unit>

    suspend fun createVault(
        salt: ByteArray,
        iv: ByteArray,
        encryptedMasterKey: ByteArray,
        verification: ByteArray,
        sessionIv: ByteArray,
        encryptedSessionKey: ByteArray,
    ): Result<Unit>

    fun clear(): Result<Unit>
}
