package com.tonapps.wallet.data.account

import com.tonapps.blockchain.model.legacy.Wallet
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.contract.walletVersion
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.rn.data.RNVaultState
import com.tonapps.wallet.data.rn.data.RNWallet
import com.tonapps.wallet.data.rn.data.RNWallet.Companion.int
import org.ton.api.pub.PublicKeyEd25519
import org.ton.crypto.hex

internal class RNMigrationHelper(
    private val rnLegacy: RNLegacy
) {

    suspend fun loadSecureStore(passcode: String): RNVaultState {
        return rnLegacy.getVaultState(passcode)
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
            if (version == WalletVersion.UNKNOWN) {
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
            val type: WalletType
            if (legacyWallet.network == RNWallet.Network.Testnet) {
                type = WalletType.Testnet
            } else if (legacyWallet.type == RNWallet.Type.Regular) {
                type = WalletType.Default
            } else if (legacyWallet.type == RNWallet.Type.WatchOnly) {
                type = WalletType.Watch
            } else if (legacyWallet.type == RNWallet.Type.Lockup) {
                type = WalletType.Lockup
            } else if (legacyWallet.type == RNWallet.Type.SignerDeeplink) {
                type = WalletType.Signer
            } else if (legacyWallet.type == RNWallet.Type.Signer) {
                type = WalletType.SignerQR
            } else if (legacyWallet.type == RNWallet.Type.Ledger) {
                type = WalletType.Ledger
            } else if (legacyWallet.type == RNWallet.Type.Keystone) {
                type = WalletType.Keystone
            } else {
                continue
            }

            val entity = WalletEntity(
                id = legacyWallet.identifier,
                publicKey = PublicKeyEd25519(hex(legacyWallet.pubkey)),
                type = type,
                version = version,
                label = label,
                ledger = legacyWallet.ledger?.let {
                    WalletEntity.Ledger(
                        deviceId = it.deviceId,
                        accountIndex = it.accountIndex
                    )
                },
                keystone = legacyWallet.keystone?.let {
                    WalletEntity.Keystone(
                        xfp = it.xfp,
                        path = it.path
                    )
                },
                initialized = false
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