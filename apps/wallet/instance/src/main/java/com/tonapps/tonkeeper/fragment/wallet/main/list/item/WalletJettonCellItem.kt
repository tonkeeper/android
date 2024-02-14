package com.tonapps.tonkeeper.fragment.wallet.main.list.item

import android.net.Uri

data class WalletJettonCellItem(
    override val position: com.tonapps.uikit.list.ListCell.Position,
    val address: String,
    val name: String,
    val iconURI: Uri,
    val code: String,
    val balance: String,
    val balanceCurrency: String?,
    val rate: String?,
    val rateDiff24h: String?
): WalletCellItem(TYPE_JETTON, position)