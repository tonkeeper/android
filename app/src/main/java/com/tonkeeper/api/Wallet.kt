package com.tonkeeper.api

import com.tonkeeper.api.model.JettonItemModel
import com.tonkeeper.api.model.NFTItem

data class Wallet(
    val address: String,
    val balanceTON: Float,
    val balanceUSD: Float,
    val jettons: List<JettonItemModel>,
    val rate: Float,
    val rateDiff24h: String,
    val nfts: List<NFTItem>
)