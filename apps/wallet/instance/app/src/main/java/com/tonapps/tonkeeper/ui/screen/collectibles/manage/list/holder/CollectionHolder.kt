package com.tonapps.tonkeeper.ui.screen.collectibles.manage.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.facebook.imagepipeline.common.ResizeOptions
import com.tonapps.tonkeeper.ui.screen.collectibles.manage.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Plurals
import uikit.extensions.drawable
import uikit.widget.FrescoView

class CollectionHolder(
    parent: ViewGroup,
    private val onClick: (Item.Collection) -> Unit
): Holder<Item.Collection>(parent, R.layout.view_manage_collection) {

    private val actionView = findViewById<AppCompatImageView>(R.id.action)
    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val countView = findViewById<AppCompatTextView>(R.id.count)
    private val chevronView = findViewById<View>(R.id.chevron)

    override fun onBind(item: Item.Collection) {
        itemView.background = item.position.drawable(context)
        iconView.setImageURIWithResize(item.imageUri, ResizeOptions.forSquareSize(128)!!)
        titleView.text = item.title
        countView.text = context.resources.getQuantityString(
            Plurals.nft_count,
            item.count,
            item.count
        )
        if (item.spam) {
            actionView.visibility = View.GONE
            actionView.setOnClickListener(null)
            chevronView.visibility = View.VISIBLE
            itemView.setOnClickListener { onClick(item) }
        } else {
            chevronView.visibility = View.GONE
            actionView.visibility = View.VISIBLE
            actionView.setImageResource(if (item.visible) {
                UIKitIcon.ic_minus
            } else {
                UIKitIcon.ic_plus
            })
            actionView.setOnClickListener { onClick(item) }
            itemView.setOnClickListener(null)
        }
    }

}