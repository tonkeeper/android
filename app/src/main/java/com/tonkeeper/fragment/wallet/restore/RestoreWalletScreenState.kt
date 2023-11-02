package com.tonkeeper.fragment.wallet.restore

import uikit.mvi.UiState

data class RestoreWalletScreenState(
    val hintWords: List<String> = emptyList(),
    val canNext: Boolean = false,
    val loading: Boolean = false,
    val done: Boolean = false
): UiState()