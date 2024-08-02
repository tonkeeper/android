package com.tonapps.tonkeeper.ui.screen.staking.stake.options.list

import com.tonapps.icu.CurrencyFormatter
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity

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
            for ((index, pool) in pools.withIndex()) {
                val position = ListCell.getPosition(pools.size, index)
                val minimumDepositFormat = CurrencyFormatter.format("TON", pool.minStake)
                items.add(Pool(
                    entity = pool,
                    position = position,
                    selected = selectedPool.implementation == pool.implementation,
                    minimumDepositFormat = minimumDepositFormat,
                    maxApy = index == 0
                ))
            }
            items.add(Space)
            return items
        }
    }

    data class Title(val resId: Int): Item(TYPE_TITLE)

    data class Pool(
        val entity: PoolInfoEntity,
        val position: ListCell.Position,
        val selected: Boolean,
        val minimumDepositFormat: CharSequence,
        val maxApy: Boolean,
    ): Item(TYPE_POOL)

    data object Space: Item(TYPE_SPACE)
}