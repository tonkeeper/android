package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.staking.viewer.StakeViewerScreen
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.drawable
import uikit.navigation.Navigation
import uikit.widget.FrescoView

class StakedHolder(parent: ViewGroup): Holder<Item.Stake>(parent, R.layout.view_wallet_staked) {

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val nameView = findViewById<AppCompatTextView>(R.id.name)
    private val balanceView = findViewById<AppCompatTextView>(R.id.balance)
    private val balanceFiatView = findViewById<AppCompatTextView>(R.id.balance_fiat)

    override fun onBind(item: Item.Stake) {
        itemView.background = item.position.drawable(context)
        iconView.setImageURI(item.iconUri, null)
        nameView.text = item.poolName
        balanceView.text = item.balanceFormat
        balanceFiatView.text = item.fiatFormat

        itemView.setOnClickListener {
            Navigation.from(context)?.add(StakeViewerScreen.newInstance(item.poolAddress, item.poolName))
        }
    }
}