package com.tonapps.tonkeeper.fragment.wallet.main.list.item

open class WalletCellItem(
    type: Int,
    override val position: com.tonapps.uikit.list.ListCell.Position
): WalletItem(type), com.tonapps.uikit.list.ListCell