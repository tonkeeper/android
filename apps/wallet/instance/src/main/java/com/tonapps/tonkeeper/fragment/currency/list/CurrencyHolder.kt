package com.tonapps.tonkeeper.fragment.currency.list

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.BaseListHolder
import uikit.extensions.drawable

class CurrencyHolder(
    parent: ViewGroup,
    private val onClick: (item: CurrencyItem) -> Unit
): BaseListHolder<CurrencyItem>(parent, R.layout.view_currency_cell) {

    private val codeView = findViewById<AppCompatTextView>(R.id.code)
    private val nameView = findViewById<AppCompatTextView>(R.id.name)
    private val checkView = findViewById<AppCompatImageView>(R.id.check)

    override fun onBind(item: CurrencyItem) {
        itemView.background = item.position.drawable(itemView.context)
        codeView.text = item.currency
        nameView.setText(item.nameResId)
        checkView.visibility = if (item.selected) {
            AppCompatImageView.VISIBLE
        } else {
            AppCompatImageView.GONE
        }
        itemView.setOnClickListener {
            onClick(item)
        }
    }
}