package com.tonapps.wallet.data.account.entities

data class SimpleAccountEntity(
    val id: Long,
    val name: String,
    val emoji: String,
    val color: Int,
    val address: String,
)