package com.tonkeeper.fragment.settings.accounts.list.item

import com.tonkeeper.api.shortAddress
import ton.wallet.Wallet
import uikit.list.ListCell

data class AccountsWalletItem(
    val createDate: Long,
    val name: String,
    val address: String,
    override val position: ListCell.Position
): AccountsItem(TYPE_WALLET, position) {

    val nameOrDefault: String
        get() {
            return if (name.isEmpty()) {
                "Wallet " + address.shortAddress
            } else {
                name
            }
        }

    constructor(wallet: Wallet, position: ListCell.Position) : this(
        createDate = wallet.id,
        name = wallet.name ?: "",
        address = wallet.address,
        position = position
    )
}