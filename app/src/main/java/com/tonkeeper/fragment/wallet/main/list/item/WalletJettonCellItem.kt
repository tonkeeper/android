package com.tonkeeper.fragment.wallet.main.list.item

import android.net.Uri
import uikit.list.ListCell

class WalletJettonCellItem(
    position: ListCell.Position,
    val iconURI: Uri,
    val code: String,
    val balance: String,
    val balanceCurrency: String,
    val rate: String,
    val rateDiff24h: String
): WalletCellItem(TYPE_JETTON, position)