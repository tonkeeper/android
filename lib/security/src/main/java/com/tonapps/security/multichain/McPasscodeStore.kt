package com.tonapps.security.multichain

import com.tonapps.log.L
import com.tonapps.security.Security
import com.tonapps.security.tryCallGC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest

// TODO Async.Io
// TODO Implement
class McPasscodeStore(
    private val storage: VaultStorage,
    private val sessionKeyStore: SessionKeyStore,
    private val sessionKeyCipher: SessionKeyCipher,
) {

    private val mutex = Mutex()

    val isSessionUnlocked: Boolean
        get() = sessionKeyStore.isUnlocked

    suspend fun hasVault(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            storage.getEncryptedMasterKey() != null
        }
    }

    /**
     * Quick PIN validation for UI — derives key, compares verification hash.
     * Does not decrypt the master key.
     */
    suspend fun isValidPin(pin: CharArray): Boolean {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val salt = storage.getMasterSalt() ?: return@withContext false
                val storedVerification = storage.getMasterVerification() ?: return@withContext false

                try {
                    val derivedKey = CryptoSecure.deriveKey(pin, salt) ?: return@withContext false
                    try {
                        val verification =
                            CryptoSecure.calcVerification(derivedKey, VERIFICATION_SIZE)
                        val result = MessageDigest.isEqual(storedVerification, verification)
                        verification.fill(0)
                        result
                    } finally {
                        derivedKey.fill(0)
                    }
                } finally {
                    salt.fill(0)
                    storedVerification.fill(0)
                    tryCallGC()
                }
            }
        }
    }

    suspend fun setPin(pin: CharArray): MnemonicCoder {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                // Guard against racing hasVault()/setPin() callers: silently minting a new master key
                // over a live vault would make everything sealed by the old one undecryptable.
                check(storage.getEncryptedMasterKey() == null) { "Vault already exists" }

                val salt = Security.randomBytes(CryptoSecure.SALT_LENGTH_BYTES)
                val masterKey = Security.randomBytes(MASTER_KEY_SIZE)
                val sessionKey = Security.randomBytes(SESSION_KEY_SIZE)

                try {
                    val derivedKey = CryptoSecure.deriveKey(pin, salt)
                        ?: throw IllegalStateException("Failed to derive key")

                    val (verification, sealedMasterKey) = try {
                        CryptoSecure.calcVerification(derivedKey, VERIFICATION_SIZE) to
                            CryptoSecure.seal(derivedKey, masterKey)
                    } finally {
                        derivedKey.fill(0)
                    }

                    val sealedSessionKey = CryptoSecure.seal(masterKey, sessionKey)

                    // Attached before the write because the storage seals the device certificate
                    // with it; dropped again if the write fails, so the store never reports
                    // unlocked for a vault that does not exist.
                    sessionKeyStore.attach(sessionKey.copyOf())

                    try {
                        storage.createVault(
                            salt = salt,
                            iv = sealedMasterKey.iv,
                            encryptedMasterKey = sealedMasterKey.ciphertext,
                            verification = verification,
                            sessionIv = sealedSessionKey.iv,
                            encryptedSessionKey = sealedSessionKey.ciphertext,
                        ).getOrThrow()
                    } catch (e: Throwable) {
                        sessionKeyStore.clear()
                        throw e
                    } finally {
                        verification.fill(0)
                        sealedMasterKey.clear()
                        sealedSessionKey.clear()
                    }

                    rotateDeviceSessionKey(sessionKey)

                    MnemonicCoder(masterKey.copyOf())
                } finally {
                    masterKey.fill(0)
                    sessionKey.fill(0)
                    tryCallGC()
                }
            }
        }
    }

    suspend fun unlock(pin: CharArray): MnemonicCoder {
        return mutex.withLock {
            internalUnlock(pin)
        }
    }

    private suspend fun internalUnlock(pin: CharArray): MnemonicCoder {
        return withContext(Dispatchers.IO) {
            val salt = storage.getMasterSalt() ?: throw IllegalStateException("No salt stored")
            val iv = storage.getMasterIv() ?: throw IllegalStateException("No IV stored")
            val encrypted = storage.getEncryptedMasterKey() ?: throw IllegalStateException("No master key stored")

            try {
                val derivedKey = CryptoSecure.deriveKey(pin, salt)
                    ?: throw IllegalStateException("Failed to derive key (unlock pin=${pin.size} salt=${salt.size})")

                try {
                    val masterKey = CryptoSecure.open(derivedKey, SealedData(iv, encrypted))

                    try {
                        unlockSessionKey(masterKey)
                    } catch (e: Throwable) {
                        L.e(e, "Credential Error")
                        // Best-effort: a session failure must not block vault access.
                    }

                    MnemonicCoder(masterKey)
                } finally {
                    derivedKey.fill(0)
                }
            } finally {
                salt.fill(0)
                iv.fill(0)
                encrypted.fill(0)
                tryCallGC()
            }
        }
    }

    suspend fun changePin(oldPin: CharArray, newPin: CharArray) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val salt = storage.getMasterSalt() ?: throw IllegalStateException("No salt stored")
                val iv = storage.getMasterIv() ?: throw IllegalStateException("No IV stored")
                val encrypted = storage.getEncryptedMasterKey()
                    ?: throw IllegalStateException("No master key stored")

                try {
                    val oldDerivedKey = CryptoSecure.deriveKey(oldPin, salt)
                        ?: throw IllegalStateException("Failed to derive old key (changePin oldPin=${oldPin.size} salt=${salt.size})")

                    val masterKey = try {
                        CryptoSecure.open(oldDerivedKey, SealedData(iv, encrypted))
                    } finally {
                        oldDerivedKey.fill(0)
                    }

                    try {
                        val newSalt = Security.randomBytes(CryptoSecure.SALT_LENGTH_BYTES)
                        val newDerivedKey = CryptoSecure.deriveKey(newPin, newSalt)
                            ?: throw IllegalStateException("Failed to derive new key (changePin newPin=${newPin.size} salt=${newSalt.size})")

                        try {
                            val newVerification =
                                CryptoSecure.calcVerification(newDerivedKey, VERIFICATION_SIZE)
                            val sealedMasterKey = CryptoSecure.seal(newDerivedKey, masterKey)

                            storage.saveMasterKey(
                                salt = newSalt,
                                iv = sealedMasterKey.iv,
                                encryptedMasterKey = sealedMasterKey.ciphertext,
                                verification = newVerification,
                            ).getOrThrow()

                            newVerification.fill(0)
                            sealedMasterKey.clear()
                        } finally {
                            newDerivedKey.fill(0)
                        }

                        // The master key is unchanged, so the wrapped session key stays valid.
                        try {
                            unlockSessionKey(masterKey)
                        } catch (e: Throwable) {
                            L.e(e, "Credential Error")
                            // Best-effort; retried on the next unlock.
                        }
                    } finally {
                        masterKey.fill(0)
                    }
                } finally {
                    salt.fill(0)
                    iv.fill(0)
                    encrypted.fill(0)
                    tryCallGC()
                }
            }
        }
    }

    /** Unlocks just long enough to unwrap the session key; no-op when already unlocked. */
    suspend fun unlockSession(pin: CharArray) {
        mutex.withLock {
            if (sessionKeyStore.isUnlocked) {
                return
            }

            internalUnlock(pin)
                .use { }
        }
    }

    suspend fun deleteAll() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                sessionKeyStore.clear()
                sessionKeyCipher.delete()
                storage.clear().getOrThrow()
            }
        }
    }

    /**
     * Attaches the session from the device-unlock-bound copy, no passcode involved. False when
     * there is nothing cached, when the screen is locked, or when the keystore key is gone; the
     * session then waits for a PIN unlock, which recovers the very same key from the vault.
     */
    suspend fun restoreSession(): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (sessionKeyStore.isUnlocked) {
                return@withContext true
            }

            val blob = storage.getDeviceSessionKey() ?: return@withContext false

            when (val restored = sessionKeyCipher.open(blob)) {
                is DeviceSessionKey.Restored -> {
                    sessionKeyStore.attach(restored.sessionKey)
                    true
                }
                DeviceSessionKey.Locked -> false
                DeviceSessionKey.Lost -> {
                    storage.clearDeviceSessionKey()
                    false
                }
            }
        }
    }

    /**
     * The session key is being replaced, so the wrapping key goes with it: a blob that outlives
     * this — a failed delete, a crash before the new one lands — is then unopenable rather than a
     * live handle to the superseded key, which would restore a session that decrypts no credential
     * and get every one of them discarded as unreadable.
     */
    private fun rotateDeviceSessionKey(sessionKey: ByteArray) {
        sessionKeyCipher.delete()
        storage.clearDeviceSessionKey()

        writeDeviceSessionKey(sessionKey)
    }

    // The session key is unchanged, so an existing blob still describes it. One sealed under a
    // wrapping key that has since been replaced is dropped by restoreSession and re-sealed by the
    // unlock after it, so refreshing it here would buy a single launch per keystore round trip.
    private fun saveDeviceSessionKey(sessionKey: ByteArray) {
        if (storage.getDeviceSessionKey() != null) {
            return
        }

        writeDeviceSessionKey(sessionKey)
    }

    // Best-effort by design: the vault copy is the source of truth, this one only spares a passcode.
    private fun writeDeviceSessionKey(sessionKey: ByteArray) {
        try {
            val blob = sessionKeyCipher.seal(sessionKey) ?: return
            storage.saveDeviceSessionKey(blob).getOrThrow()
        } catch (e: Throwable) {
            L.e(e)
        }
    }

    private fun createSessionKey(masterKey: ByteArray) {
        val sessionKey = Security.randomBytes(SESSION_KEY_SIZE)

        try {
            val sealedSessionKey = CryptoSecure.seal(masterKey, sessionKey)
            storage.saveSessionKey(sealedSessionKey.iv, sealedSessionKey.ciphertext)
                .getOrThrow()
            sealedSessionKey.clear()
        } catch (e: Throwable) {
            sessionKey.fill(0)
            throw e
        }

        rotateDeviceSessionKey(sessionKey)
        sessionKeyStore.attach(sessionKey)
    }

    private fun unlockSessionKey(masterKey: ByteArray) {
        if (sessionKeyStore.isUnlocked) {
            return
        }

        val iv = storage.getSessionKeyIv()
        val encrypted = storage.getEncryptedSessionKey()
        if (iv != null && encrypted != null) {
            try {
                val sessionKey = CryptoSecure.open(masterKey, SealedData(iv, encrypted))
                saveDeviceSessionKey(sessionKey)
                sessionKeyStore.attach(sessionKey)
                return
            } catch (_: Throwable) {
                // Fall through and mint a fresh session key.
            } finally {
                iv.fill(0)
                encrypted.fill(0)
            }
        }

        createSessionKey(masterKey)
    }

    private companion object {
        const val MASTER_KEY_SIZE = 32
        const val SESSION_KEY_SIZE = 32
        const val VERIFICATION_SIZE = 4
    }
}
