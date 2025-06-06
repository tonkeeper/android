package com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main.list.holder

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.drawable
import uikit.widget.FrescoView

class MoreHolder(
    parent: ViewGroup,
    val onMoreClick: ((Item.More) -> Unit)
): Holder<Item.More>(parent, R.layout.view_currency_item) {

    private val imagesView = findViewById<FrameLayout>(R.id.images)
    private val imageVies = arrayOf<FrescoView>(findViewById(R.id.image1), findViewById(R.id.image2))

    private val iconView = findViewById<AppCompatImageView>(R.id.icon)
    private val symbolView = findViewById<AppCompatTextView>(R.id.symbol)

    override fun onBind(item: Item.More) {
        itemView.background = item.position.drawable(context)
        symbolView.text = item.title
        itemView.setOnClickListener { onMoreClick(item) }

        applyIcons(item.values.mapNotNull { it.iconUri })
    }

    private fun applyIcons(icons: List<Uri>) {
        if (icons.isEmpty()) {
            imagesView.visibility = View.GONE
            return
        }
        imagesView.visibility = View.VISIBLE
        for (i in imageVies.indices) {
            val imageView = imageVies[i]
            if (i < icons.size) {
                imageView.setImageURI(icons[i], this)
                imageView.visibility = View.VISIBLE
            } else {
                imageView.visibility = View.GONE
            }
        }

        iconView.visibility = View.GONE
    }

}