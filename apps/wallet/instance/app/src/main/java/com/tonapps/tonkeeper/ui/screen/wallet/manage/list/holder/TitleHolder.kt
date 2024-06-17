package com.tonapps.tonkeeper.ui.screen.wallet.manage.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.wallet.manage.list.Item
import com.tonapps.tonkeeperx.R

class TitleHolder(parent: ViewGroup): Holder<Item.Title>(parent, R.layout.view_wallet_manage_title) {

    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val detailsView = findViewById<AppCompatTextView>(R.id.details)

    override fun onBind(item: Item.Title) {
        titleView.setText(item.titleRes)
        if (item.detailsRes != 0) {
            detailsView.setText(item.detailsRes)
            detailsView.visibility = View.VISIBLE
        } else {
            detailsView.visibility = View.GONE
        }
    }

}