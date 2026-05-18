package com.tonapps.tonkeeper.ui.screen.tronfees.list

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.battery.BatteryScreen
import com.tonapps.tonkeeper.view.BatteryView
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import uikit.extensions.drawable
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.AsyncImageView

class BatteryHolder(parent: ViewGroup): Holder<Item.Battery>(parent, R.layout.view_tron_fee_option) {

    private val asyncImageView = itemView.findViewById<AsyncImageView>(R.id.icon)
    private val batteryContainerView = itemView.findViewById<View>(R.id.battery_container)
    private val batteryView = itemView.findViewById<BatteryView>(R.id.battery)
    private val titleView = itemView.findViewById<AppCompatTextView>(R.id.title)
    private val balanceView = itemView.findViewById<AppCompatTextView>(R.id.balance)
    private val requiredView = itemView.findViewById<AppCompatTextView>(R.id.required)

    override fun onBind(item: Item.Battery) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener {
            context.navigation?.add(BatteryScreen.newInstance(wallet = item.wallet, from = "tron_fees"))
        }
        asyncImageView.visibility = View.GONE
        batteryContainerView.visibility = View.VISIBLE
        batteryView.setBatteryLevel(1f)
        titleView.text = getString(Localization.battery)
        balanceView.text = context.getString(Localization.balance_prefix, item.balanceFormat)
        requiredView.text = context.getString(Localization.required_prefix, item.amountFormat)
    }
}