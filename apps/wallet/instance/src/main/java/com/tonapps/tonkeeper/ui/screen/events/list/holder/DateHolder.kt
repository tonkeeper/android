package com.tonapps.tonkeeper.ui.screen.events.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.events.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.setPaddingVertical
import uikit.widget.TitleView

class DateHolder(parent: ViewGroup): Holder<Item.Date>(parent, R.layout.view_history_header) {

    private val titleView = findViewById<TitleView>(R.id.title)

    init {
        titleView.setPaddingVertical(0)
    }

    override fun onBind(item: Item.Date) {
        titleView.text = item.date
    }

}