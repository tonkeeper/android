package com.tonapps.wallet.data.loyalty

sealed interface LoyaltyReward {

    data object Hidden : LoyaltyReward

    data object Unavailable : LoyaltyReward

    data class Ohms(val total: String) : LoyaltyReward
}
