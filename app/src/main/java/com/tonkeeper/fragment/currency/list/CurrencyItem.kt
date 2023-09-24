package com.tonkeeper.fragment.currency.list

import com.tonkeeper.uikit.list.BaseListItem

data class CurrencyItem(
    val code: String,
    val nameResId: Int,
    val selected: Boolean,
    override val position: Cell.Position
): BaseListItem(), BaseListItem.Cell