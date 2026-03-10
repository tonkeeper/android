package com.tonapps.tonkeeper.ui.screen.tronfees.list

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import uikit.extensions.getSpannable
import uikit.navigation.Navigation.Companion.navigation

class LearnMoreHolder(parent: ViewGroup): Holder<Item.LearnMore>(parent, R.layout.view_tron_fee_learn_more) {
    val textView = findViewById<AppCompatTextView>(R.id.text)

    override fun onBind(item: Item.LearnMore) {
        textView.text = context.getSpannable(Localization.tron_fees_learn_more)
        itemView.setOnClickListener {
            context.navigation?.openURL(item.url)
        }
    }

}