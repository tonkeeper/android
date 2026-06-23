package com.tonapps.core.flags

enum class TooltipState {
    ALWAYS,
    SHOWN,
    NOT_SHOWN;
}

sealed interface TooltipKey {
    val tooltipName: String
    val defaultState: TooltipState
    val maxTimeToShow: Int
}
