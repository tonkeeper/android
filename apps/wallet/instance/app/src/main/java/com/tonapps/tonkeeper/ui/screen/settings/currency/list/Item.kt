package com.tonapps.tonkeeper.ui.screen.settings.currency.list

import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.entity.CurrencyEntity

data class Item(
    val currency: CurrencyEntity,
    val selected: Boolean,
    val position: ListCell.Position
): BaseListItem()
