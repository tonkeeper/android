package com.tonkeeper.dialog.fiat.list

import com.tonkeeper.core.fiat.models.FiatItem
import uikit.list.BaseListItem
import uikit.list.ListCell

data class MethodItem(
    val body: FiatItem,
    override val position: ListCell.Position
): BaseListItem(), ListCell {

    val id: String
        get() = body.id

    val title: String
        get() = body.title

    val subtitle: String
        get() = body.subtitle

    val iconUrl: String
        get() = body.iconUrl
}