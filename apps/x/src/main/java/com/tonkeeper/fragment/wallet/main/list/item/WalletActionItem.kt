package com.tonkeeper.fragment.wallet.main.list.item

import ton.wallet.WalletType

data class WalletActionItem(
    val walletType: WalletType
): WalletItem(TYPE_ACTIONS)