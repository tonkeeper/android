package com.tonkeeper.fragment.send.confirm

import uikit.mvi.UiState

data class ConfirmScreenState(
    val items: List<Item> = emptyList()
): UiState() {

    data class Item(
        val title: CharSequence,
        val value: CharSequence,
        val description: CharSequence? = null,
    )
}