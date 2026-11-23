package com.tonapps.wallet.data.ledger

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.extensions.hex

data class LedgerLegacyWallet(
    val walletId: String,
    val name: String,
    val accountIndex: Int,
    val tonPublicKeyHex: String,
    val tonAddress: String,
)

object LedgerLegacyTwins {

    fun find(wallets: List<WalletEntity>, tonPublicKeyHex: String): WalletEntity? =
        wallets.firstOrNull { wallet -> isMigratable(wallet) && isSameKey(wallet.publicKey.hex(), tonPublicKeyHex) }

    fun isMigratable(wallet: WalletEntity): Boolean = wallet.isLedger && !wallet.testnet

    fun isSameKey(first: String, second: String): Boolean = normalize(first) == normalize(second)

    private fun normalize(hex: String): String = hex.removePrefix(HEX_PREFIX).lowercase()
}

private const val HEX_PREFIX = "0x"
