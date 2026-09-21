package com.tonapps.security.multichain

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.tonapps.log.L
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

sealed interface DeviceSessionKey {

    /** Takes ownership of [sessionKey]. */
    class Restored(val sessionKey: ByteArray) : DeviceSessionKey

    /** The key is intact but unusable until the screen is unlocked; nothing to repair. */
    data object Locked : DeviceSessionKey

    /** The keystore key is gone for good — drop the blob and re-seal after the next PIN unlock. */
    data object Lost : DeviceSessionKey
}

/**
 * Seals the session key under a hardware-backed key bound to device unlock, so a returning process
 * can attach the session without a passcode.
 *
 * Strictly a cache. Keystore entries do not survive every OS update, restore or factory image, so
 * the PIN-sealed copy in the vault stays the source of truth and every failure here degrades to the
 * passcode path — which recovers the same session key, leaving everything sealed under it valid.
 */
class SessionKeyCipher(context: Context) {

    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)

    private val keyStore: KeyStore? by lazy {
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }

    private val isDeviceLocked: Boolean
        get() = keyguardManager?.isDeviceLocked == true

    fun seal(sessionKey: ByteArray): ByteArray? {
        val key = getOrCreateKey() ?: return null

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val sealed = SealedData(cipher.iv, cipher.doFinal(sessionKey))
            try {
                sealed.toBytes()
            } finally {
                sealed.clear()
            }
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }

    fun open(blob: ByteArray): DeviceSessionKey {
        val key = readKey() ?: return DeviceSessionKey.Lost

        if (isDeviceLocked) {
            return DeviceSessionKey.Locked
        }

        val sealed = try {
            SealedData.fromBytes(blob)
        } catch (e: Throwable) {
            L.e(e)
            return DeviceSessionKey.Lost
        }

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, sealed.iv))
            DeviceSessionKey.Restored(cipher.doFinal(sealed.ciphertext))
        } catch (e: Throwable) {
            L.e(e)
            // A screen that locks mid-operation reads exactly like a discarded key; only the second
            // one may drop the blob, so the lock state decides rather than the exception.
            if (isDeviceLocked) {
                DeviceSessionKey.Locked
            } else {
                DeviceSessionKey.Lost
            }
        } finally {
            sealed.clear()
        }
    }

    /** Drops the wrapping key, leaving any blob still on disk unopenable rather than merely stale. */
    fun delete() {
        try {
            keyStore?.deleteEntry(ALIAS)
        } catch (e: Throwable) {
            L.e(e)
        }
    }

    private fun readKey(): SecretKey? {
        val store = keyStore ?: return null

        return try {
            store.getKey(ALIAS, null) as? SecretKey
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }

    // Without setUnlockedDeviceRequired the cache would be readable by the process whenever it
    // runs, locked screen included, so pre-P installs get no key at all and stay on the PIN path.
    private fun getOrCreateKey(): SecretKey? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return null
        }

        readKey()
            ?.let { return it }

        return try {
            keyStore?.deleteEntry(ALIAS)

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE)
                    .setRandomizedEncryptionRequired(true)
                    .setUnlockedDeviceRequired(true)
                    .build()
            )
            generator.generateKey()
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "mc_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE = 256
        const val TAG_LENGTH_BITS = 128
    }
}
