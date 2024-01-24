package com.tonapps.singer.screen.main.list

import com.tonapps.singer.core.KeyEntity
import uikit.list.BaseListItem
import uikit.list.ListCell

sealed class MainItem(
    type: Int,
    open val id: Long,
): BaseListItem(type) {

    companion object {
        const val TYPE_ACTIONS = 1
        const val TYPE_ACCOUNT = 2
    }

    data object Actions: MainItem(TYPE_ACTIONS, -1)

    data class Account(
        override val id: Long,
        val label: String,
        val hex: String,
        val position: ListCell.Position
    ): MainItem(TYPE_ACCOUNT, id) {

        constructor(key: KeyEntity, position: ListCell.Position) : this(
            id = key.id,
            label = key.name,
            hex = key.hex,
            position = position
        )
    }
}