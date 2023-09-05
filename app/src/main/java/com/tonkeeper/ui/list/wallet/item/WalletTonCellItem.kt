package com.tonkeeper.ui.list.wallet.item

data class WalletTonCellItem(
    val balance: String,
    val balanceUSD: String,
    val rate: String,
    val rateDiff24h: String
): WalletCellItem(TYPE_TON, Position.FIRST)