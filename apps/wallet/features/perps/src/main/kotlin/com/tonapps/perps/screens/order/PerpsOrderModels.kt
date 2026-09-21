package com.tonapps.perps.screens.order

/**
 * Hardcoded perps order flow models. These mirror the design ("Long BTC" open-order
 * screen + order-type sheet + limit price screen) and stay UI-only until the real
 * trading API lands.
 */
enum class PerpsOrderSide(val label: String) {
    Long("Long"),
    Short("Short"),
}

enum class PerpsOrderType {
    Market,
    Limit,
}
