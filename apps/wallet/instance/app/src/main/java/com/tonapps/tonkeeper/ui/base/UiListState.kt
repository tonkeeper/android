package com.tonapps.tonkeeper.ui.base

import com.tonapps.uikit.list.BaseListItem

sealed class UiListState {
    data object Empty: UiListState()
    data object Loading: UiListState()
    data class Items(
        val cache: Boolean,
        val items: List<BaseListItem>
    ): UiListState()
}