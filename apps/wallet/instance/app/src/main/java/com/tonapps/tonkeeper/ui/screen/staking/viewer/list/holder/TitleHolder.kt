package com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeperx.R

class TitleHolder(
    parent: ViewGroup,
) : Holder<Item.Title>(parent, R.layout.view_staking_title) {

    private val titleView = itemView as AppCompatTextView

    override fun onBind(item: Item.Title) {
        titleView.setText(item.titleRes)
    }
}
