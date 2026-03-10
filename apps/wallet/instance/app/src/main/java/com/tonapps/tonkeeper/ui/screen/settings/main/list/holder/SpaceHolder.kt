package com.tonapps.tonkeeper.ui.screen.settings.main.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.settings.main.list.Item
import uikit.extensions.getDimensionPixelSize

class SpaceHolder(
    parent: ViewGroup,
    onClick: ((Item) -> Unit)
): Holder<Item.Space>(View(parent.context), onClick) {

    init {
        itemView.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, context.getDimensionPixelSize(uikit.R.dimen.offsetMedium))
    }

    override fun onBind(item: Item.Space) {

    }
}