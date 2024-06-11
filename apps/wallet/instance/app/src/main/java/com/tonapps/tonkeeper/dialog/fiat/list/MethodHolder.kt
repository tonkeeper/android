package com.tonapps.tonkeeper.dialog.fiat.list

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.facebook.drawee.view.SimpleDraweeView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.BaseListHolder
import uikit.extensions.drawable

class MethodHolder(
    parent: ViewGroup,
    private val onClick: (item: MethodItem) -> Unit
): BaseListHolder<MethodItem>(parent, R.layout.view_fiat_method) {

    private val iconView = findViewById<SimpleDraweeView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val subtitleView = findViewById<AppCompatTextView>(R.id.subtitle)

    override fun onBind(item: MethodItem) {
        itemView.background = item.position.drawable(itemView.context)
        iconView.setImageURI(item.iconUrl)
        itemView.setOnClickListener { onClick(item) }

        titleView.text = item.title
        subtitleView.text = item.subtitle
    }

}