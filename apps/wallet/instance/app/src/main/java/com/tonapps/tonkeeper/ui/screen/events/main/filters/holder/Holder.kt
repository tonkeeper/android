package com.tonapps.tonkeeper.ui.screen.events.main.filters.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.events.main.filters.FilterItem
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.buttonPrimaryBackgroundColor
import com.tonapps.uikit.color.buttonPrimaryForegroundColor
import com.tonapps.uikit.color.buttonSecondaryBackgroundColor
import com.tonapps.uikit.color.buttonSecondaryForegroundColor
import com.tonapps.uikit.color.stateList
import com.tonapps.uikit.list.BaseListHolder

abstract class Holder<I: FilterItem>(
    parent: ViewGroup,
): BaseListHolder<I>(parent, R.layout.view_filter_chip) {

    val titleView = itemView.findViewById<AppCompatTextView>(R.id.title)

    fun setSelected(selected: Boolean) {
        val textColor = if (selected) context.buttonPrimaryForegroundColor else context.buttonSecondaryForegroundColor
        val color = if (selected) context.buttonPrimaryBackgroundColor else context.buttonSecondaryBackgroundColor
        itemView.backgroundTintList = color.stateList
        titleView.setTextColor(textColor)
    }
}