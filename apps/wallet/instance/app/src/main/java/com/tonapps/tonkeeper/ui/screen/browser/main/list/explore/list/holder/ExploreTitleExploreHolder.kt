package com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.ExploreItem
import com.tonapps.tonkeeperx.R

class ExploreTitleExploreHolder(
    parent: ViewGroup,
    private val onMoreClick: (String) -> Unit
): ExploreHolder<ExploreItem.Title>(parent, R.layout.view_browser_title) {

    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val allView = findViewById<AppCompatTextView>(R.id.all)

    override fun onBind(item: ExploreItem.Title) {
        titleView.text = item.title
        allView.setOnClickListener {
            onMoreClick(item.id)
        }
    }
}