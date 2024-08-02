package com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.flexbox.FlexboxLayout
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import uikit.extensions.dp
import uikit.extensions.drawable
import uikit.extensions.inflate
import uikit.extensions.setLeftDrawable
import uikit.navigation.Navigation

class LinksHolder(
    parent: ViewGroup,
): Holder<Item.Links>(parent, R.layout.view_staking_links) {

    private val linkDrawable = context.drawable(UIKitIcon.ic_globe_16)
    private val linksView = findViewById<FlexboxLayout>(R.id.links)

    override fun onBind(item: Item.Links) {
        linksView.removeAllViews()
        for (link in item.links) {
            val host = Uri.parse(link).host!!
            val linkView = context.inflate(R.layout.view_link, linksView) as AppCompatTextView
            linkView.text = host
            linkView.setLeftDrawable(linkDrawable)
            linkView.setOnClickListener { Navigation.from(context)?.openURL(link, true) }
            linksView.addView(linkView)
            linksView.addView(View(context), ViewGroup.LayoutParams(8.dp, 0))
        }
    }

}