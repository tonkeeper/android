package com.tonapps.tonkeeper.ui.screen.send.main

import com.tonapps.icu.Coins

sealed class SendEvent {
    data class Failed(val throwable: Throwable): SendEvent()
    data object Canceled: SendEvent()
    data object Success: SendEvent()
    data object Loading: SendEvent()
    data class InsufficientBalance(
        val balance: Coins,
        val required: Coins,
        val withRechargeBattery: Boolean,
        val singleWallet: Boolean
    ): SendEvent()
    data object Confirm: SendEvent()
    data class Fee(
        val value: Coins,
        val format: CharSequence,
        val convertedFormat: CharSequence,
        val isBattery: Boolean,
        val isGasless: Boolean,
        val showGaslessToggle: Boolean,
        val tokenSymbol: String,
    ): SendEvent()
}