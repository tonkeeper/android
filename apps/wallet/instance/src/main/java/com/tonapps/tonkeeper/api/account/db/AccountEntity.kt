package com.tonapps.tonkeeper.api.account.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey val accountId: String,
    val data: String
)