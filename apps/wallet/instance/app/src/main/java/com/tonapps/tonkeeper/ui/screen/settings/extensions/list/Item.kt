package com.tonapps.tonkeeper.ui.screen.settings.extensions.list

import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.entities.WalletEntity
import io.tonapi.models.WalletPlugin

sealed class Item(type: Int) : BaseListItem(type) {

    companion object {
        const val TYPE_PLUGIN = 0
    }

    data class Plugin(
        val plugin: WalletPlugin,
        val wallet: WalletEntity,
        val position: ListCell.Position
    ) : Item(TYPE_PLUGIN)
}



