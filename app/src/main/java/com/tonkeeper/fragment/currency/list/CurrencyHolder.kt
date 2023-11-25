package com.tonkeeper.fragment.currency.list

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import com.tonkeeper.R
import uikit.drawable.CellBackgroundDrawable
import uikit.list.BaseListHolder

class CurrencyHolder(
    parent: ViewGroup,
    private val onClick: (item: CurrencyItem) -> Unit
): BaseListHolder<CurrencyItem>(parent, R.layout.view_currency_cell) {

    private val codeView = findViewById<AppCompatTextView>(R.id.code)
    private val nameView = findViewById<AppCompatTextView>(R.id.name)
    private val checkView = findViewById<AppCompatImageView>(R.id.check)

    override fun onBind(item: CurrencyItem) {
        itemView.background = CellBackgroundDrawable(itemView.context, item.position)
        codeView.text = item.currency.code
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