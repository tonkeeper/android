package com.tonapps.tonkeeper.core.history

data class ActionOptions(
    val spamFilter: SpamFilter = SpamFilter.NONE,
    val safeMode: Boolean = false,
    val hiddenBalances: Boolean = false,
    val removeDate: Boolean = false,
    val positionExtra: Int = 0,
) {

    enum class SpamFilter {
        NONE,
        SPAM,
        NOT_SPAM
    }
}