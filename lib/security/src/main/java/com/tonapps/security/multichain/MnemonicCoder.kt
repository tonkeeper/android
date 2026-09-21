package com.tonapps.security.multichain

import com.tonapps.chainkit.core.secure.ext.use
import com.tonapps.chainkit.core.secure.mnemonic.Mnemonic
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class MnemonicCoder internal constructor(
    private val data: ByteArray,
) : AutoCloseable {

    private val isDisposed = AtomicBoolean(false)

    fun encryptMnemonic(mnemonic: Mnemonic): ByteArray {
        check(!isDisposed()) { "MnemonicCoder is already disposed" }
        return mnemonic.toBytes().use { bytes ->
            CryptoSecure.sealToBytes(data, bytes)
        }
    }

    fun decryptMnemonic(encryptedMnemonic: ByteArray): Mnemonic {
        check(!isDisposed()) { "MnemonicCoder is already disposed" }
        return CryptoSecure.openBytes(data, encryptedMnemonic).use { bytes ->
            Mnemonic.fromBytes(bytes)
        }
    }

    fun isDisposed(): Boolean = isDisposed.load()

    override fun close() {
        if (isDisposed.compareAndSet(expectedValue = false, newValue = true)) {
            data.fill(0)
        }
    }
}
