package com.tonapps.tonkeeper.ui.screen.backup.main.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.backup.main.list.Item
import com.tonapps.tonkeeperx.R

class ManualAccentHolder(
    parent: ViewGroup,
    private val onClick: (Item) -> Unit
): Holder<Item.ManualAccentBackup>(parent, R.layout.view_backup_manual_accent) {

    override fun onBind(item: Item.ManualAccentBackup) {
        itemView.setOnClickListener { onClick(item) }
    }

}