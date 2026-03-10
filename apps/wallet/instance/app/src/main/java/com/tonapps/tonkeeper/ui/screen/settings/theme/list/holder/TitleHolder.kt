package com.tonapps.tonkeeper.ui.screen.settings.theme.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.settings.theme.list.Item
import com.tonapps.tonkeeperx.R

class TitleHolder(parent: ViewGroup): Holder<Item.Title>(parent, R.layout.view_theme_title) {

    private val titleView = findViewById<AppCompatTextView>(R.id.title)

    override fun onBind(item: Item.Title) {
        titleView.text = item.title
    }
}