package com.tonapps.wallet.api.entity

import android.os.Parcelable
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import io.tonapi.models.Account
import io.tonapi.models.AccountStatus
import io.tonapi.models.Wallet
import kotlinx.parcelize.Parcelize

@Parcelize
data class AccountDetailsEntity(
    val query: String,
    val preview: AccountEntity,
    val active: Boolean,
    val walletVersion: WalletVersion,
    val balance: Long,
    val new: Boolean = false,
    val initialized: Boolean,
    val network: TonNetwork,
): Parcelable {

    val address: String
        get() = preview.address

    val name: String?
        get() = preview.name

    constructor(
        contract: BaseWalletContract,
        network: TonNetwork,
        new: Boolean = false,
        initialized: Boolean,
    ) : this(
        query = "",
        preview = AccountEntity(contract.address, network),
        active = true,
        walletVersion = contract.getWalletVersion(),
        balance = 0,
        new = new,
        initialized = initialized,
        network = network,
    )

    constructor(
        query: String,
        account: Account,
        network: TonNetwork,
        new: Boolean = false
    ) : this(
        query = query,
        preview = AccountEntity(account, network),
        active = account.status == AccountStatus.active,
        walletVersion = resolveVersion(network, account.interfaces, account.address),
        balance = account.balance,
        new = new,
        initialized = account.status == AccountStatus.active || account.status == AccountStatus.frozen,
        network = network,
    )

    constructor(
        query: String,
        wallet: Wallet,
        network: TonNetwork,
        new: Boolean = false
    ) : this(
        query = query,
        preview = AccountEntity(wallet, network),
        active = wallet.status == AccountStatus.active,
        walletVersion = resolveVersion(network, wallet.interfaces, wallet.address),
        balance = wallet.balance,
        new = new,
        initialized = wallet.status == AccountStatus.active || wallet.status == AccountStatus.frozen,
        network = network,
    )

    private companion object {

        private fun resolveVersion(network: TonNetwork, interfaces: List<String>?, address: String): WalletVersion {
            val version = resolveVersionByInterface(interfaces)
            if (version == WalletVersion.UNKNOWN) {
                return resolveVersionByAddress(address.toUserFriendly(
                    wallet = true,
                    testnet = network.isTestnet,
                ))
            }
            return version
        }

        private fun resolveVersionByAddress(userFriendlyAddress: String): WalletVersion {
            /*if (userFriendlyAddress.isValidTonAddress()) {
                return WalletVersion.V5BETA
            }*/
            return WalletVersion.UNKNOWN
        }

        private fun resolveVersionByInterface(interfaces: List<String>?): WalletVersion {
            interfaces ?: return WalletVersion.UNKNOWN
            return if (interfaces.contains("wallet_v5_beta")) {
                WalletVersion.V5BETA
            } else if (interfaces.contains("wallet_v5") || interfaces.contains("wallet_v5r1")) {
                WalletVersion.V5R1
            } else if (interfaces.contains("wallet_v4r2")) {
                WalletVersion.V4R2
            } else if (interfaces.contains("wallet_v3r2")) {
                WalletVersion.V3R2
            } else if (interfaces.contains("wallet_v3r1")) {
                WalletVersion.V3R1
            } else if (interfaces.contains("wallet_v4r1")) {
                WalletVersion.V4R1
            } else {
                WalletVersion.UNKNOWN
            }
        }
    }

}
