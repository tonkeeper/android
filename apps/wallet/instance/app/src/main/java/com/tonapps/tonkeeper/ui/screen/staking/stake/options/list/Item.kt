package com.tonapps.tonkeeper.ui.screen.staking.stake.options.list

import android.content.Context
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import com.tonapps.wallet.localization.Localization

sealed class Item(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_TITLE = 1
        const val TYPE_POOL = 2
        const val TYPE_SPACE = 3

        fun map(
            pools: List<PoolInfoEntity>,
            selectedPool: PoolEntity,
        ): List<Item> {
            val items = mutableListOf<Item>()
            val liquid = pools.filter { it.pools.size == 1 }
            val other = pools.filter { it.pools.size > 1 }

            items.add(Space)
            items.add(Title(Localization.liquid_staking))
            items.add(Space)
            items.addAll(build(liquid, selectedPool))
            items.add(Space)

            if (other.isNotEmpty()) {
                items.add(Space)
                items.add(Title(Localization.other))
                items.add(Space)
                items.addAll(build(other, selectedPool))
                items.add(Space)
            }

            return items.toList()
        }

        private fun build(
            pools: List<PoolInfoEntity>,
            selectedPool: PoolEntity
        ): List<Pool> {
            val items = mutableListOf<Pool>()
            for ((index, pool) in pools.withIndex()) {
                val position = ListCell.getPosition(pools.size, index)
                val minimumDepositFormat = CurrencyFormatter.format("TON", pool.minStake)
                val apyFormat = CurrencyFormatter.formatPercent(pool.apy)
                items.add(Pool(
                    entity = pool,
                    position = position,
                    selected = selectedPool.implementation == pool.implementation,
                    minimumDepositFormat = minimumDepositFormat,
                    maxApy = pool.maxApy,
                    apyFormat = apyFormat
                ))
            }
            return items.toList()
        }
    }

    data class Title(val resId: Int): Item(TYPE_TITLE)

    data class Pool(
        val entity: PoolInfoEntity,
        val position: ListCell.Position,
        val selected: Boolean,
        val minimumDepositFormat: CharSequence,
        val maxApy: Boolean,
        val apyFormat: CharSequence,
    ): Item(TYPE_POOL) {

        fun getDescription(context: Context): String {
            val lines = mutableListOf<String>()
            lines.add(context.getString(Localization.staking_minimum_deposit, minimumDepositFormat))
            lines.add("${context.getString(Localization.staking_apy)} ≈ $apyFormat")
            return lines.joinToString("\n")
        }
    }

    data object Space: Item(TYPE_SPACE)
}