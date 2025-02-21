package com.tonapps.tonkeeper.ui.screen.events.main.filters.holder

import android.view.View
import android.view.ViewGroup
import androidx.core.view.updatePadding
import com.tonapps.tonkeeper.ui.screen.events.main.filters.FilterItem
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.constantWhiteColor
import uikit.extensions.dp
import uikit.extensions.drawable
import uikit.extensions.withAlpha
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
        iconView.setPlaceholder(context.drawable(uikit.R.drawable.bg_oval).apply {
            setTint(context.constantWhiteColor.withAlpha(.2f))
        })
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