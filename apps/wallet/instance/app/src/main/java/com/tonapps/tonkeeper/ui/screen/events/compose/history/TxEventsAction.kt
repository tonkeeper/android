package com.tonapps.tonkeeper.ui.screen.events.compose.history

import ui.components.events.EventItemClickPart

sealed interface TxEventsAction {
    data object BuyTon : TxEventsAction
    data object OpenQR : TxEventsAction
    data class Details(val id: String, val part: EventItemClickPart) : TxEventsAction
    data class SelectFilter(val id: Int) : TxEventsAction
}