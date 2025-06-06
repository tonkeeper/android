package com.tonapps.tonkeeper.ui.screen.onramp.picker.provider.list

import android.text.SpannableStringBuilder
import android.util.Log
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentBlueColor
import com.tonapps.uikit.color.stateList
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.wallet.localization.Localization
import uikit.extensions.drawable
import uikit.extensions.withBlueBadge
import uikit.extensions.withDefaultBadge
import uikit.widget.FrescoView

class Holder(
    parent: ViewGroup,
    val onClick: ((Item) -> Unit)
): BaseListHolder<Item>(parent,  R.layout.view_purchase_method) {

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val descriptionView = findViewById<AppCompatTextView>(R.id.description)
    private val arrowView = findViewById<AppCompatImageView>(R.id.arrow)

    init {
        arrowView.imageTintList = context.accentBlueColor.stateList
    }

    override fun onBind(item: Item) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener { onClick(item) }

        iconView.setImageURI(item.iconUri, this)
        if (item.best) {
            titleView.text = item.title.withBlueBadge(context, Localization.best)
        } else {
            titleView.text = item.title
        }
        descriptionView.text = item.description

        if (item.selected) {
            arrowView.setImageResource(UIKitIcon.ic_donemark_otline_28)
        } else {
            arrowView.setImageDrawable(null)
        }
    }

}