package com.tonkeeper.fragment.wallet.main.list.item

data class WalletDataItem(
    val amount: String,
    val address: String
): WalletItem(TYPE_DATA)