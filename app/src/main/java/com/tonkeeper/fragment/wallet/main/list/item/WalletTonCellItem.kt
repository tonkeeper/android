package com.tonkeeper.fragment.wallet.main.list.item

import uikit.list.ListCell

data class WalletTonCellItem(
    val balance: String,
    val balanceCurrency: String,
    val rate: String,
    val rateDiff24h: String
): WalletCellItem(TYPE_TON, ListCell.Position.SINGLE)