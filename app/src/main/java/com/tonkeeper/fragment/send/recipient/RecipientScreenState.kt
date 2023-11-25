package com.tonkeeper.fragment.send.recipient

import com.tonkeeper.api.shortAddress
import uikit.mvi.UiState

data class RecipientScreenState(
    val addressState: AddressState = AddressState.EMPTY,
    val address: String = "",
    val comment: String = "",
    val name: String? = null
): UiState() {

    enum class AddressState {
        EMPTY,
        INVALID,
        VALID,
        LOADING
    }
}