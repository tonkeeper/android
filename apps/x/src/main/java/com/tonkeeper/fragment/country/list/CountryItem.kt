package com.tonkeeper.fragment.country.list

import com.tonkeeper.fragment.country.CountryScreenFeature
import uikit.list.BaseListItem
import uikit.list.ListCell

data class CountryItem(
    val code: String,
    val title: String,
    val emoji: String,
    val selected: Boolean,
    override val position: ListCell.Position
): BaseListItem(), ListCell {

    constructor(
        country: CountryScreenFeature.Country,
        selected: Boolean,
        position: ListCell.Position
    ) : this(
        country.code,
        country.title,
        country.emoji,
        selected,
        position
    )
}