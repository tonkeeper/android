package com.tonapps.wallet.data.settings.entities

data class TokenPrefsEntity(
    val pinned: Boolean = false,
    val hidden: Boolean = false,
    val index: Int = -1,
)