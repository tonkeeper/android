package com.tonapps.wallet.api.entity

import android.os.Parcelable
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.contract.WalletVersion
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
): Parcelable {

    val address: String
        get() = preview.address

    val name: String?
        get() = preview.name

    val isWallet: Boolean
        get() = preview.isWallet

    constructor(contract: BaseWalletContract, testnet: Boolean, new: Boolean = false) : this(
        query = "",
        preview = AccountEntity(contract.address, testnet),
        active = true,
        walletVersion = contract.getWalletVersion(),
        balance = 0,
        new = new
    )

    constructor(query: String, account: Account, testnet: Boolean, new: Boolean = false) : this(
        query = query,
        preview = AccountEntity(account, testnet),
        active = account.status == AccountStatus.active,
        walletVersion = resolveVersion(account.interfaces),
        balance = account.balance,
        new = new
    )

    private companion object {
        private fun resolveVersion(interfaces: List<String>?): WalletVersion {
            interfaces ?: return WalletVersion.UNKNOWN
            return if(interfaces.contains("wallet_v5_beta")) {
                WalletVersion.V5BETA
            } else if (interfaces.contains("wallet_v5")) {
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