package com.tonapps.tonkeeper.ui.screen.backup.main.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.backup.main.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.setMoonOnClickListener

class ManualHolder(
    parent: ViewGroup,
    private val onClick: (Item) -> Unit
): Holder<Item.ManualBackup>(parent, R.layout.view_backup_manual) {

    init {
    }

    override fun onBind(item: Item.ManualBackup) {
        itemView.setMoonOnClickListener { onClick(item) }
    }

}