package com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeperx.R

class DetailsHolder(
    parent: ViewGroup,
): Holder<Item.Details>(parent, R.layout.view_staking_details) {

    private val apyView = findViewById<AppCompatTextView>(R.id.apy)
    private val minDepositView = findViewById<AppCompatTextView>(R.id.min_deposit)

    override fun onBind(item: Item.Details) {
        apyView.text = item.apyFormat
        minDepositView.text = item.minDepositFormat
    }

}