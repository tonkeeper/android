package com.tonapps.wallet

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.chain.model.account.PrivateKey

/**
 * The keys a confirmation signs with. A multichain wallet signs with its BIP39 [Hd] wallet; a legacy
 * wallet has no HD wallet behind its addresses, so it signs with the raw [Keys] its TON and TRON
 * addresses were created with.
 */
sealed interface SigningWallet {

    fun getPrivateKey(chain: Chain): PrivateKey

    class Hd(val wallet: CryptoWallet) : SigningWallet {

        override fun getPrivateKey(chain: Chain): PrivateKey = wallet.getPrivateKey(chain)
    }

    class Keys(private val keys: Map<Chain, ByteArray>) : SigningWallet {

        val chains: Set<Chain>
            get() = keys.keys

        override fun getPrivateKey(chain: Chain): PrivateKey {
            val key = keys[chain] ?: throw IllegalStateException("No signing key for $chain")
            return PrivateKey(com.trustwallet.core.PrivateKey(key.copyOf()))
        }
    }
}

// Signing that needs the whole HD wallet (Bitcoin inputs, relayer payloads) is only offered to
// multichain wallets; reaching it with raw keys is a bug of the caller.
fun SigningWallet.requireHd(): CryptoWallet {
    return (this as? SigningWallet.Hd)?.wallet
        ?: throw IllegalStateException("This transaction can only be signed by an HD wallet")
}
