package com.tonapps.signer.screen.change

import com.tonapps.signer.SimpleState

sealed class UiState(open val pageIndex: Int) {

    data class Task(
        val state: SimpleState,
        override val pageIndex: Int
    ): UiState(pageIndex)

    data class InputValid(
        val valid: Boolean,
        override val pageIndex: Int
    ): UiState(pageIndex)
}