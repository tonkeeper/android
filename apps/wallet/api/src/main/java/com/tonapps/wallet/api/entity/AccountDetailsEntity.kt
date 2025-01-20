package com.tonapps.wallet.api.entity

import android.os.Parcelable
import android.util.Log
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.isValidTonAddress
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.blockchain.ton.extensions.toUserFriendly
import io.tonapi.models.Account
import io.tonapi.models.AccountStatus
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
    val testnet: Boolean,
): Parcelable {

    val address: String
        get() = preview.address

    val name: String?
        get() = preview.name

    constructor(
        contract: BaseWalletContract,
        testnet: Boolean,
        new: Boolean = false,
        initialized: Boolean,
    ) : this(
        query = "",
        preview = AccountEntity(contract.address, testnet),
        active = true,
        walletVersion = contract.getWalletVersion(),
        balance = 0,
        new = new,
        initialized = initialized,
        testnet = testnet,
    )

    constructor(
        query: String,
        account: Account,
        testnet: Boolean,
        new: Boolean = false
    ) : this(
        query = query,
        preview = AccountEntity(account, testnet),
        active = account.status == AccountStatus.active,
        walletVersion = resolveVersion(testnet, account.interfaces, account.address),
        balance = account.balance,
        new = new,
        initialized = account.status == AccountStatus.active || account.status == AccountStatus.frozen,
        testnet = testnet,
    )

    private companion object {

        private fun resolveVersion(testnet: Boolean, interfaces: List<String>?, address: String): WalletVersion {
            val version = resolveVersionByInterface(interfaces)
            if (version == WalletVersion.UNKNOWN) {
                return resolveVersionByAddress(address.toUserFriendly(
                    wallet = true,
                    testnet = testnet,
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