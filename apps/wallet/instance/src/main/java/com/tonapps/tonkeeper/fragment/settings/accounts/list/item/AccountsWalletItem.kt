package com.tonapps.tonkeeper.fragment.settings.accounts.list.item

import com.tonapps.uikit.list.ListCell
import ton.wallet.Wallet

data class AccountsWalletItem(
    val id: Long,
    val address: String,
    val name: String,
    override val position: ListCell.Position
): AccountsItem(TYPE_WALLET, position) {

    val nameOrDefault: String
        get() = name.ifEmpty { address }

    constructor(wallet: Wallet, position: ListCell.Position) : this(
        id = wallet.id,
        address = wallet.address,
        name = wallet.name,
        position = position
    )
}