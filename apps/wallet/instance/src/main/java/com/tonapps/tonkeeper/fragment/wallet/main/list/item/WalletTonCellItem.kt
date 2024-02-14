package com.tonapps.tonkeeper.fragment.wallet.main.list.item

data class WalletTonCellItem(
    override val position: com.tonapps.uikit.list.ListCell.Position,
    val balance: String,
    val balanceCurrency: String,
    val rate: String,
    val rateDiff24h: String
): WalletCellItem(TYPE_TON, position)