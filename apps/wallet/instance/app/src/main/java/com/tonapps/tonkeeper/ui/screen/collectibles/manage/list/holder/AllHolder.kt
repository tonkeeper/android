package com.tonapps.tonkeeper.ui.screen.collectibles.manage.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.collectibles.manage.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.setMoonOnClickListener

class AllHolder(
    parent: ViewGroup,
    private val showAllClick: () -> Unit
): Holder<Item.All>(parent, R.layout.view_item_show_all) {

    private val button = findViewById<View>(R.id.button)

    init {
        button.setMoonOnClickListener { showAllClick() }
    }

    override fun onBind(item: Item.All) {

    }

}