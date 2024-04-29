package com.tonapps.tonkeeper.core.history.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import uikit.extensions.setPaddingVertical
import uikit.widget.TitleView

class HistoryHeaderHolder(parent: ViewGroup): HistoryHolder<HistoryItem.Header>(parent, R.layout.view_history_header) {

    private val titleView = findViewById<TitleView>(R.id.title)

    init {
        titleView.setPaddingVertical(0)
    }

    override fun onBind(item: HistoryItem.Header) {
        titleView.text = item.title
    }

}