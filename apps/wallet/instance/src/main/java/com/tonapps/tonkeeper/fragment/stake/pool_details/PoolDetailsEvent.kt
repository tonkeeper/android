package com.tonapps.tonkeeper.fragment.stake.pool_details

sealed class PoolDetailsEvent {
    object NavigateBack : PoolDetailsEvent()
    object FinishFlow : PoolDetailsEvent()
}