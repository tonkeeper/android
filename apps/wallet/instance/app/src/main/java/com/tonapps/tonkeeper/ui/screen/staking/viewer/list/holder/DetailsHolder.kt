package com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import uikit.extensions.withGreenBadge

class DetailsHolder(
    parent: ViewGroup,
): Holder<Item.Details>(parent, R.layout.view_staking_details) {

    private val apyView = findViewById<AppCompatTextView>(R.id.apy)
    private val apyTitleView = findViewById<AppCompatTextView>(R.id.apy_title)
    private val minDepositView = findViewById<AppCompatTextView>(R.id.min_deposit)
    private val minDepositContainerView = findViewById<View>(R.id.min_deposit_container)

    override fun onBind(item: Item.Details) {
        val apyTitle = getString(Localization.staking_apy)
        apyTitleView.text = if (item.maxApy) apyTitle.withGreenBadge(context, Localization.staking_max_apy) else apyTitle
        apyView.text = item.apyFormat
        if (item.minDepositFormat.isBlank()) {
            minDepositContainerView.visibility = AppCompatTextView.GONE
        } else {
            minDepositContainerView.visibility = AppCompatTextView.VISIBLE
            minDepositView.text = item.minDepositFormat.withCustomSymbol(context)
        }
    }

}