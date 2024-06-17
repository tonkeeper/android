package com.tonapps.wallet.data.account

import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.contract.walletVersion
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.rn.data.RNMnemonic
import com.tonapps.wallet.data.rn.data.RNWallet
import com.tonapps.wallet.data.rn.data.RNWallet.Companion.int
import org.ton.api.pub.PublicKeyEd25519
import org.ton.crypto.hex

internal class RNMigrationHelper(
    private val rnLegacy: RNLegacy
) {

    suspend fun loadSecureStore(passcode: String): List<RNMnemonic> {
        return rnLegacy.loadSecureStore(passcode)
    }

    suspend fun loadLegacy(): Pair<String, List<WalletEntity>> {
        val legacyWallets = rnLegacy.getWallets()
        var selectedIdentifier = legacyWallets.selectedIdentifier
        if (legacyWallets.wallets.isEmpty()) {
            return Pair("", emptyList())
        }
        val list = mutableListOf<WalletEntity>()
        for (legacyWallet in legacyWallets.wallets) {
            val version = walletVersion(legacyWallet.version)
            if (version == WalletVersion.UNKNOWN || version == WalletVersion.V5R1) {
                if (selectedIdentifier == legacyWallet.identifier) {
                    selectedIdentifier = ""
                }
                continue
            }

            var emoji = legacyWallet.emoji
            if (emoji.startsWith("ic-")) {
                emoji = emoji.replace("ic-", "custom_")
            }
            emoji = emoji.removeSuffix("-32").replace("-", "_")

            val label = Wallet.Label(
                accountName = legacyWallet.name,
                emoji = emoji,
                color = legacyWallet.color.int,
            )
            val type: Wallet.Type
            if (legacyWallet.network == RNWallet.Network.Testnet) {
                type = Wallet.Type.Testnet
            } else if (legacyWallet.type == RNWallet.Type.Regular) {
                type = Wallet.Type.Default
            } else if (legacyWallet.type == RNWallet.Type.WatchOnly) {
                type = Wallet.Type.Watch
            } else if (legacyWallet.type == RNWallet.Type.Lockup) {
                type = Wallet.Type.Lockup
            } else if (legacyWallet.type == RNWallet.Type.SignerDeeplink) {
                type = Wallet.Type.Signer
            } else if (legacyWallet.type == RNWallet.Type.Signer) {
                type = Wallet.Type.SignerQR
            } else if (legacyWallet.type == RNWallet.Type.Ledger) {
                type = Wallet.Type.Ledger
            } else {
                continue
            }
            val entity = WalletEntity(
                id = legacyWallet.identifier,
                publicKey = PublicKeyEd25519(hex(legacyWallet.pubkey)),
                type = type,
                version = version,
                label = label
            )
            list.add(entity)
        }
        if (selectedIdentifier.isEmpty()) {
            val wallet = list.firstOrNull() ?: return Pair("", emptyList())
            selectedIdentifier = wallet.id
        }
        return Pair(selectedIdentifier, list)
    }
}