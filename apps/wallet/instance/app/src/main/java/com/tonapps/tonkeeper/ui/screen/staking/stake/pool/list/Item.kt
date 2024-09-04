package com.tonapps.tonkeeper.ui.screen.staking.stake.pool.list

import android.content.Context
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.staking.entities.PoolDetailsEntity
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import com.tonapps.wallet.localization.Localization

data class Item(
    val pool: PoolEntity,
    val details: PoolDetailsEntity,
    val position: ListCell.Position,
    val selected: Boolean,
    val minimumDepositFormat: CharSequence,
    val maxApy: Boolean,
    val apyFormat: CharSequence,
): BaseListItem(0) {

    companion object {

        fun map(
            info: PoolInfoEntity,
            selectedPoolAddress: String,
        ): List<Item> {
            val items = mutableListOf<Item>()
            for ((index, pool) in info.pools.withIndex()) {
                val position = ListCell.getPosition(info.pools.size, index)
                val minimumDepositFormat = CurrencyFormatter.format("TON", pool.minStake)
                val apyFormat = CurrencyFormatter.formatPercent(pool.apy)
                items.add(Item(
                    pool = pool,
                    details = info.details,
                    position = position,
                    selected = selectedPoolAddress.equalsAddress(pool.address),
                    minimumDepositFormat = minimumDepositFormat,
                    maxApy = pool.maxApy,
                    apyFormat = apyFormat,
                ))
            }
            return items.toList()
        }
    }

    fun getDescription(context: Context): String {
        val lines = mutableListOf<String>()
        lines.add(context.getString(Localization.staking_minimum_deposit, minimumDepositFormat))
        lines.add("${context.getString(Localization.staking_apy)} ≈ $apyFormat")
        return lines.joinToString("\n")
    }

}