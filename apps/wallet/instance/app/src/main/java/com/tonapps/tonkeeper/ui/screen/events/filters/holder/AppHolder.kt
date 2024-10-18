package com.tonapps.tonkeeper.ui.screen.events.filters.holder

import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.updatePadding
import com.tonapps.tonkeeper.ui.screen.events.filters.FilterItem
import com.tonapps.tonkeeperx.R
import uikit.extensions.dp
import uikit.extensions.setPaddingHorizontal
import uikit.widget.FrescoView

class AppHolder(
    parent: ViewGroup,
    private val onClick: (item: FilterItem) -> Unit
): Holder<FilterItem.App>(parent) {

    private val iconView = findViewById<FrescoView>(R.id.icon)

    init {
        itemView.updatePadding(
            left = 6.dp,
            right = 14.dp
        )
        iconView.visibility = View.VISIBLE
    }

    override fun onBind(item: FilterItem.App) {
        itemView.setOnClickListener { onClick(item) }
        titleView.text = item.name
        iconView.setImageURI(item.iconUrl, null)
        updateSelected(item)
    }

    fun updateSelected(item: FilterItem) {
        setSelected(item.selected)
    }

}