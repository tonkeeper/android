package com.tonapps.tonkeeper.fragment.currency.list


data class CurrencyItem(
    val currency: String,
    val nameResId: Int,
    val selected: Boolean,
    override val position: com.tonapps.uikit.list.ListCell.Position
): com.tonapps.uikit.list.BaseListItem(), com.tonapps.uikit.list.ListCell {

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