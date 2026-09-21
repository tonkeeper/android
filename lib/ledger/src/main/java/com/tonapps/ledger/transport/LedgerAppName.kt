package com.tonapps.ledger.transport

data class LedgerAppName(
    val name: String,
    val version: String
) {
    val isTonApp: Boolean
        get() = name == TON_APP_NAME

    val isDashboard: Boolean
        get() = name.trimEnd(' ', '\u0000') in DASHBOARD_APP_NAMES

    companion object {
        const val TON_APP_NAME = "TON"
        val DASHBOARD_APP_NAMES = setOf("BOLOS", "OLOS")
    }
}
