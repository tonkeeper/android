package com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main.list.holder

import android.net.Uri
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.extensions.spannableCode
import com.tonapps.tonkeeper.ui.component.CountryFlagView
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.main.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentBlueColor
import uikit.extensions.badgeAccentColor
import uikit.extensions.badgeDefault
import uikit.extensions.badgeRed
import uikit.extensions.drawable
import uikit.extensions.withDefaultBadge
import uikit.extensions.withGreenBadge
import uikit.widget.FrescoView

class CurrencyHolder(
    parent: ViewGroup,
    val onCurrencyClick: ((Item.Currency) -> Unit)
): Holder<Item.Currency>(parent, R.layout.view_currency_item) {

    private val imageView = findViewById<FrescoView>(R.id.image)
    private val iconView = findViewById<CountryFlagView>(R.id.icon)
    private val symbolView = findViewById<AppCompatTextView>(R.id.symbol)
    private val nameView = findViewById<AppCompatTextView>(R.id.name)
    private val checkView = findViewById<View>(R.id.check)

    init {
        findViewById<View>(R.id.arrow).visibility = View.GONE
    }

    override fun onBind(item: Item.Currency) {
        itemView.background = item.position.drawable(context)
        nameView.text = item.title

        symbolView.text = item.currency.spannableCode(context)
        applyIcon(item.icon, item.iconRes)
        itemView.setOnClickListener { onCurrencyClick(item) }
        checkView.visibility = if (item.selected) View.VISIBLE else View.GONE
    }

    private fun applyIcon(icon: Uri?, iconRes: Int?) {
        imageView.visibility = View.GONE
        iconView.visibility = View.GONE
        if (iconRes != null) {
            iconView.setIcon(iconRes)
            iconView.visibility = View.VISIBLE
        }

        if (icon != null) {
            imageView.setImageURI(icon)
            imageView.visibility = View.VISIBLE
        }
    }

}