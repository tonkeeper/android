package com.tonapps.tonkeeper.ui.screen.staking.stake.options.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.ui.screen.staking.stake.options.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.stateList
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import uikit.extensions.drawable
import uikit.extensions.withAlpha
import uikit.widget.FrescoView
import uikit.widget.RadioView

class PoolHolder(
    parent: ViewGroup,
    private val onClick: (PoolInfoEntity) -> Unit,
): Holder<Item.Pool>(parent, R.layout.view_staking_options_pool) {

    private val iconView = findViewById<FrescoView>(R.id.icon)
    private val nameView = findViewById<AppCompatTextView>(R.id.name)
    private val maxApyView = findViewById<View>(R.id.max_apy)
    private val descriptionView = findViewById<AppCompatTextView>(R.id.description)
    private val radioView = findViewById<RadioView>(R.id.radio)
    private val arrowView = findViewById<View>(R.id.arrow)

    init {
        iconView.setCircular()
        radioView.setOnClickListener(null)
    }

    override fun onBind(item: Item.Pool) {
        itemView.background = item.position.drawable(context)
        itemView.setOnClickListener {
            onClick(item.entity)
        }

        iconView.setLocalRes(StakingPool.getIcon(item.entity.implementation))
        nameView.setText(StakingPool.getTitle(item.entity.implementation))
        maxApyView.visibility = if (item.maxApy) {
            maxApyView.backgroundTintList = context.accentGreenColor.withAlpha(.16f).stateList
            View.VISIBLE
        } else {
            View.GONE
        }

        if (item.entity.pools.size > 1) {
            arrowView.visibility = View.VISIBLE
            radioView.visibility = View.GONE
        } else {
            arrowView.visibility = View.GONE
            radioView.visibility = View.VISIBLE
            radioView.isClickable = false
            radioView.checked = item.selected
        }

        descriptionView.text = item.getDescription(context).withCustomSymbol(context)
    }
}