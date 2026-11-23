package com.tonapps.wallet.data.ledger

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.core
import com.trustwallet.core.AnyAddress
import com.trustwallet.core.PublicKey
import com.trustwallet.core.PublicKeyType

internal object LedgerAddresses {

    fun display(chain: Chain, publicKey: ByteArray): String {
        val type = when (chain) {
            is Chain.Ton, is Chain.Solana -> PublicKeyType.ED25519
            is Chain.Bitcoin -> PublicKeyType.SECP256k1
            else -> PublicKeyType.SECP256k1Extended
        }
        return AnyAddress(PublicKey(publicKey, type), chain.coin.core).description
    }

    fun hexWithPrefix(bytes: ByteArray): String = "0x" + bytes.joinToString("") { "%02x".format(it) }
}
