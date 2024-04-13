package com.tonapps.tonkeeper.dialog.fiat.list

import com.tonapps.tonkeeper.core.fiat.models.FiatItem

data class MethodItem(
    val body: FiatItem,
    override val position: com.tonapps.uikit.list.ListCell.Position
): com.tonapps.uikit.list.BaseListItem(), com.tonapps.uikit.list.ListCell {

    val id: String
        get() = body.id

    val title: String
        get() = body.title

    val subtitle: String
        get() = body.subtitle

    val iconUrl: String
        get() = body.iconUrl
}