package com.tonapps.core.flags

enum class WalletTooltipKey(
    override val tooltipName: String,
    override val maxTimeToShow: Int,
    override val defaultState: TooltipState = TooltipState.NOT_SHOWN,
) : TooltipKey {
    RAMP("ramp_tooltip", 2),
    ;
}

sealed interface WalletTooltip {
    val key: TooltipKey

    val shouldShow: Boolean get() = TooltipManager.shouldShow(key)
    val state: TooltipState get() = TooltipManager.getState(key)

    data object Ramp : WalletTooltip {
        override val key: TooltipKey get() = WalletTooltipKey.RAMP
    }
}
