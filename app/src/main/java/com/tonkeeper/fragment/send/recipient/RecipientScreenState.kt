package com.tonkeeper.fragment.send.recipient

import uikit.mvi.UiState

data class RecipientScreenState(
    val addressState: AddressState = AddressState.EMPTY,
    val address: String = "",
    val name: String? = null,
    val requireComment: Boolean = false
): UiState() {

    enum class AddressState {
        EMPTY,
        INVALID,
        VALID,
        LOADING
    }
}