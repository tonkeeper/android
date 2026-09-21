package com.tonapps.core.flags

enum class WalletTooltipKey(
    override val tooltipName: String,
    override val maxTimeToShow: Int,
    override val defaultState: TooltipState = TooltipState.NOT_SHOWN,
) : TooltipKey {
    ADD_MULTICHAIN_WALLET("add_multichain_wallet", maxTimeToShow = 3);
}

sealed interface WalletTooltip {
    val key: TooltipKey

    val shouldShow: Boolean get() = TooltipManager.shouldShow(key)
    val state: TooltipState get() = TooltipManager.getState(key)
}
