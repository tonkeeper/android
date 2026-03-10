package com.tonapps.wallet.data.settings.entities

data class TokenPrefsEntity(
    val pinned: Boolean = false,
    val state: State = State.NONE,
    private val hidden: Boolean = false,
    val index: Int = -1
) {

    companion object {

        fun state(value: Int) = when (value) {
            1 -> State.TRUST
            2 -> State.SPAM
            else -> State.NONE
        }
    }

    enum class State(val state: Int) {
        NONE(0), TRUST(1), SPAM(2)
    }

    val isTrust: Boolean
        get() = state == State.TRUST

    val isHidden: Boolean
        get() = state == State.SPAM || hidden

}