package com.tonapps.tonkeeper.ui.screen.staking.stake.pool.list

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.stateList
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.data.staking.entities.PoolEntity
import uikit.extensions.drawable
import uikit.extensions.withAlpha
import uikit.widget.FrescoView
import uikit.widget.RadioView

class Holder(
    parent: ViewGroup,
    private val onClick: (PoolEntity) -> Unit
): BaseListHolder<Item>(parent, R.layout.view_staking_options_pool) {

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val nameView = findViewById<AppCompatTextView>(R.id.name)
    private val maxApyView = findViewById<View>(R.id.max_apy)
    private val descriptionView = findViewById<AppCompatTextView>(R.id.description)
    private val radioView = findViewById<RadioView>(R.id.radio)

    init {
        iconView.setCircular()
        radioView.setOnClickListener(null)
    }

    override fun onBind(item: Item) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener {
            onClick(item.pool)
        }

        iconView.setLocalRes(StakingPool.getIcon(item.pool.implementation))
        nameView.text = item.pool.name
        maxApyView.visibility = if (item.maxApy) {
            maxApyView.backgroundTintList = context.accentGreenColor.withAlpha(.16f).stateList
            View.VISIBLE
        } else {
            View.GONE
        }

        radioView.isClickable = false
        radioView.checked = item.selected
        descriptionView.text = item.getDescription(context).withCustomSymbol(context)
    }

}