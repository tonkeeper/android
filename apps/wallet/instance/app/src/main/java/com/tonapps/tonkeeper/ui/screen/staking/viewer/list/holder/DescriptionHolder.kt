package com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.getSpannable

class DescriptionHolder(parent: ViewGroup): Holder<Item.Description>(parent, R.layout.view_staking_description) {

    private val textView = findViewById<AppCompatTextView>(R.id.text)

    override fun onBind(item: Item.Description) {
        textView.text = context.getSpannable(item.resId)
        if (item.uri == null) {
            textView.setOnClickListener(null)
        } else {
            textView.setOnClickListener {
                BrowserHelper.open(context, item.uri)
            }
        }
    }

}