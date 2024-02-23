package com.tonapps.tonkeeper.fragment.wallet.main.list.item

import ton.wallet.WalletType

data class WalletDataItem(
    val amount: String,
    val address: String,
    val walletType: WalletType,
): WalletItem(TYPE_DATA)