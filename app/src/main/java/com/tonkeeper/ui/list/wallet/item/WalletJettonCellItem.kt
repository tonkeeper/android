package com.tonkeeper.ui.list.wallet.item

import android.net.Uri

class WalletJettonCellItem(
    position: Position,
    val iconURI: Uri,
    val code: String,
    val balance: String,
): WalletCellItem(TYPE_JETTON, position)