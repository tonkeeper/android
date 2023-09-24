package com.tonkeeper.fragment.currency.list

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonkeeper.R
import com.tonkeeper.uikit.drawable.CellBackgroundDrawable
import com.tonkeeper.uikit.list.BaseListHolder

class CurrencyHolder(parent: ViewGroup): BaseListHolder<CurrencyItem>(parent, R.layout.view_currency_cell) {

    private val codeView = findViewById<AppCompatTextView>(R.id.code)
    private val nameView = findViewById<AppCompatTextView>(R.id.name)

    override fun onBind(item: CurrencyItem) {
        itemView.background = CellBackgroundDrawable(itemView.context, item.position)
        codeView.text = item.code
        nameView.setText(item.nameResId)
    }
}