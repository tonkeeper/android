package com.tonkeeper.fragment.wallet.main.list.item

import uikit.list.ListCell

open class WalletCellItem(
    type: Int,
    override val position: ListCell.Position
): WalletItem(type), ListCell