package com.tonkeeper.fragment.wallet.main.list.item

import android.net.Uri
import uikit.list.ListCell

data class WalletJettonCellItem(
    override val position: ListCell.Position,
    val address: String,
    val name: String,
    val iconURI: Uri,
    val code: String,
    val balance: String,
    val balanceCurrency: String,
    val rate: String,
    val rateDiff24h: String
): WalletCellItem(TYPE_JETTON, position)