package com.tonapps.wallet.data.ledger

import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.ledgerkit.apdu.Bip32Path

object LedgerDerivation {

    const val MAX_ACCOUNT_INDEX = 100

    fun ton(index: Int): Bip32Path = Bip32Path.parse("m/44'/607'/0'/0'/$index'/0'")

    fun ethereum(index: Int): Bip32Path = Bip32Path.parse("m/44'/60'/$index'/0/0")

    fun tron(index: Int): Bip32Path = Bip32Path.parse("m/44'/195'/$index'/0/0")

    fun solana(index: Int): Bip32Path = Bip32Path.parse("m/44'/501'/$index'/0'")

    fun bitcoinAccount(index: Int): Bip32Path = Bip32Path.parse("m/84'/0'/$index'")

    fun bitcoinAddress(index: Int, change: Int, addressIndex: Int): Bip32Path =
        Bip32Path.parse("m/84'/0'/$index'/$change/$addressIndex")

    fun defaultPath(chain: Chain, index: Int): Bip32Path = when (chain) {
        is Chain.Ton -> ton(index)
        is Chain.Tron -> tron(index)
        is Chain.Bitcoin -> bitcoinAccount(index)
        is Chain.Solana -> solana(index)
        else -> ethereum(index)
    }
}
