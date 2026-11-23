package com.tonapps.blockchain.tron

import com.tonapps.chainkit.chain.tron.utils.TronUtils

// The TRON account of a legacy TON wallet, derived by ChainKit: from the entropy behind a standard
// TON mnemonic, or from a BIP39 phrase taken as is (no checksum check, as the wallet was imported).
class KeychainTrxAccountsProvider private constructor(
    private val account: TronUtils.LegacyAccount
) {

    val mnemonics: List<String>
        get() = account.mnemonic

    fun getAddress(): String {
        return account.address
    }

    fun getPrivateKey(): ByteArray {
        return account.privateKey
    }

    companion object {

        fun fromEntropy(entropy: ByteArray): KeychainTrxAccountsProvider {
            return KeychainTrxAccountsProvider(TronUtils.legacyAccountFromTonEntropy(entropy))
        }

        fun fromMnemonic(mnemonics: List<String>): KeychainTrxAccountsProvider {
            return KeychainTrxAccountsProvider(TronUtils.legacyAccountFromMnemonic(mnemonics))
        }
    }
}
