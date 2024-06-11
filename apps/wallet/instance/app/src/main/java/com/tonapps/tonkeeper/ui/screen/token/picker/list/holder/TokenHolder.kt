package com.tonapps.tonkeeper.ui.screen.token.picker.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.token.picker.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.drawable
import uikit.widget.FrescoView

class TokenHolder(
    parent: ViewGroup,
    private val onClick: (Item.Token) -> Unit,
): Holder<Item.Token>(parent, R.layout.view_token) {

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val balanceView = findViewById<AppCompatTextView>(R.id.balance)
    private val checkView = findViewById<View>(R.id.check)

    override fun onBind(item: Item.Token) {
        itemView.setOnClickListener { onClick(item) }
        itemView.background = item.position.drawable(context)
        iconView.setImageURI(item.iconUri, this)
        titleView.text = item.symbol
        balanceView.text = item.balance
        checkView.visibility = if (item.selected) View.VISIBLE else View.GONE
    }

}