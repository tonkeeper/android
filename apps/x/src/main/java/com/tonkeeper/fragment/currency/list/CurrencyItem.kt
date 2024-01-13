package com.tonkeeper.fragment.currency.list

import ton.SupportedCurrency
import uikit.list.BaseListItem
import uikit.list.ListCell

data class CurrencyItem(
    val currency: SupportedCurrency,
    val nameResId: Int,
    val selected: Boolean,
    override val position: ListCell.Position
): BaseListItem(), ListCell {

    override fun equals(other: Any?): Boolean {
        if (other is CurrencyItem) {
            return currency == other.currency && nameResId == other.nameResId && selected == other.selected && position == other.position
        }
        return false
    }

    override fun hashCode(): Int {
        var result = currency.hashCode()
        result = 31 * result + nameResId
        result = 31 * result + selected.hashCode()
        result = 31 * result + position.hashCode()
        return result
    }
}