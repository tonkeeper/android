package com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeperx.R

class DescriptionHolder(parent: ViewGroup): Holder<Item.Description>(parent, R.layout.view_staking_description) {

    private val textView = findViewById<AppCompatTextView>(R.id.text)

    override fun onBind(item: Item.Description) {
        textView.setText(item.resId)
    }

}