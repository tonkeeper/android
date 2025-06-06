package com.tonapps.tonkeeper.ui.base.picker.currency.list

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.extensions.spannableCode
import com.tonapps.tonkeeper.ui.component.CountryFlagView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.BaseListHolder
import uikit.extensions.drawable
import uikit.widget.FrescoView

class Holder(
    parent: ViewGroup,
    val onClick: ((Item) -> Unit)
): BaseListHolder<Item>(parent, R.layout.view_currency_item) {

    private val imageView = findViewById<FrescoView>(R.id.image)
    private val iconView = findViewById<CountryFlagView>(R.id.icon)
    private val symbolView = findViewById<AppCompatTextView>(R.id.symbol)
    private val nameView = findViewById<AppCompatTextView>(R.id.name)

    override fun onBind(item: Item) {
        itemView.background = item.position.drawable(context)
        applyDrawableRes(item.drawableRes)
        applyImageUri(item.iconUri)
        symbolView.text = item.currency.spannableCode(context)
        nameView.text = item.name
        itemView.setOnClickListener { onClick(item) }
    }

    private fun applyDrawableRes(icon: Int?) {
        if (icon == null) {
            iconView.visibility = View.GONE
        } else {
            iconView.visibility = View.VISIBLE
            imageView.visibility = View.GONE
            iconView.setIcon(icon)
        }
    }

    private fun applyImageUri(uri: Uri?) {
        if (uri == null) {
            imageView.visibility = View.GONE
        } else {
            imageView.visibility = View.VISIBLE
            iconView.visibility = View.GONE
            imageView.setImageURI(uri)
        }
    }

}