package com.tonapps.tonkeeper.ui.screen.backup.main.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.backup.main.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.dp

class SpaceHolder(parent: ViewGroup): Holder<Item.Space>(parent, R.layout.view_backup_space) {

    init {
        itemView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 24.dp)
    }

    override fun onBind(item: Item.Space) {

    }
}