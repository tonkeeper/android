package com.tonapps.singer.screen.main.list

import uikit.list.BaseListItem
import uikit.list.ListCell

sealed class MainItem(type: Int): BaseListItem(type) {

    companion object {
        const val TYPE_ACTIONS = 1
        const val TYPE_ACCOUNT = 2
        const val TYPE_SPACE = 3
    }

    data object Actions: MainItem(TYPE_ACTIONS)

    data class Account(
        val id: Long,
        val label: String,
        val hex: String,
        val position: ListCell.Position
    ): MainItem(TYPE_ACCOUNT)

    data object Space: MainItem(TYPE_SPACE)
}