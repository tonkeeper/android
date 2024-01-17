package com.tonkeeper.fragment.send

import uikit.mvi.UiState

data class SendScreenState(
    val headerTitle: CharSequence = "",
    val headerSubtitle: CharSequence? = null,
    val headerVisible: Boolean = true,
    val currentPage: Int = 0
): UiState()