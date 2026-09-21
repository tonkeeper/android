package com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.ExploreItem
import com.tonapps.tonkeeperx.R

class ExploreSpaceHolder(
    parent: ViewGroup,
) : ExploreHolder<ExploreItem.Space>(parent, R.layout.view_item_space_medium) {

    override fun onBind(item: ExploreItem.Space) = Unit
}
