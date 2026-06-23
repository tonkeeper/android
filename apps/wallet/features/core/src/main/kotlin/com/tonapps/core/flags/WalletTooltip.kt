package com.tonapps.core.flags

enum class WalletTooltipKey(
    override val tooltipName: String,
    override val maxTimeToShow: Int,
    override val defaultState: TooltipState = TooltipState.NOT_SHOWN,
) : TooltipKey {
    HISTORY_HERE("history_here_tooltip", 1),
    TRADING_TAB("trading_tab_tooltip", 1),
    ;
}

sealed interface WalletTooltip {
    val key: TooltipKey

    val shouldShow: Boolean get() = TooltipManager.shouldShow(key)
    val state: TooltipState get() = TooltipManager.getState(key)

    data object HistoryHere : WalletTooltip {
        override val key: TooltipKey get() = WalletTooltipKey.HISTORY_HERE
    }

    data object TradingTab : WalletTooltip {
        override val key: TooltipKey get() = WalletTooltipKey.TRADING_TAB
    }
}
