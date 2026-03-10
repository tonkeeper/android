package com.tonapps.signer.screen.main.list

import com.tonapps.signer.core.entities.KeyEntity

sealed class MainItem(
    type: Int,
    open val id: Long,
): com.tonapps.uikit.list.BaseListItem(type) {

    companion object {
        const val TYPE_ACTIONS = 1
        const val TYPE_ACCOUNT = 2
    }

    data object Actions: MainItem(TYPE_ACTIONS, -1)

    data class Account(
        override val id: Long,
        val label: String,
        val hex: String,
        val position: com.tonapps.uikit.list.ListCell.Position
    ): MainItem(TYPE_ACCOUNT, id) {

        constructor(key: KeyEntity, position: com.tonapps.uikit.list.ListCell.Position) : this(
            id = key.id,
            label = key.name,
            hex = key.hex,
            position = position
        )
    }
}